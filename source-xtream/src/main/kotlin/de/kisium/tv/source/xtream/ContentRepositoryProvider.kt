package de.kisium.tv.source.xtream

import android.content.Context
import de.kisium.tv.core.data.CatalogCache
import de.kisium.tv.core.data.EpgCache
import de.kisium.tv.core.data.SettingsRepository
import de.kisium.tv.core.data.WatchHistoryRepository
import de.kisium.tv.core.model.ContentRepository
import de.kisium.tv.source.catalogjson.CatalogJsonContentRepository
import de.kisium.tv.source.catalogjson.CatalogJsonLoader
import de.kisium.tv.source.catalogjson.CatalogJsonParser

class ContentRepositoryProvider(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val apiFactory: XtreamApiFactory,
    private val cache: CatalogCache,
    private val epgCache: EpgCache,
    private val history: WatchHistoryRepository,
    private val catalogJsonLoader: CatalogJsonLoader,
    private val catalogJsonParser: CatalogJsonParser,
) {
    @Volatile
    private var cachedKey: String? = null

    @Volatile
    private var cachedRepository: ContentRepository? = null

    fun invalidate() {
        cachedKey = null
        cachedRepository = null
    }

    @Synchronized
    fun current(): ContentRepository {
        val settings = settingsRepository.current
        val useDemo = settings.demoMode || !settings.isConfigured
        val jsonLocation = if (settings.hasJsonCatalog && !useDemo) {
            settings.catalogJsonUri.trim()
        } else {
            ""
        }
        val key = if (useDemo) {
            "demo"
        } else {
            "${XtreamConfig(settings.serverUrl, settings.username, settings.password).sourceId}|json:$jsonLocation"
        }
        cachedRepository?.let { existing ->
            if (cachedKey == key) return existing
        }

        val repository = if (useDemo) {
            DemoContentRepository(context.applicationContext, history)
        } else {
            val config = XtreamConfig(settings.serverUrl, settings.username, settings.password)
            val xtream = XtreamRepository(
                config = config,
                api = apiFactory.create(config),
                apiFactory = apiFactory,
                cache = cache,
                epgCache = epgCache,
                history = history,
            )
            if (jsonLocation.isNotBlank()) {
                CatalogJsonContentRepository(
                    location = jsonLocation,
                    loader = catalogJsonLoader,
                    parser = catalogJsonParser,
                    fallback = xtream,
                    history = history,
                )
            } else {
                xtream
            }
        }
        cachedKey = key
        cachedRepository = repository
        return repository
    }
}
