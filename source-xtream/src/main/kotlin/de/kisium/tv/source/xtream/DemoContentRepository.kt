package de.kisium.tv.source.xtream

import android.content.Context
import de.kisium.tv.core.data.WatchHistoryRepository
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
import kotlinx.coroutines.flow.first

/**
 * Bundled offline catalog shown until an Xtream account is configured. Artwork
 * lives in app-tv/res/drawable-nodpi, stream URLs are public test streams.
 */
class DemoContentRepository(
    private val context: Context,
    private val history: WatchHistoryRepository,
) : ContentRepository {
    override val sourceLabel: String = "Demo-Katalog"
    override val isDemo: Boolean = true

    private val sourceId = "demo"

    private fun art(name: String): String =
        "android.resource://${context.packageName}/drawable/$name"

    private val liveChannels = listOf(
        PosterItem(
            id = "live-1",
            sourceId = sourceId,
            kind = MediaKind.LIVE,
            title = "Kisium One",
            subtitle = "Live · Showcase",
            imageUrl = art("demo_live_1"),
            backdropUrl = art("demo_live_1"),
            categoryName = "Unterhaltung",
            streamId = 9001,
            directUrl = DEMO_HLS,
            previewUrl = DEMO_HLS,
        ),
        PosterItem(
            id = "live-2",
            sourceId = sourceId,
            kind = MediaKind.LIVE,
            title = "Kisium Cinema",
            subtitle = "Live · Filme",
            imageUrl = art("demo_live_2"),
            backdropUrl = art("demo_live_2"),
            categoryName = "Filme",
            streamId = 9002,
            directUrl = DEMO_HLS_ALT,
            previewUrl = DEMO_HLS_ALT,
        ),
        PosterItem(
            id = "live-3",
            sourceId = sourceId,
            kind = MediaKind.LIVE,
            title = "Kisium Doku",
            subtitle = "Live · Wissen",
            imageUrl = art("demo_live_3"),
            backdropUrl = art("demo_live_3"),
            categoryName = "Doku",
            streamId = 9003,
            directUrl = DEMO_HLS,
            previewUrl = DEMO_HLS,
        ),
        PosterItem(
            id = "live-4",
            sourceId = sourceId,
            kind = MediaKind.LIVE,
            title = "Kisium Sport",
            subtitle = "Live · Sport",
            imageUrl = art("demo_live_4"),
            backdropUrl = art("demo_live_4"),
            categoryName = "Sport",
            streamId = 9004,
            directUrl = DEMO_HLS_ALT,
            previewUrl = DEMO_HLS_ALT,
        ),
    )

    private val movies = listOf(
        PosterItem(
            id = "movie-1",
            sourceId = sourceId,
            kind = MediaKind.MOVIE,
            title = "Stille Wasser",
            subtitle = "2025 · Drama · ★ 8.1",
            imageUrl = art("demo_poster_stille"),
            backdropUrl = art("demo_hero_stille"),
            rating = 8.1,
            year = "2025",
            plot = "Eine Seenplatte, ein verschwundenes Boot und eine Ermittlerin, die selbst " +
                "nicht mehr weiß, welcher Erinnerung sie trauen kann.",
            categoryName = "Drama",
            streamId = 101,
            containerExtension = "mp4",
            added = 1_760_000_000L,
            duration = "1 Std. 52 Min.",
            previewUrl = DEMO_MP4_1,
            directUrl = DEMO_MP4_1,
        ),
        PosterItem(
            id = "movie-2",
            sourceId = sourceId,
            kind = MediaKind.MOVIE,
            title = "Die letzte Frequenz",
            subtitle = "2024 · Sci-Fi · ★ 7.9",
            imageUrl = art("demo_poster_frequenz"),
            backdropUrl = art("demo_hero_frequenz"),
            rating = 7.9,
            year = "2024",
            plot = "Ein Nachtmoderator empfängt eine Sendung, die erst in sechs Stunden " +
                "ausgestrahlt werden wird.",
            categoryName = "Sci-Fi",
            streamId = 102,
            containerExtension = "mp4",
            added = 1_750_000_000L,
            duration = "1 Std. 38 Min.",
            previewUrl = DEMO_MP4_2,
            directUrl = DEMO_MP4_2,
        ),
        PosterItem(
            id = "movie-3",
            sourceId = sourceId,
            kind = MediaKind.MOVIE,
            title = "Nachtzug",
            subtitle = "2023 · Thriller · ★ 7.6",
            imageUrl = art("demo_poster_nachtzug"),
            backdropUrl = art("demo_hero_nachtzug"),
            rating = 7.6,
            year = "2023",
            plot = "Zwischen Wien und Paris verschwindet ein Passagier – und niemand will ihn " +
                "gesehen haben.",
            categoryName = "Thriller",
            streamId = 103,
            containerExtension = "mp4",
            added = 1_740_000_000L,
            duration = "1 Std. 44 Min.",
            previewUrl = DEMO_MP4_3,
            directUrl = DEMO_MP4_3,
        ),
        PosterItem(
            id = "movie-4",
            sourceId = sourceId,
            kind = MediaKind.MOVIE,
            title = "Glut",
            subtitle = "2025 · Drama · ★ 8.3",
            imageUrl = art("demo_poster_glut"),
            backdropUrl = art("demo_hero_glut"),
            rating = 8.3,
            year = "2025",
            plot = "Drei Generationen, ein Weingut und ein Sommer, in dem alles verbrennt, " +
                "was lange verschwiegen wurde.",
            categoryName = "Drama",
            streamId = 104,
            containerExtension = "mp4",
            added = 1_765_000_000L,
            duration = "2 Std. 06 Min.",
            previewUrl = DEMO_MP4_4,
            directUrl = DEMO_MP4_4,
        ),
    )

    private val series = listOf(
        PosterItem(
            id = "series-1",
            sourceId = sourceId,
            kind = MediaKind.SERIES,
            title = "Nordlicht",
            subtitle = "2025 · Krimi · 2 Staffeln",
            imageUrl = art("demo_poster_nordlicht"),
            backdropUrl = art("demo_hero_nordlicht"),
            rating = 8.7,
            year = "2025",
            plot = "Im hohen Norden taut ein jahrzehntealter Fall auf. Kommissarin Liv Berger " +
                "ermittelt gegen die eigene Familie.",
            categoryName = "Krimi",
            seriesId = 501,
            added = 1_770_000_000L,
            previewUrl = DEMO_MP4_2,
        ),
        PosterItem(
            id = "series-2",
            sourceId = sourceId,
            kind = MediaKind.SERIES,
            title = "Kosmos 9",
            subtitle = "2024 · Sci-Fi · 1 Staffel",
            imageUrl = art("demo_poster_kosmos"),
            backdropUrl = art("demo_hero_kosmos"),
            rating = 8.2,
            year = "2024",
            plot = "Die Besatzung einer Forschungsstation empfängt ein Signal aus dem eigenen " +
                "Kontrollraum – zehn Jahre in der Zukunft.",
            categoryName = "Sci-Fi",
            seriesId = 502,
            added = 1_755_000_000L,
            previewUrl = DEMO_HLS_ALT,
        ),
        PosterItem(
            id = "series-3",
            sourceId = sourceId,
            kind = MediaKind.SERIES,
            title = "Hafenkante",
            subtitle = "2025 · Alltag · 3 Staffeln",
            imageUrl = art("demo_poster_hafenkante"),
            backdropUrl = art("demo_hero_hafenkante"),
            rating = 7.7,
            year = "2025",
            plot = "Eine kleine Reederei, große Geheimnisse und eine Stadt, die niemals " +
                "schläft.",
            categoryName = "Alltag",
            seriesId = 503,
            added = 1_760_000_000L,
            previewUrl = DEMO_HLS,
        ),
        PosterItem(
            id = "series-4",
            sourceId = sourceId,
            kind = MediaKind.SERIES,
            title = "Archiv 77",
            subtitle = "2023 · Mystery · 1 Staffel",
            imageUrl = art("demo_poster_archiv"),
            backdropUrl = art("demo_hero_archiv"),
            rating = 8.0,
            year = "2023",
            plot = "In einem vergessenen Stadtarchiv finden zwei Praktikanten Akten, die es " +
                "offiziell nie gegeben hat.",
            categoryName = "Mystery",
            seriesId = 504,
            added = 1_730_000_000L,
            previewUrl = DEMO_HLS_ALT,
        ),
    )

    override suspend fun loadHome(): HomeFeed {
        val continueItems = runCatching {
            history.continueWatching.first().map { it.item.copy(resumeProgress = it.progress) }
        }.getOrDefault(emptyList())

        val forYou = (movies + series)
            .sortedByDescending { it.rating ?: 0.0 }
            .distinctBy { it.cardKey }
            .take(20)
        val newest = (movies + series)
            .sortedByDescending { it.added ?: 0L }
            .distinctBy { it.cardKey }
            .take(20)

        val rails = buildList {
            if (continueItems.isNotEmpty()) add(HomeRail("Weiterschauen", RailKind.CONTINUE, continueItems))
            add(HomeRail("Jetzt live", RailKind.LIVE, liveChannels))
            add(HomeRail("Für dich", RailKind.MOVIES, forYou))
            add(HomeRail("Neu", RailKind.NEW, newest))
            add(HomeRail("Filme", RailKind.MOVIES, movies))
            add(HomeRail("Serien", RailKind.SERIES, series))
        }

        val hero = series.firstOrNull { it.previewUrl != null } ?: movies.firstOrNull()

        return HomeFeed(
            hero = hero,
            rails = rails,
            sourceLabel = sourceLabel,
            isDemo = true,
        )
    }

    override suspend fun loadCategories(kind: MediaKind): List<Category> {
        return when (kind) {
            MediaKind.LIVE -> listOf(
                Category("all", "Alle Sender", kind),
                Category("Unterhaltung", "Unterhaltung", kind),
                Category("Filme", "Filme", kind),
                Category("Doku", "Doku", kind),
                Category("Sport", "Sport", kind),
            )

            MediaKind.MOVIE -> listOf(
                Category("all", "Alle Filme", kind),
                Category("Drama", "Drama", kind),
                Category("Sci-Fi", "Sci-Fi", kind),
                Category("Thriller", "Thriller", kind),
            )

            MediaKind.SERIES -> listOf(
                Category("all", "Alle Serien", kind),
                Category("Krimi", "Krimi", kind),
                Category("Sci-Fi", "Sci-Fi", kind),
                Category("Mystery", "Mystery", kind),
                Category("Alltag", "Alltag", kind),
            )
        }
    }

    override suspend fun loadCategoryItems(kind: MediaKind, categoryId: String?): List<PosterItem> {
        val source = when (kind) {
            MediaKind.LIVE -> liveChannels
            MediaKind.MOVIE -> movies
            MediaKind.SERIES -> series
        }
        if (categoryId.isNullOrBlank() || categoryId == "all") return source
        return source.filter { it.categoryName == categoryId }
    }

    override suspend fun loadSeries(seriesId: String): SeriesDetail {
        val show = series.firstOrNull { it.id == seriesId }
            ?: throw XtreamException("Demo-Serie nicht gefunden.")
        val seasons = (1..2).map { season ->
            SeasonEpisodes(
                season = season,
                episodes = (1..4).map { number ->
                    val stream = 7000 + season * 100 + number
                    Episode(
                        id = "$seriesId-$season-$number",
                        seriesId = seriesId,
                        season = season,
                        number = number,
                        title = demoEpisodeTitle(show.title, season, number),
                        plot = "Liv Berger folgt einer Spur, die tiefer in die Vergangenheit führt.",
                        stillUrl = show.backdropUrl,
                        streamId = stream,
                        containerExtension = "mp4",
                        duration = "48 Min.",
                    )
                },
            )
        }
        return SeriesDetail(show, seasons)
    }

    override suspend fun nowNext(channel: PosterItem): EpgNowNext {
        if (channel.kind != MediaKind.LIVE) return EpgNowNext()
        val slot = 30 * 60 * 1000L
        val now = System.currentTimeMillis()
        val start = (now / slot) * slot
        val titles = when (channel.id) {
            "live-1" -> listOf("Kisium Journal", "Die große Show", "Nachtstudio")
            "live-2" -> listOf("Stille Wasser", "Nachtzug", "Glut")
            "live-3" -> listOf("Wildes Deutschland", "Technik von morgen", "Archiv der Dinge")
            else -> listOf("Matchday Live", "Taktiktafel", "Sport kompakt")
        }
        val index = ((now / slot) % titles.size).toInt()
        val current = EpgEntry(
            channelId = channel.id,
            startMillis = start,
            endMillis = start + slot,
            title = titles[index],
            description = "Demo-EPG · Now/Next für ${channel.title}",
        )
        val next = EpgEntry(
            channelId = channel.id,
            startMillis = start + slot,
            endMillis = start + slot * 2,
            title = titles[(index + 1) % titles.size],
            description = "Demo-EPG · als Nächstes",
        )
        return EpgNowNext(current, next)
    }

    override suspend fun playback(item: PosterItem, episode: Episode?): PlaybackRequest {
        if (item.kind == MediaKind.SERIES) {
            val episode = episode ?: throw XtreamException("Bitte eine Folge auswählen.")
            return PlaybackRequest(
                itemKey = item.cardKey,
                title = item.title,
                subtitle = "S${episode.season} · E${episode.number} · ${episode.title}",
                url = DEMO_MP4_2,
                kind = MediaKind.SERIES,
                isLive = false,
                artworkUrl = item.backdropUrl,
            )
        }
        if (item.kind == MediaKind.LIVE) {
            return PlaybackRequest(
                itemKey = item.cardKey,
                title = item.title,
                subtitle = item.subtitle,
                url = item.directUrl ?: DEMO_HLS,
                kind = MediaKind.LIVE,
                isLive = true,
                artworkUrl = item.backdropUrl,
            )
        }
        return PlaybackRequest(
            itemKey = item.cardKey,
            title = item.title,
            subtitle = item.subtitle,
            url = item.directUrl ?: DEMO_MP4_1,
            kind = MediaKind.MOVIE,
            isLive = false,
            artworkUrl = item.backdropUrl,
        )
    }

    override suspend fun testConnection(): ConnectionResult {
        return ConnectionResult(
            success = true,
            message = "Demo-Modus aktiv. Für echte Sender in den Einstellungen Xtream-Zugang eintragen.",
        )
    }

    private fun demoEpisodeTitle(show: String, season: Int, number: Int): String {
        val titles = listOf(
            "Eisiger Empfang", "Die zweite Spur", "Nebel", "Unter der Oberfläche",
            "Alte Narben", "Der Anruf", "Ohne Alibi", "Letztes Licht",
        )
        return titles[((season - 1) * 4 + number - 1) % titles.size]
    }

    companion object {
        const val DEMO_HLS = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        const val DEMO_HLS_ALT = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        const val DEMO_MP4_1 = "https://media.w3.org/2010/05/bunny/trailer.mp4"
        const val DEMO_MP4_2 = "https://media.w3.org/2010/05/sintel/trailer.mp4"
        const val DEMO_MP4_3 = "https://media.w3.org/2010/05/video/movie_300.mp4"
        const val DEMO_MP4_4 = "https://media.w3.org/2010/05/video/movie_300.mp4"
    }
}
