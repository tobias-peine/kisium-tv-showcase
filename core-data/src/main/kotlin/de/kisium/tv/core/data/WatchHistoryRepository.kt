package de.kisium.tv.core.data

import com.google.gson.Gson
import de.kisium.tv.core.model.ContinueItem
import de.kisium.tv.core.model.MediaKind
import de.kisium.tv.core.model.PosterItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class WatchHistoryRepository(
    private val dao: WatchProgressDao,
    private val gson: Gson,
) {
    val continueWatching: Flow<List<ContinueItem>> =
        dao.observeAll().map { rows ->
            rows.mapNotNull { row ->
                val item = runCatching { gson.fromJson(row.payload, PosterItem::class.java) }.getOrNull()
                    ?: return@mapNotNull null
                ContinueItem(
                    item = item,
                    positionMs = row.positionMs,
                    durationMs = row.durationMs,
                    updatedAt = row.updatedAt,
                )
            }
        }

    suspend fun get(itemKey: String): ContinueItem? = withContext(Dispatchers.IO) {
        val row = dao.get(itemKey) ?: return@withContext null
        val item = runCatching { gson.fromJson(row.payload, PosterItem::class.java) }.getOrNull()
            ?: return@withContext null
        ContinueItem(item, row.positionMs, row.durationMs, row.updatedAt)
    }

    suspend fun save(item: PosterItem, positionMs: Long, durationMs: Long) = withContext(Dispatchers.IO) {
        if (item.kind == MediaKind.LIVE || durationMs <= 0L) return@withContext
        val progress = positionMs.toFloat() / durationMs.toFloat()
        if (progress < 0.02f || progress > 0.95f) {
            dao.delete(item.cardKey)
            return@withContext
        }
        dao.upsert(
            WatchProgressEntity(
                itemKey = item.cardKey,
                payload = gson.toJson(item),
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun remove(item: PosterItem) = withContext(Dispatchers.IO) {
        dao.delete(item.cardKey)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        dao.deleteAll()
    }
}
