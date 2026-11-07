package de.kisium.tv.source.catalogjson

import com.google.gson.annotations.SerializedName

data class CatalogDocument(
    @SerializedName("schema_version") val schemaVersion: Int = 0,
    @SerializedName("generated_at") val generatedAt: String? = null,
    val source: CatalogSourceDto? = null,
    val pagination: CatalogPaginationDto? = null,
    val movies: List<CatalogMovieDto> = emptyList(),
)

data class CatalogSourceDto(
    val id: String? = null,
    val type: String? = null,
)

data class CatalogPaginationDto(
    @SerializedName("page_size") val pageSize: Int? = null,
    val total: Int? = null,
)

data class CatalogMovieDto(
    val id: String? = null,
    @SerializedName("stream_id") val streamId: Int? = null,
    @SerializedName("source_id") val sourceId: String? = null,
    val kind: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    @SerializedName("category_id") val categoryId: String? = null,
    @SerializedName("category_name") val categoryName: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("backdrop_url") val backdropUrl: String? = null,
    @SerializedName("is_logo") val isLogo: Boolean? = null,
    val plot: String? = null,
    val year: Int? = null,
    val rating: Double? = null,
    val added: Long? = null,
    @SerializedName("container_extension") val containerExtension: String? = null,
)

class CatalogJsonException(message: String, cause: Throwable? = null) : Exception(message, cause)
