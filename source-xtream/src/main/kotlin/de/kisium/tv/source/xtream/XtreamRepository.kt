package de.kisium.tv.source.xtream

import android.util.Base64
import de.kisium.tv.core.data.CatalogCache
import de.kisium.tv.core.data.EpgCache
import de.kisium.tv.core.data.WatchHistoryRepository
import de.kisium.tv.core.model.AccountInfo
import de.kisium.tv.core.model.Category
import de.kisium.tv.core.model.ConnectionResult
import de.kisium.tv.core.model.ContentRepository
import de.kisium.tv.core.model.EpgEntry
import de.kisium.tv.core.model.EpgNowNext
import de.kisium.tv.core.model.Episode
import de.kisium.tv.core.model.HomeFeed
import de.kisium.tv.core.model.HomeRail
import de.kisium.tv.core.model.MediaKind
import de.kisium.tv.core.model.PlaybackRequest
import de.kisium.tv.core.model.PosterItem
import de.kisium.tv.core.model.RailKind
import de.kisium.tv.core.model.SeasonEpisodes
import de.kisium.tv.core.model.SeriesDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

class XtreamRepository(
    private val config: XtreamConfig,
    private val api: XtreamApi,
    private val apiFactory: XtreamApiFactory,
    private val cache: CatalogCache,
    private val epgCache: EpgCache,
    private val history: WatchHistoryRepository,
) : ContentRepository {

    override val sourceLabel: String = "Xtream · ${config.username}"
    override val isDemo: Boolean = false

    override suspend fun loadHome(): HomeFeed {
        val live = safeList { loadCategoryItems(MediaKind.LIVE, null) }
        val movies = safeList { loadCategoryItems(MediaKind.MOVIE, null) }
        val series = safeList { loadCategoryItems(MediaKind.SERIES, null) }

        if (live.isEmpty() && movies.isEmpty() && series.isEmpty()) {
            throw XtreamException("Keine Inhalte vom Server erhalten. Zugangsdaten und Server-URL prüfen.")
        }

        val continueItems = runCatching {
            history.continueWatching.first().map { continueItem ->
                continueItem.item.copy(resumeProgress = continueItem.progress)
            }
        }.getOrDefault(emptyList())

        val forYou = (movies.sortedByDescending { it.rating ?: 0.0 } +
            series.sortedByDescending { it.rating ?: 0.0 })
            .distinctBy { it.cardKey }
            .take(20)

        val newest = (movies + series)
            .sortedByDescending { it.added ?: 0L }
            .distinctBy { it.cardKey }
            .take(20)

        val rails = buildList {
            if (continueItems.isNotEmpty()) add(HomeRail("Weiterschauen", RailKind.CONTINUE, continueItems))
            if (live.isNotEmpty()) add(HomeRail("Jetzt live", RailKind.LIVE, live.take(20)))
            if (forYou.isNotEmpty()) add(HomeRail("Für dich", RailKind.MOVIES, forYou))
            if (newest.isNotEmpty()) add(HomeRail("Neu", RailKind.NEW, newest))
            if (movies.isNotEmpty()) add(HomeRail("Filme", RailKind.MOVIES, movies.take(20)))
            if (series.isNotEmpty()) add(HomeRail("Serien", RailKind.SERIES, series.take(20)))
        }

        val hero = movies.maxByOrNull { (it.rating ?: 0.0) + if (it.backdropUrl != null) 1.0 else 0.0 }
            ?: live.firstOrNull()
            ?: series.firstOrNull()

        return HomeFeed(
            hero = hero,
            rails = rails,
            sourceLabel = sourceLabel,
            isDemo = false,
        )
    }

    override suspend fun loadCategories(kind: MediaKind): List<Category> {
        val cacheKey = "${config.sourceId}:categories:${kind.name}"
        val cached = cache.readCategories(cacheKey)
        val cachedStale = cache.readCategories(cacheKey, Long.MAX_VALUE)
        return try {
            val dtos = when (kind) {
                MediaKind.LIVE -> api.liveCategories(config.username, config.password)
                MediaKind.MOVIE -> api.vodCategories(config.username, config.password)
                MediaKind.SERIES -> api.seriesCategories(config.username, config.password)
            }
            val mapped = dtos.mapNotNull { dto ->
                val id = dto.categoryId?.trim().orEmpty()
                if (id.isEmpty()) null else Category(
                    id = id,
                    name = dto.categoryName?.trim()?.ifEmpty { "Ohne Namen" } ?: "Ohne Namen",
                    kind = kind,
                )
            }
            cache.writeCategories(cacheKey, mapped)
            mapped
        } catch (t: Throwable) {
            t.rethrowCancellation()
            cached ?: cachedStale ?: throw t.toFriendlyXtreamError()
        }
    }

    override suspend fun loadCategoryItems(kind: MediaKind, categoryId: String?): List<PosterItem> {
        val cacheKey = "${config.sourceId}:items:${kind.name}:${categoryId ?: "all"}"
        cache.readItems(cacheKey)?.let { return it }
        val stale = cache.readItems(cacheKey, Long.MAX_VALUE)
        return try {
            val mapped = when (kind) {
                MediaKind.LIVE -> api.liveStreams(
                    config.username,
                    config.password,
                    "get_live_streams",
                    categoryId,
                ).mapNotNull { it.toPosterItem() }

                MediaKind.MOVIE -> api.vodStreams(
                    config.username,
                    config.password,
                    "get_vod_streams",
                    categoryId,
                ).mapNotNull { it.toPosterItem() }

                MediaKind.SERIES -> api.series(
                    config.username,
                    config.password,
                    "get_series",
                    categoryId,
                ).mapNotNull { it.toPosterItem() }
            }
            cache.writeItems(cacheKey, mapped)
            mapped
        } catch (t: Throwable) {
            t.rethrowCancellation()
            stale ?: throw t.toFriendlyXtreamError()
        }
    }

    override suspend fun loadSeries(seriesId: String): SeriesDetail {
        val response = try {
            api.seriesInfo(config.username, config.password, "get_series_info", seriesId)
        } catch (t: Throwable) {
            t.rethrowCancellation()
            throw t.toFriendlyXtreamError()
        }

        val info = response.info
        val allEpisodes = response.episodes.orEmpty()
            .flatMap { (seasonKey, episodes) ->
                val fallbackSeason = seasonKey.toIntOrNull() ?: 1
                episodes.mapNotNull { dto ->
                    val id = dto.id?.trim().orEmpty()
                    val episodeNumber = dto.episodeNum ?: return@mapNotNull null
                    if (id.isEmpty()) return@mapNotNull null
                    Episode(
                        id = id,
                        seriesId = seriesId,
                        season = dto.season ?: dto.info?.season ?: fallbackSeason,
                        number = episodeNumber,
                        title = dto.title?.trim()?.ifEmpty { "Folge $episodeNumber" }
                            ?: "Folge $episodeNumber",
                        plot = dto.info?.plot,
                        stillUrl = dto.info?.movieImage,
                        streamId = id.toIntOrNull(),
                        containerExtension = dto.containerExtension,
                        duration = dto.info?.duration,
                    )
                }
            }
            .sortedWith(compareBy<Episode> { it.season }.thenBy { it.number })

        val seasons = allEpisodes
            .groupBy { it.season }
            .toSortedMap()
            .map { (season, episodes) -> SeasonEpisodes(season, episodes) }

        val cachedSeries = runCatching {
            loadCategoryItems(MediaKind.SERIES, null).firstOrNull { it.id == seriesId }
        }.getOrNull()

        val release = info?.releaseDate ?: info?.releaseDateAlt
        val poster = cachedSeries?.copy(
            plot = cachedSeries.plot ?: info?.plot,
            imageUrl = cachedSeries.imageUrl ?: info?.cover,
            rating = cachedSeries.rating ?: info?.rating?.toDoubleOrNull(),
            year = cachedSeries.year ?: release?.take(4),
        ) ?: PosterItem(
            id = seriesId,
            sourceId = config.sourceId,
            kind = MediaKind.SERIES,
            title = info?.name?.trim()?.ifEmpty { "Serie" } ?: "Serie",
            subtitle = info?.genre,
            imageUrl = info?.cover ?: info?.backdropPath?.firstOrNull(),
            backdropUrl = info?.backdropPath?.firstOrNull(),
            rating = info?.rating?.toDoubleOrNull(),
            year = release?.take(4),
            plot = info?.plot,
            seriesId = seriesId.toIntOrNull(),
        )

        return SeriesDetail(poster, seasons)
    }

    override suspend fun nowNext(channel: PosterItem): EpgNowNext {
        val streamId = channel.streamId ?: channel.id.toIntOrNull()
        if (streamId != null) {
            runCatching {
                val response = api.shortEpg(
                    config.username,
                    config.password,
                    "get_short_epg",
                    streamId.toString(),
                    8,
                )
                val entries = response.listings.orEmpty().mapNotNull { it.toEpgEntry(channel.id) }
                if (entries.isNotEmpty()) {
                    epgCache.write(config.sourceId, channel.id, entries)
                }
            }.onFailure { it.rethrowCancellation() }
        }
        return epgCache.read(config.sourceId, channel.id)
    }

    override suspend fun playback(item: PosterItem, episode: Episode?): PlaybackRequest {
        if (item.kind == MediaKind.SERIES) {
            val episodeId = episode?.streamId
                ?: throw XtreamException("Für diese Serie ist keine abspielbare Folge hinterlegt.")
            val url = item.directUrl ?: apiFactory.episodeStreamUrl(
                config,
                episodeId,
                episode.containerExtension ?: "mp4",
            )
            return PlaybackRequest(
                itemKey = item.cardKey,
                title = item.title,
                subtitle = "S${episode.season} · E${episode.number} · ${episode.title}",
                url = url,
                kind = MediaKind.SERIES,
                isLive = false,
                artworkUrl = item.backdropUrl ?: item.imageUrl,
            )
        }

        val streamId = item.streamId ?: item.id.toIntOrNull()
            ?: throw XtreamException("Dieser Eintrag hat keine Stream-ID.")

        if (item.kind == MediaKind.LIVE) {
            val directUrl = item.directUrl
            if (!directUrl.isNullOrBlank()) {
                return PlaybackRequest(
                    itemKey = item.cardKey,
                    title = item.title,
                    subtitle = item.subtitle,
                    url = directUrl,
                    kind = MediaKind.LIVE,
                    isLive = true,
                    artworkUrl = item.backdropUrl ?: item.imageUrl,
                )
            }
            val hls = apiFactory.liveStreamUrl(config, streamId, "m3u8")
            val ts = apiFactory.liveStreamUrl(config, streamId, "ts")
            return PlaybackRequest(
                itemKey = item.cardKey,
                title = item.title,
                subtitle = item.subtitle,
                url = hls,
                alternateUrl = ts,
                kind = MediaKind.LIVE,
                isLive = true,
                artworkUrl = item.backdropUrl ?: item.imageUrl,
            )
        }

        val url = item.directUrl ?: apiFactory.movieStreamUrl(
            config,
            streamId,
            item.containerExtension ?: "mp4",
        )
        return PlaybackRequest(
            itemKey = item.cardKey,
            title = item.title,
            subtitle = item.subtitle,
            url = url,
            kind = MediaKind.MOVIE,
            isLive = false,
            artworkUrl = item.backdropUrl ?: item.imageUrl,
        )
    }

    override suspend fun testConnection(): ConnectionResult {
        return try {
            val response = api.authenticate(config.username, config.password)
            val info = response.userInfo
            val active = info?.auth == 1 || info?.status.equals("Active", ignoreCase = true)
            if (active) {
                val expires = info?.expDate?.toLongOrNull()?.let { epoch ->
                    SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(epoch * 1000L))
                } ?: "unbegrenzt"
                ConnectionResult(
                    success = true,
                    message = "Verbindung erfolgreich. Ablauf: $expires",
                    account = AccountInfo(
                        status = info?.status.orEmpty(),
                        expiresAt = expires,
                        isTrial = info?.isTrial == "1",
                        activeConnections = info?.activeConnections?.toIntOrNull(),
                        maxConnections = info?.maxConnections?.toIntOrNull(),
                        serverTimezone = response.serverInfo?.timezone,
                    ),
                )
            } else {
                ConnectionResult(
                    success = false,
                    message = info?.message?.takeIf { it.isNotBlank() }
                        ?: "Login fehlgeschlagen. Benutzername oder Passwort prüfen.",
                )
            }
        } catch (t: Throwable) {
            t.rethrowCancellation()
            ConnectionResult(false, t.toFriendlyXtreamError().message ?: "Unbekannter Fehler")
        }
    }

    private fun XtreamLiveStreamDto.toPosterItem(): PosterItem? {
        val id = streamId ?: return null
        val hls = apiFactory.liveStreamUrl(config, id, "m3u8")
        return PosterItem(
            id = id.toString(),
            sourceId = config.sourceId,
            kind = MediaKind.LIVE,
            title = name?.trim()?.ifEmpty { "Sender $id" } ?: "Sender $id",
            subtitle = "Live-TV",
            imageUrl = streamIcon?.trim()?.takeIf { it.isNotEmpty() },
            backdropUrl = streamIcon?.trim()?.takeIf { it.isNotEmpty() },
            categoryName = categoryId,
            streamId = id,
            added = added?.toLongOrNull(),
            previewUrl = hls,
            directUrl = directSource?.trim()?.takeIf { it.isNotEmpty() && it != "0" },
        )
    }

    private fun XtreamVodStreamDto.toPosterItem(): PosterItem? {
        val id = streamId ?: return null
        val image = streamIcon?.trim()?.takeIf { it.isNotEmpty() }
        val rating = rating?.toDoubleOrNull() ?: rating5Based
        val url = directSource?.trim()?.takeIf { it.isNotEmpty() && it != "0" }
            ?: apiFactory.movieStreamUrl(config, id, containerExtension ?: "mp4")
        return PosterItem(
            id = id.toString(),
            sourceId = config.sourceId,
            kind = MediaKind.MOVIE,
            title = name?.trim()?.ifEmpty { "Film $id" } ?: "Film $id",
            subtitle = listOfNotNull(rating?.let { "★ %.1f".format(Locale.GERMANY, it) }).firstOrNull(),
            imageUrl = image,
            backdropUrl = image,
            rating = rating,
            plot = null,
            categoryName = categoryId,
            streamId = id,
            containerExtension = containerExtension ?: "mp4",
            added = added?.toLongOrNull(),
            previewUrl = url,
            directUrl = url,
        )
    }

    private fun XtreamSeriesDto.toPosterItem(): PosterItem? {
        val id = seriesId ?: return null
        val image = cover?.trim()?.takeIf { it.isNotEmpty() }
        val backdrop = backdropPath?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: image
        val rating = rating?.toDoubleOrNull() ?: rating5Based
        return PosterItem(
            id = id.toString(),
            sourceId = config.sourceId,
            kind = MediaKind.SERIES,
            title = name?.trim()?.ifEmpty { "Serie $id" } ?: "Serie $id",
            subtitle = genre?.trim()?.takeIf { it.isNotEmpty() },
            imageUrl = image,
            backdropUrl = backdrop,
            rating = rating,
            year = releaseDate?.take(4),
            plot = plot,
            categoryName = categoryId,
            seriesId = id,
            added = lastModified?.toLongOrNull(),
        )
    }

    private fun XtreamEpgListingDto.toEpgEntry(channelId: String): EpgEntry? {
        val start = startTimestamp?.toLongOrNull()?.times(1000L) ?: parseXtreamDate(start) ?: return null
        val end = stopTimestamp?.toLongOrNull()?.times(1000L) ?: parseXtreamDate(end) ?: return null
        if (end <= start) return null
        return EpgEntry(
            channelId = channelId,
            startMillis = start,
            endMillis = end,
            title = decodeEpgText(title) ?: "Ohne Titel",
            description = decodeEpgText(description),
        )
    }

    private fun parseXtreamDate(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            format.parse(value)?.time
        }.getOrNull()
    }

    private fun decodeEpgText(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val decoded = runCatching {
            String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8).trim()
        }.getOrNull()
        if (decoded.isNullOrBlank()) return value.trim()
        val printable = decoded.count { it.isLetterOrDigit() || it.isWhitespace() || it.isPunctuation() }
        return if (printable >= decoded.length * 0.85) decoded else value.trim()
    }

    private fun Char.isPunctuation(): Boolean = this in ".,;:!?()[]{}'\"-–—/&+%€$"

    private suspend fun safeList(block: suspend () -> List<PosterItem>): List<PosterItem> {
        return try {
            block()
        } catch (t: Throwable) {
            t.rethrowCancellation()
            emptyList()
        }
    }

    private fun Throwable.rethrowCancellation() {
        if (this is CancellationException) throw this
    }

    private fun Throwable.toFriendlyXtreamError(): XtreamException {
        val text = when (this) {
            is XtreamException -> return this
            is HttpException -> "Server antwortet mit HTTP ${code()}."
            is IOException -> "Netzwerkfehler. Server erreichbar? (${message ?: "I/O"})"
            else -> message ?: "Unbekannter Fehler"
        }
        return XtreamException(text)
    }
}

class XtreamException(message: String) : RuntimeException(message)
