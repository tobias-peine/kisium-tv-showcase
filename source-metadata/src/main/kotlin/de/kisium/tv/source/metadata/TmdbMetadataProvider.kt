package de.kisium.tv.source.metadata

import com.google.gson.Gson
import de.kisium.tv.core.model.MediaKind
import de.kisium.tv.core.model.MetadataProvider
import de.kisium.tv.core.model.PosterItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Optional TMDB enrichment. No API key means a no-op: Kisium TV must work
 * completely without any Google or third-party account.
 */
class TmdbMetadataProvider(
    private val apiKey: String,
    private val client: OkHttpClient,
    private val gson: Gson,
) : MetadataProvider {
    override suspend fun enrich(item: PosterItem): PosterItem = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || item.kind == MediaKind.LIVE) return@withContext item
        runCatching {
            val url = "https://api.themoviedb.org/3/search/multi".toHttpUrl().newBuilder()
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("language", "de-DE")
                .addQueryParameter("include_adult", "false")
                .addQueryParameter("query", item.title)
                .build()
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "KisiumTV/1.0")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use item
                val body = response.body?.string() ?: return@use item
                val parsed = gson.fromJson(body, TmdbSearchResponse::class.java)
                val wantedType = if (item.kind == MediaKind.SERIES) "tv" else "movie"
                val match = parsed?.results
                    ?.firstOrNull { it.mediaType == wantedType }
                    ?: parsed?.results?.firstOrNull()
                    ?: return@use item
                item.copy(
                    plot = item.plot?.takeIf { it.isNotBlank() } ?: match.overview?.takeIf { it.isNotBlank() },
                    imageUrl = item.imageUrl?.takeIf { it.isNotBlank() }
                        ?: match.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
                    backdropUrl = item.backdropUrl?.takeIf { it.isNotBlank() }
                        ?: match.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
                    rating = item.rating ?: match.voteAverage?.takeIf { it > 0 },
                    year = item.year ?: (match.releaseDate ?: match.firstAirDate)?.take(4),
                )
            }
        }.getOrDefault(item)
    }
}

private data class TmdbSearchResponse(
    val results: List<TmdbResult>? = null,
)

private data class TmdbResult(
    val id: Long? = null,
    @com.google.gson.annotations.SerializedName("media_type") val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    @com.google.gson.annotations.SerializedName("poster_path") val posterPath: String? = null,
    @com.google.gson.annotations.SerializedName("backdrop_path") val backdropPath: String? = null,
    @com.google.gson.annotations.SerializedName("vote_average") val voteAverage: Double? = null,
    @com.google.gson.annotations.SerializedName("release_date") val releaseDate: String? = null,
    @com.google.gson.annotations.SerializedName("first_air_date") val firstAirDate: String? = null,
)
