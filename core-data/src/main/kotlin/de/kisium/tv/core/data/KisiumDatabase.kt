package de.kisium.tv.core.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "catalog_cache")
data class CatalogCacheEntity(
    @PrimaryKey val cacheKey: String,
    val payload: String,
    val updatedAt: Long,
)

@Dao
interface CatalogCacheDao {
    @Query("SELECT * FROM catalog_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun get(key: String): CatalogCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: CatalogCacheEntity)

    @Query("DELETE FROM catalog_cache WHERE cacheKey LIKE :prefix || '%'")
    suspend fun deleteByPrefix(prefix: String)

    @Query("DELETE FROM catalog_cache")
    suspend fun deleteAll()
}

@Entity(
    tableName = "epg_cache",
    primaryKeys = ["sourceId", "channelId", "startMillis"],
)
data class EpgEntity(
    val sourceId: String,
    val channelId: String,
    val startMillis: Long,
    val endMillis: Long,
    val title: String,
    val description: String?,
)

@Dao
interface EpgDao {
    @Query(
        """
        SELECT * FROM epg_cache
        WHERE sourceId = :sourceId AND channelId = :channelId AND endMillis > :now
        ORDER BY startMillis ASC
        LIMIT 2
        """,
    )
    suspend fun upcoming(sourceId: String, channelId: String, now: Long): List<EpgEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<EpgEntity>)

    @Query("DELETE FROM epg_cache WHERE sourceId = :sourceId AND channelId = :channelId")
    suspend fun deleteChannel(sourceId: String, channelId: String)

    @Query("DELETE FROM epg_cache WHERE sourceId = :sourceId")
    suspend fun deleteSource(sourceId: String)

    @Query("DELETE FROM epg_cache")
    suspend fun deleteAll()
}

@Entity(tableName = "watch_progress")
data class WatchProgressEntity(
    @PrimaryKey val itemKey: String,
    val payload: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

@Dao
interface WatchProgressDao {
    @Query("SELECT * FROM watch_progress ORDER BY updatedAt DESC LIMIT 24")
    fun observeAll(): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress WHERE itemKey = :itemKey LIMIT 1")
    suspend fun get(itemKey: String): WatchProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchProgressEntity)

    @Query("DELETE FROM watch_progress WHERE itemKey = :itemKey")
    suspend fun delete(itemKey: String)

    @Query("DELETE FROM watch_progress")
    suspend fun deleteAll()
}

@Database(
    entities = [CatalogCacheEntity::class, EpgEntity::class, WatchProgressEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class KisiumDatabase : RoomDatabase() {
    abstract fun catalogCacheDao(): CatalogCacheDao
    abstract fun epgDao(): EpgDao
    abstract fun watchProgressDao(): WatchProgressDao

    companion object {
        const val NAME = "kisium-tv.db"
    }
}
