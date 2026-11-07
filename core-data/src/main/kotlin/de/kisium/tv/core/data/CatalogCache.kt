package de.kisium.tv.core.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import de.kisium.tv.core.model.Category
import de.kisium.tv.core.model.PosterItem

/**
 * Small JSON-backed cache for catalog responses. Xtream servers are often slow
 * and rate-limited; the first paint should always come from Room.
 */
class CatalogCache(
    private val dao: CatalogCacheDao,
    private val gson: Gson,
) {
    suspend fun readItems(key: String, maxAgeMs: Long = DEFAULT_MAX_AGE_MS): List<PosterItem>? {
        val row = dao.get(prefix(key)) ?: return null
        if (System.currentTimeMillis() - row.updatedAt > maxAgeMs) return null
        return runCatching {
            gson.fromJson<List<PosterItem>>(
                row.payload,
                object : TypeToken<List<PosterItem>>() {}.type,
            )
        }.getOrNull()
    }

    suspend fun writeItems(key: String, value: List<PosterItem>) {
        dao.put(
            CatalogCacheEntity(
                cacheKey = prefix(key),
                payload = gson.toJson(value),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun readCategories(key: String, maxAgeMs: Long = DEFAULT_MAX_AGE_MS): List<Category>? {
        val row = dao.get(prefix(key)) ?: return null
        if (System.currentTimeMillis() - row.updatedAt > maxAgeMs) return null
        return runCatching {
            gson.fromJson<List<Category>>(
                row.payload,
                object : TypeToken<List<Category>>() {}.type,
            )
        }.getOrNull()
    }

    suspend fun writeCategories(key: String, value: List<Category>) {
        dao.put(
            CatalogCacheEntity(
                cacheKey = prefix(key),
                payload = gson.toJson(value),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun clearSource(sourceId: String) {
        dao.deleteByPrefix(prefix(sourceId))
    }

    suspend fun clearAll() {
        dao.deleteAll()
    }

    private fun prefix(key: String): String = "catalog:$key"

    companion object {
        private const val DEFAULT_MAX_AGE_MS = 6L * 60L * 60L * 1000L
    }
}
