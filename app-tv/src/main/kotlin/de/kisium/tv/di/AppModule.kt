package de.kisium.tv.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.kisium.tv.core.data.CatalogCache
import de.kisium.tv.core.data.EpgCache
import de.kisium.tv.core.data.EpgDao
import de.kisium.tv.core.data.FavoritesRepository
import de.kisium.tv.core.data.KisiumDatabase
import de.kisium.tv.core.data.SettingsRepository
import de.kisium.tv.core.data.WatchHistoryRepository
import de.kisium.tv.core.data.WatchProgressDao
import de.kisium.tv.source.catalogjson.CatalogJsonLoader
import de.kisium.tv.source.catalogjson.CatalogJsonParser
import de.kisium.tv.source.xtream.ContentRepositoryProvider
import de.kisium.tv.source.xtream.XtreamApiFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun gson(): Gson = GsonBuilder().setLenient().create()

    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): KisiumDatabase =
        Room.databaseBuilder(context, KisiumDatabase::class.java, KisiumDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun catalogCache(database: KisiumDatabase, gson: Gson): CatalogCache =
        CatalogCache(database.catalogCacheDao(), gson)

    @Provides
    fun epgDao(database: KisiumDatabase): EpgDao = database.epgDao()

    @Provides
    fun epgCache(epgDao: EpgDao): EpgCache = EpgCache(epgDao)

    @Provides
    fun watchProgressDao(database: KisiumDatabase): WatchProgressDao = database.watchProgressDao()

    @Provides
    fun history(watchProgressDao: WatchProgressDao, gson: Gson): WatchHistoryRepository =
        WatchHistoryRepository(watchProgressDao, gson)

    @Provides
    @Singleton
    fun settingsRepository(@ApplicationContext context: Context): SettingsRepository =
        SettingsRepository(context)

    @Provides
    @Singleton
    fun favoritesRepository(@ApplicationContext context: Context): FavoritesRepository =
        FavoritesRepository(context)

    @Provides
    @Singleton
    fun apiFactory(gson: Gson, client: OkHttpClient): XtreamApiFactory =
        XtreamApiFactory(gson, client)

    @Provides
    @Singleton
    fun catalogJsonParser(gson: Gson): CatalogJsonParser = CatalogJsonParser(gson)

    @Provides
    @Singleton
    fun catalogJsonLoader(
        @ApplicationContext context: Context,
        client: OkHttpClient,
        gson: Gson,
    ): CatalogJsonLoader = CatalogJsonLoader(context, client, gson)

    @Provides
    @Singleton
    fun contentRepositoryProvider(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository,
        apiFactory: XtreamApiFactory,
        catalogCache: CatalogCache,
        epgCache: EpgCache,
        history: WatchHistoryRepository,
        catalogJsonLoader: CatalogJsonLoader,
        catalogJsonParser: CatalogJsonParser,
    ): ContentRepositoryProvider = ContentRepositoryProvider(
        context = context,
        settingsRepository = settingsRepository,
        apiFactory = apiFactory,
        cache = catalogCache,
        epgCache = epgCache,
        history = history,
        catalogJsonLoader = catalogJsonLoader,
        catalogJsonParser = catalogJsonParser,
    )
}
