package de.kisium.tv.core.data

import de.kisium.tv.core.model.EpgEntry
import de.kisium.tv.core.model.EpgNowNext

class EpgCache(
    private val dao: EpgDao,
) {
    suspend fun read(sourceId: String, channelId: String, now: Long = System.currentTimeMillis()): EpgNowNext {
        val entries = dao.upcoming(sourceId, channelId, now).map { entity ->
            EpgEntry(
                channelId = entity.channelId,
                startMillis = entity.startMillis,
                endMillis = entity.endMillis,
                title = entity.title,
                description = entity.description,
            )
        }
        val current = entries.firstOrNull { it.startMillis <= now && it.endMillis > now }
        val next = entries.firstOrNull { it.startMillis > now }
        return EpgNowNext(now = current, next = next)
    }

    suspend fun write(sourceId: String, channelId: String, entries: List<EpgEntry>) {
        dao.deleteChannel(sourceId, channelId)
        if (entries.isEmpty()) return
        dao.insertAll(
            entries.map { entry ->
                EpgEntity(
                    sourceId = sourceId,
                    channelId = channelId,
                    startMillis = entry.startMillis,
                    endMillis = entry.endMillis,
                    title = entry.title,
                    description = entry.description,
                )
            },
        )
    }

    suspend fun clearSource(sourceId: String) {
        dao.deleteSource(sourceId)
    }

    suspend fun clearAll() {
        dao.deleteAll()
    }
}
