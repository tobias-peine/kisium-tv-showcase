package de.kisium.tv.core.model

import java.util.concurrent.TimeUnit

enum class MediaKind {
    LIVE,
    MOVIE,
    SERIES,
}

enum class RailKind {
    CONTINUE,
    LIVE,
    MOVIES,
    SERIES,
    NEW,
}

data class Category(
    val id: String,
    val name: String,
    val kind: MediaKind,
)

/**
 * A single card in the UI. The same type is used for live channels, movies and
 * series so that rails and detail screens stay independent from the source.
 */
data class PosterItem(
    val id: String,
    val sourceId: String,
    val kind: MediaKind,
    val title: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val backdropUrl: String? = null,
    val rating: Double? = null,
    val year: String? = null,
    val plot: String? = null,
    val categoryName: String? = null,
    val streamId: Int? = null,
    val seriesId: Int? = null,
    val containerExtension: String? = null,
    val added: Long? = null,
    val duration: String? = null,
    val previewUrl: String? = null,
    val directUrl: String? = null,
    val resumeProgress: Float? = null,
    val isDemo: Boolean = false,
    val isLogo: Boolean = false,
) {
    val cardKey: String get() = "$sourceId:${kind.name}:$id"
}

data class Episode(
    val id: String,
    val seriesId: String,
    val season: Int,
    val number: Int,
    val title: String,
    val plot: String? = null,
    val stillUrl: String? = null,
    val streamId: Int? = null,
    val containerExtension: String? = null,
    val duration: String? = null,
)

data class SeasonEpisodes(
    val season: Int,
    val episodes: List<Episode>,
)

data class SeriesDetail(
    val series: PosterItem,
    val seasons: List<SeasonEpisodes>,
)

sealed interface DetailContent {
    data class Movie(val item: PosterItem, val nowNext: EpgNowNext? = null) : DetailContent
    data class Series(val detail: SeriesDetail) : DetailContent
}

data class EpgEntry(
    val channelId: String,
    val startMillis: Long,
    val endMillis: Long,
    val title: String,
    val description: String? = null,
) {
    fun progressAt(nowMillis: Long): Float {
        if (endMillis <= startMillis) return 0f
        return ((nowMillis - startMillis).toFloat() / (endMillis - startMillis).toFloat())
            .coerceIn(0f, 1f)
    }

    fun timeRange(): String {
        val formatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.GERMANY)
        return "${formatter.format(java.util.Date(startMillis))}–${formatter.format(java.util.Date(endMillis))}"
    }
}

data class EpgNowNext(
    val now: EpgEntry? = null,
    val next: EpgEntry? = null,
)

data class PlaybackRequest(
    val itemKey: String,
    val title: String,
    val subtitle: String? = null,
    val url: String,
    val kind: MediaKind,
    val isLive: Boolean = false,
    val artworkUrl: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val startPositionMs: Long = 0L,
    val alternateUrl: String? = null,
)

data class HomeRail(
    val title: String,
    val kind: RailKind,
    val items: List<PosterItem>,
)

data class HomeFeed(
    val hero: PosterItem?,
    val rails: List<HomeRail>,
    val sourceLabel: String,
    val isDemo: Boolean,
)

data class ContinueItem(
    val item: PosterItem,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    val progress: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    val remainingLabel: String
        get() {
            val remainingMinutes = TimeUnit.MILLISECONDS.toMinutes((durationMs - positionMs).coerceAtLeast(0L))
            return if (remainingMinutes < 1) "Fast zu Ende" else "Noch ${remainingMinutes} Min."
        }
}

object KisiumServerDefaults {
    /** Muss auf die LAN-Adresse des Proxy-Servers angepasst werden. */
    const val PROXY_BASE_URL: String = "https://your-proxy.example"
    const val CATALOG_JSON_URI: String = PROXY_BASE_URL + "/catalog.json"
}

data class AppSettings(
    val serverUrl: String = KisiumServerDefaults.PROXY_BASE_URL,
    val username: String = "",
    val password: String = "",
    val tmdbApiKey: String = "",
    val autoplayPreview: Boolean = true,
    val demoMode: Boolean = true,
    val useJsonCatalog: Boolean = true,
    val catalogJsonUri: String = KisiumServerDefaults.CATALOG_JSON_URI,
) {
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    val activeSource: String
        get() = if (demoMode || !isConfigured) "Demo" else username

    val hasJsonCatalog: Boolean
        get() = useJsonCatalog && catalogJsonUri.isNotBlank()
}

data class AccountInfo(
    val status: String = "",
    val expiresAt: String? = null,
    val isTrial: Boolean = false,
    val activeConnections: Int? = null,
    val maxConnections: Int? = null,
    val serverTimezone: String? = null,
)

data class ConnectionResult(
    val success: Boolean,
    val message: String,
    val account: AccountInfo? = null,
)

interface ContentRepository {
    val sourceLabel: String
    val isDemo: Boolean

    suspend fun loadHome(): HomeFeed
    suspend fun loadCategories(kind: MediaKind): List<Category>
    suspend fun loadCategoryItems(kind: MediaKind, categoryId: String?): List<PosterItem>
    suspend fun loadSeries(seriesId: String): SeriesDetail
    suspend fun nowNext(channel: PosterItem): EpgNowNext
    suspend fun playback(item: PosterItem, episode: Episode? = null): PlaybackRequest
    suspend fun testConnection(): ConnectionResult
}

interface MetadataProvider {
    suspend fun enrich(item: PosterItem): PosterItem
}
