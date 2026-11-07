package de.kisium.tv.source.catalogjson

import com.google.gson.Gson
import de.kisium.tv.core.model.MediaKind
import de.kisium.tv.core.model.PosterItem
import java.util.Locale

class CatalogJsonParser(private val gson: Gson) {

    fun parse(raw: String): CatalogDocument {
        if (raw.isBlank()) throw CatalogJsonException("Der Katalog ist leer.")
        val document = runCatching {
            gson.fromJson(raw, CatalogDocument::class.java)
        }.getOrElse { throw CatalogJsonException("Der Katalog ist kein gültiges JSON.", it) }
            ?: throw CatalogJsonException("Der Katalog ist leer.")
        if (document.schemaVersion != 1) {
            throw CatalogJsonException(
                "Nicht unterstützte schema_version ${document.schemaVersion}; erwartet wird 1.",
            )
        }
        return document
    }

    fun toPosterItems(document: CatalogDocument): List<PosterItem> {
        val fallbackSourceId = document.source?.id?.trim()?.takeIf { it.isNotEmpty() }
            ?: "catalog-json"
        val byId = linkedMapOf<Int, PosterItem>()
        document.movies.forEach { movie ->
            val title = movie.title?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            val streamId = movie.streamId ?: movie.id?.trim()?.toIntOrNull() ?: return@forEach
            val rating = movie.rating
            byId[streamId] = PosterItem(
                id = streamId.toString(),
                sourceId = movie.sourceId?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackSourceId,
                kind = MediaKind.MOVIE,
                title = title,
                subtitle = movie.subtitle?.trim()?.takeIf { it.isNotEmpty() },
                imageUrl = movie.imageUrl?.trim()?.takeIf { it.isNotEmpty() },
                backdropUrl = movie.backdropUrl?.trim()?.takeIf { it.isNotEmpty() },
                rating = rating,
                plot = movie.plot?.trim()?.takeIf { it.isNotEmpty() },
                year = movie.year?.toString(),
                categoryName = movie.categoryName?.trim()?.takeIf { it.isNotEmpty() } ?: "Filme",
                streamId = streamId,
                containerExtension = movie.containerExtension?.trim()?.takeIf { it.isNotEmpty() } ?: "mp4",
                added = movie.added,
                isDemo = false,
                isLogo = movie.isLogo == true,
            )
        }
        return byId.values.sortedWith(
            compareByDescending<PosterItem> { it.added ?: 0L }
                .thenBy { it.title.lowercase(Locale.GERMANY) },
        )
    }
}
