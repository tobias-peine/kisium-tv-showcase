package de.kisium.tv.source.catalogjson

import android.util.Log
import de.kisium.tv.core.data.WatchHistoryRepository
import de.kisium.tv.core.model.Category
import de.kisium.tv.core.model.ConnectionResult
import de.kisium.tv.core.model.ContentRepository
import de.kisium.tv.core.model.EpgNowNext
import de.kisium.tv.core.model.Episode
import de.kisium.tv.core.model.HomeFeed
import de.kisium.tv.core.model.HomeRail
import de.kisium.tv.core.model.MediaKind
import de.kisium.tv.core.model.PlaybackRequest
import de.kisium.tv.core.model.PosterItem
import de.kisium.tv.core.model.RailKind
import de.kisium.tv.core.model.SeriesDetail
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reads the precomputed catalog JSON and combines its movie catalog with the
 * existing Xtream repository. Live TV, series and playback stay delegated to
 * Xtream, because the JSON intentionally contains no credentials or URLs.
 */
class CatalogJsonContentRepository(
    private val location: String,
    private val loader: CatalogJsonLoader,
    private val parser: CatalogJsonParser,
    private val fallback: ContentRepository,
    private val history: WatchHistoryRepository,
    private val cacheTtlMillis: Long = 5 * 60 * 1000L,
) : ContentRepository {

    override val sourceLabel: String = "Netzkino VOD"
    override val isDemo: Boolean = false

    private val mutex = Mutex()

    @Volatile
    private var snapshot: Snapshot? = null

    override suspend fun loadHome(): HomeFeed = coroutineScope {
        val jsonDeferred = async { loadSnapshotSafely() }
        val baseDeferred = async {
            runCatching { fallback.loadHome() }
                .onFailure { Log.w(TAG, "Xtream-Fallback für Live/Serien fehlgeschlagen", it) }
                .getOrNull()
        }
        val json = jsonDeferred.await()
        // The JSON catalog is the primary path. Do not let a slow/unreachable
        // Xtream panel block the movie rails for a minute.
        val baseHome = withTimeoutOrNull(2_000L) { baseDeferred.await() }
        if (baseHome == null) baseDeferred.cancel()

        val continueItems = runCatching {
            history.continueWatching.first().map { it.item.copy(resumeProgress = it.progress) }
        }.getOrDefault(emptyList())

        val liveRail = baseHome?.rails?.firstOrNull { it.kind == RailKind.LIVE }
        val seriesRail = baseHome?.rails?.firstOrNull { it.kind == RailKind.SERIES }
        val movies = json?.movies.orEmpty()
        val newest = movies.sortedByDescending { it.added ?: 0L }.take(20)
        val forYou = movies.sortedByDescending { it.rating ?: 0.0 }.take(20)

        val rails = buildList {
            if (continueItems.isNotEmpty()) add(HomeRail("Weiterschauen", RailKind.CONTINUE, continueItems))
            liveRail?.let { add(it) }
            if (forYou.isNotEmpty()) add(HomeRail("Für dich", RailKind.MOVIES, forYou))
            if (newest.isNotEmpty()) add(HomeRail("Neu", RailKind.NEW, newest))
            if (movies.isNotEmpty()) add(HomeRail("Filme", RailKind.MOVIES, movies.take(20)))
            val family = movies.filter { movie ->
                val category = movie.categoryName.orEmpty().lowercase()
                FAMILY_CATEGORY_HINTS.any(category::contains)
            }
            if (family.isNotEmpty()) {
                add(HomeRail("Kinder & Familie", RailKind.MOVIES, family.take(20)))
            }
            val animation = movies.filter { movie ->
                movie.categoryName.orEmpty().contains("animation", ignoreCase = true)
            }
            if (animation.isNotEmpty()) {
                add(HomeRail("Animationsfilme", RailKind.MOVIES, animation.take(20)))
            }
            val categoryRails = movies
                .mapNotNull { movie ->
                    movie.categoryName?.trim()?.takeIf { it.isNotEmpty() }?.let { it to movie }
                }
                .groupBy({ it.first }, { it.second })
                .entries
                .filter { it.value.size >= 2 }
                .sortedWith(
                    compareByDescending<Map.Entry<String, List<PosterItem>>> { it.value.size }
                        .thenBy { it.key.lowercase() },
                )
                .take(8)
                .map { (name, items) ->
                    HomeRail(name, RailKind.MOVIES, items.take(20))
                }
            addAll(categoryRails)
            seriesRail?.let { add(it) }
        }

        if (movies.isEmpty() && baseHome == null) {
            throw CatalogJsonException(
                "JSON-Katalog und Xtream-Fallback sind nicht erreichbar.",
            )
        }

        val hero = movies.maxByOrNull { (it.rating ?: 0.0) + if (it.imageUrl != null) 0.5 else 0.0 }
            ?: baseHome?.hero

        HomeFeed(
            hero = hero,
            rails = rails,
            sourceLabel = sourceLabel,
            isDemo = false,
        )
    }

    override suspend fun loadCategories(kind: MediaKind): List<Category> {
        if (kind != MediaKind.MOVIE) return fallback.loadCategories(kind)
        val movies = runCatching { loadSnapshot().movies }.getOrElse {
            return fallback.loadCategories(kind)
        }
        val categories = movies.mapNotNull { it.categoryName?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .sorted()
        return buildList {
            add(Category("", "Alle Filme", MediaKind.MOVIE))
            categories.forEach { add(Category(it, it, MediaKind.MOVIE)) }
        }
    }

    override suspend fun loadCategoryItems(kind: MediaKind, categoryId: String?): List<PosterItem> {
        if (kind != MediaKind.MOVIE) return fallback.loadCategoryItems(kind, categoryId)
        val movies = runCatching { loadSnapshot().movies }.getOrElse {
            return fallback.loadCategoryItems(kind, categoryId)
        }
        if (categoryId.isNullOrBlank()) return movies
        return movies.filter { it.categoryName.equals(categoryId, ignoreCase = true) }
    }

    override suspend fun loadSeries(seriesId: String): SeriesDetail = fallback.loadSeries(seriesId)

    override suspend fun nowNext(channel: PosterItem): EpgNowNext = fallback.nowNext(channel)

    override suspend fun playback(item: PosterItem, episode: Episode?): PlaybackRequest =
        fallback.playback(item, episode)

    override suspend fun testConnection(): ConnectionResult {
        val jsonResult = runCatching { loadSnapshot(force = true) }
        if (jsonResult.isFailure) {
            return ConnectionResult(
                false,
                "JSON-Katalog nicht lesbar: " +
                    (jsonResult.exceptionOrNull()?.message ?: "unbekannter Fehler"),
            )
        }
        val base = fallback.testConnection()
        if (!base.success) return base
        val count = jsonResult.getOrNull()?.movies?.size ?: 0
        return ConnectionResult(
            true,
            "JSON-Katalog mit $count Filmen geladen. ${base.message}",
            base.account,
        )
    }

    private suspend fun loadSnapshotSafely(): Snapshot? {
        return runCatching { loadSnapshot() }
            .onFailure { Log.w(TAG, "JSON-Katalog konnte nicht geladen werden", it) }
            .getOrNull()
    }

    private suspend fun loadSnapshot(force: Boolean = false): Snapshot {
        val current = snapshot
        val now = System.currentTimeMillis()
        if (!force && current != null && now - current.loadedAt <= cacheTtlMillis) {
            return current
        }
        return mutex.withLock {
            val latest = snapshot
            val latestNow = System.currentTimeMillis()
            if (!force && latest != null && latestNow - latest.loadedAt <= cacheTtlMillis) {
                latest
            } else {
                val document = loader.load(location)
                val movies = parser.toPosterItems(document)
                Snapshot(latestNow, movies).also { snapshot = it }
            }
        }
    }

    private data class Snapshot(
        val loadedAt: Long,
        val movies: List<PosterItem>,
    )

    companion object {
        private const val TAG = "CatalogJsonRepository"
        private val FAMILY_CATEGORY_HINTS = listOf(
            "kind",
            "kinder",
            "familie",
            "family",
            "animation",
            "märchen",
            "maerchen",
            "schlaue kinder",
        )
    }
}
