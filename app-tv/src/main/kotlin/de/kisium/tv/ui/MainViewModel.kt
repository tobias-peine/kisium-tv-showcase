package de.kisium.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import de.kisium.tv.core.data.CatalogCache
import de.kisium.tv.core.data.EpgCache
import de.kisium.tv.core.data.FavoritesRepository
import de.kisium.tv.core.data.SettingsRepository
import de.kisium.tv.core.data.WatchHistoryRepository
import de.kisium.tv.core.model.AppSettings
import de.kisium.tv.core.model.Category
import de.kisium.tv.core.model.ContentRepository
import de.kisium.tv.core.model.DetailContent
import de.kisium.tv.core.model.Episode
import de.kisium.tv.core.model.MediaKind
import de.kisium.tv.core.model.PosterItem
import de.kisium.tv.core.model.RailKind
import de.kisium.tv.source.catalogjson.CatalogJsonLoader
import de.kisium.tv.source.metadata.TmdbMetadataProvider
import de.kisium.tv.source.xtream.ContentRepositoryProvider
import de.kisium.tv.source.xtream.XtreamApiFactory
import de.kisium.tv.source.xtream.XtreamConfig
import de.kisium.tv.source.xtream.XtreamRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val repositoryProvider: ContentRepositoryProvider,
    private val history: WatchHistoryRepository,
    private val apiFactory: XtreamApiFactory,
    private val catalogCache: CatalogCache,
    private val epgCache: EpgCache,
    private val favorites: FavoritesRepository,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val catalogJsonLoader: CatalogJsonLoader,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MainUiState(
            settings = settingsRepository.current,
            secureStorage = settingsRepository.secureStorage,
            favoriteIds = favorites.ids.value,
        ),
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var homeJob: Job? = null
    private var detailJob: Job? = null
    private var liveJob: Job? = null
    private var catalogJob: Job? = null
    private var playbackJob: Job? = null
    private var lastPlayback: LastPlayback? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        settings = settings,
                        secureStorage = settingsRepository.secureStorage,
                    )
                }
            }
        }
        viewModelScope.launch {
            favorites.ids.collect { ids ->
                _uiState.update { it.copy(favoriteIds = ids) }
            }
        }
        loadHome()
    }

    fun loadHome() {
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            _uiState.update { it.copy(home = HomeUiState.Loading) }
            try {
                val feed = repositoryProvider.current().loadHome()
                _uiState.update { it.copy(home = HomeUiState.Ready(feed)) }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _uiState.update {
                    it.copy(
                        home = HomeUiState.Error(
                            t.message ?: "Der Katalog konnte nicht geladen werden.",
                        ),
                    )
                }
            }
        }
    }

    fun openSettings() {
        _uiState.update {
            it.copy(
                screen = Screen.Settings,
                settingsMessage = null,
                settingsMessageIsError = false,
            )
        }
    }

    fun openLive() {
        _uiState.update {
            it.copy(
                screen = Screen.Live,
                live = it.live.copy(loading = true, error = null),
            )
        }
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            try {
                val repo = repositoryProvider.current()
                val categories = buildList {
                    add(Category("", "Alle Sender", MediaKind.LIVE))
                    addAll(repo.loadCategories(MediaKind.LIVE))
                }
                val selected = _uiState.value.live.selectedCategoryId
                _uiState.update {
                    it.copy(
                        live = it.live.copy(
                            categories = categories,
                            selectedCategoryId = selected,
                            loading = true,
                            error = null,
                        ),
                    )
                }
                val channels = repo.loadCategoryItems(MediaKind.LIVE, selected.ifBlank { null })
                _uiState.update {
                    it.copy(
                        live = it.live.copy(
                            channels = channels,
                            loading = false,
                            error = null,
                        ),
                    )
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _uiState.update {
                    it.copy(
                        live = it.live.copy(
                            loading = false,
                            error = t.message ?: "Senderliste konnte nicht geladen werden.",
                        ),
                    )
                }
            }
        }
    }

    fun selectLiveCategory(categoryId: String) {
        _uiState.update {
            it.copy(
                live = it.live.copy(
                    selectedCategoryId = categoryId,
                    loading = true,
                    error = null,
                ),
            )
        }
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            try {
                val channels = repositoryProvider.current()
                    .loadCategoryItems(MediaKind.LIVE, categoryId.ifBlank { null })
                _uiState.update {
                    it.copy(
                        live = it.live.copy(
                            channels = channels,
                            loading = false,
                            error = null,
                        ),
                    )
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _uiState.update {
                    it.copy(
                        live = it.live.copy(
                            loading = false,
                            error = t.message ?: "Senderliste konnte nicht geladen werden.",
                        ),
                    )
                }
            }
        }
    }

    fun openCategories() {
        _uiState.update {
            it.copy(
                screen = Screen.Categories,
                catalog = it.catalog.copy(
                    loading = true,
                    error = null,
                    favoritesMode = false,
                ),
            )
        }
        loadCatalog(favoritesMode = false)
    }

    fun openFavorites() {
        _uiState.update {
            it.copy(
                screen = Screen.Favorites,
                catalog = it.catalog.copy(
                    loading = true,
                    error = null,
                    favoritesMode = true,
                ),
            )
        }
        loadCatalog(favoritesMode = true)
    }

    fun selectCatalogCategory(categoryId: String) {
        _uiState.update {
            it.copy(
                catalog = it.catalog.copy(
                    selectedCategoryId = categoryId,
                    loading = true,
                    error = null,
                ),
            )
        }
        loadCatalog(favoritesMode = _uiState.value.catalog.favoritesMode)
    }

    fun toggleFavorite(item: PosterItem) {
        favorites.toggle(favoriteKey(item))
        if (_uiState.value.screen == Screen.Favorites) {
            loadCatalog(favoritesMode = true)
        }
    }

    fun isFavorite(item: PosterItem): Boolean = favorites.isFavorite(favoriteKey(item))

    private fun loadCatalog(favoritesMode: Boolean) {
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            try {
                val repo = repositoryProvider.current()
                val categories = if (favoritesMode) {
                    emptyList()
                } else {
                    buildList {
                        add(Category("", "Alle Filme", MediaKind.MOVIE))
                        addAll(repo.loadCategories(MediaKind.MOVIE))
                    }.distinctBy { it.id.ifBlank { "__all__" } }
                }
                val selected = if (favoritesMode) "" else _uiState.value.catalog.selectedCategoryId
                val all = repo.loadCategoryItems(
                    MediaKind.MOVIE,
                    if (favoritesMode) null else selected.ifBlank { null },
                )
                val items = if (favoritesMode) {
                    val ids = favorites.ids.value
                    all.filter { favoriteKey(it) in ids }
                } else {
                    all
                }
                _uiState.update {
                    it.copy(
                        catalog = it.catalog.copy(
                            loading = false,
                            categories = categories,
                            selectedCategoryId = selected,
                            items = items,
                            error = null,
                            favoritesMode = favoritesMode,
                        ),
                    )
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _uiState.update {
                    it.copy(
                        catalog = it.catalog.copy(
                            loading = false,
                            error = t.message ?: "Katalog konnte nicht geladen werden.",
                        ),
                    )
                }
            }
        }
    }

    private fun favoriteKey(item: PosterItem): String =
        item.streamId?.toString() ?: item.id

    fun onItemClick(item: PosterItem) {
        when (item.kind) {
            MediaKind.LIVE -> playItem(item)
            MediaKind.MOVIE, MediaKind.SERIES -> openDetail(item)
        }
    }

    fun playHero(item: PosterItem) {
        if (item.kind == MediaKind.SERIES) {
            viewModelScope.launch {
                _uiState.update { it.copy(playerLoading = true, playerError = null) }
                try {
                    val detail = repositoryProvider.current().loadSeries(item.id)
                    val first = detail.seasons.firstOrNull()?.episodes?.firstOrNull()
                    if (first == null) {
                        _uiState.update {
                            it.copy(
                                playerLoading = false,
                                playerError = "Für diese Serie wurde keine Folge gefunden.",
                            )
                        }
                    } else {
                        playItem(item, first)
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    _uiState.update {
                        it.copy(
                            playerLoading = false,
                            playerError = t.message ?: "Serie konnte nicht geladen werden.",
                        )
                    }
                }
            }
        } else {
            playItem(item)
        }
    }

    fun openDetail(item: PosterItem) {
        if (item.kind == MediaKind.LIVE) {
            playItem(item)
            return
        }
        _uiState.update {
            it.copy(
                screen = Screen.Detail(item),
                detail = DetailUiState.Loading,
            )
        }
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            try {
                val repo = repositoryProvider.current()
                if (item.kind == MediaKind.SERIES) {
                    val detail = repo.loadSeries(item.id)
                    if (!isCurrentDetail(item)) return@launch
                    _uiState.update { it.copy(detail = DetailUiState.Series(detail)) }
                    enrichSeries(item, detail.series)
                } else {
                    if (!isCurrentDetail(item)) return@launch
                    _uiState.update { it.copy(detail = DetailUiState.Movie(item)) }
                    enrichMovie(item)
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                if (!isCurrentDetail(item)) return@launch
                _uiState.update {
                    it.copy(
                        detail = DetailUiState.Error(
                            t.message ?: "Details konnten nicht geladen werden.",
                        ),
                    )
                }
            }
        }
    }

    fun playItem(item: PosterItem, episode: Episode? = null) {
        lastPlayback = LastPlayback(item, episode)
        playbackJob?.cancel()
        _uiState.update { it.copy(playerLoading = true, playerError = null) }
        playbackJob = viewModelScope.launch {
            try {
                val repo = repositoryProvider.current()
                val request = repo.playback(item, episode)
                val resume = if (item.kind == MediaKind.LIVE) {
                    0L
                } else {
                    history.get(item.cardKey)?.positionMs ?: 0L
                }
                val queue = liveQueueFor(item)
                val queueIndex = queue.indexOfFirst { it.cardKey == item.cardKey }
                    .let { if (it >= 0) it else 0 }
                val session = PlayerSession(
                    item = item,
                    request = request.copy(startPositionMs = resume),
                    nowNext = null,
                    liveQueue = queue,
                    queueIndex = queueIndex,
                )
                _uiState.update { it.copy(player = session, playerLoading = false) }
                if (item.kind == MediaKind.LIVE) {
                    val epg = runCatching { repo.nowNext(item) }.getOrNull()
                    if (epg != null) updatePlayerEpg(item, epg)
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _uiState.update {
                    it.copy(
                        playerLoading = false,
                        playerError = t.message ?: "Wiedergabe konnte nicht gestartet werden.",
                    )
                }
            }
        }
    }

    fun retryPlayback() {
        val last = lastPlayback ?: return
        playItem(last.item, last.episode)
    }

    fun nextChannel() = switchChannel(1)

    fun previousChannel() = switchChannel(-1)

    private fun switchChannel(delta: Int) {
        val current = _uiState.value.player ?: return
        if (current.liveQueue.size < 2) return
        val newIndex = ((current.queueIndex + delta) % current.liveQueue.size + current.liveQueue.size) %
            current.liveQueue.size
        val item = current.liveQueue[newIndex]
        if (item.cardKey == current.item.cardKey) return
        lastPlayback = LastPlayback(item, null)
        playbackJob?.cancel()
        _uiState.update { it.copy(playerLoading = true, playerError = null) }
        playbackJob = viewModelScope.launch {
            try {
                val repo = repositoryProvider.current()
                val request = repo.playback(item)
                _uiState.update {
                    it.copy(
                        player = current.copy(
                            item = item,
                            request = request,
                            nowNext = null,
                            queueIndex = newIndex,
                        ),
                        playerLoading = false,
                    )
                }
                val epg = runCatching { repo.nowNext(item) }.getOrNull()
                if (epg != null) updatePlayerEpg(item, epg)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _uiState.update {
                    it.copy(
                        playerLoading = false,
                        playerError = t.message ?: "Sender konnte nicht gewechselt werden.",
                    )
                }
            }
        }
    }

    fun saveProgress(item: PosterItem, positionMs: Long, durationMs: Long) {
        viewModelScope.launch {
            history.save(item, positionMs, durationMs)
        }
    }

    fun dismissPlayer() {
        playbackJob?.cancel()
        playbackJob = null
        _uiState.update {
            it.copy(
                player = null,
                playerLoading = false,
                playerError = null,
            )
        }
        // The player's onDispose writes the final position; refresh the rail
        // afterwards so "Weiterschauen" is visible without restarting the app.
        viewModelScope.launch {
            kotlinx.coroutines.delay(350)
            refreshHomeQuietly()
        }
    }

    private fun refreshHomeQuietly() {
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            runCatching { repositoryProvider.current().loadHome() }
                .onSuccess { feed -> _uiState.update { it.copy(home = HomeUiState.Ready(feed)) } }
        }
    }

    fun saveSettings(value: AppSettings) {
        val normalized = value.copy(
            serverUrl = XtreamConfig.normalizeServerUrl(value.serverUrl),
            username = value.username.trim(),
            password = value.password,
            tmdbApiKey = value.tmdbApiKey.trim(),
            catalogJsonUri = value.catalogJsonUri.trim(),
            useJsonCatalog = value.useJsonCatalog && value.catalogJsonUri.isNotBlank(),
            demoMode = if (
                value.serverUrl.isBlank() || value.username.isBlank() || value.password.isBlank()
            ) {
                true
            } else {
                value.demoMode
            },
        )
        viewModelScope.launch {
            _uiState.update { it.copy(settingsBusy = true, settingsMessage = null) }
            settingsRepository.save(normalized)
            repositoryProvider.invalidate()
            _uiState.update {
                it.copy(
                    settingsBusy = false,
                    settingsMessage = when {
                        normalized.demoMode -> "Gespeichert. Demo-Modus ist aktiv."
                        normalized.hasJsonCatalog -> "Gespeichert. JSON-Katalog und Xtream werden geladen."
                        else -> "Gespeichert. Echte Xtream-Quelle wird geladen."
                    },
                    settingsMessageIsError = false,
                )
            }
            loadHome()
        }
    }

    fun testSettings(value: AppSettings) {
        if (!value.isConfigured) {
            _uiState.update {
                it.copy(
                    settingsMessage = "Bitte Server-URL, Benutzername und Passwort ausfüllen.",
                    settingsMessageIsError = true,
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(settingsBusy = true, settingsMessage = null) }
            try {
                val config = XtreamConfig(value.serverUrl, value.username, value.password)
                val testRepository = XtreamRepository(
                    config = config,
                    api = apiFactory.create(config),
                    apiFactory = apiFactory,
                    cache = catalogCache,
                    epgCache = epgCache,
                    history = history,
                )
                val baseResult = testRepository.testConnection()
                if (value.useJsonCatalog && value.catalogJsonUri.isNotBlank()) {
                    val document = catalogJsonLoader.load(value.catalogJsonUri)
                    val count = document.movies.size
                    _uiState.update {
                        it.copy(
                            settingsBusy = false,
                            settingsMessage = "JSON-Katalog: $count Filme. ${baseResult.message}",
                            settingsMessageIsError = !baseResult.success,
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            settingsBusy = false,
                            settingsMessage = baseResult.message,
                            settingsMessageIsError = !baseResult.success,
                        )
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _uiState.update {
                    it.copy(
                        settingsBusy = false,
                        settingsMessage = "Katalog-/Verbindungstest fehlgeschlagen: " +
                            (t.message ?: "unbekannter Fehler"),
                        settingsMessageIsError = true,
                    )
                }
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            _uiState.update { it.copy(settingsBusy = true, settingsMessage = null) }
            catalogCache.clearAll()
            epgCache.clearAll()
            repositoryProvider.invalidate()
            _uiState.update {
                it.copy(
                    settingsBusy = false,
                    settingsMessage = "Katalog- und EPG-Cache geleert.",
                    settingsMessageIsError = false,
                )
            }
            loadHome()
        }
    }

    fun onBack() {
        val state = _uiState.value
        if (state.player != null || state.playerLoading || state.playerError != null) {
            dismissPlayer()
            return
        }
        if (state.screen != Screen.Home) {
            _uiState.update {
                it.copy(
                    screen = Screen.Home,
                    detail = DetailUiState.Idle,
                    settingsMessage = null,
                )
            }
        }
    }

    fun clearPlayerError() {
        _uiState.update { it.copy(playerError = null) }
    }

    private fun isCurrentDetail(item: PosterItem): Boolean {
        val screen = _uiState.value.screen as? Screen.Detail ?: return false
        return screen.item.cardKey == item.cardKey
    }

    private fun liveQueueFor(item: PosterItem): List<PosterItem> {
        val state = _uiState.value
        val fromHome = (state.home as? HomeUiState.Ready)
            ?.feed
            ?.rails
            ?.firstOrNull { it.kind == RailKind.LIVE }
            ?.items
            .orEmpty()
        val fromLive = state.live.channels
        val all = (fromHome + fromLive + item)
            .distinctBy { it.cardKey }
        return if (item.kind == MediaKind.LIVE) all else emptyList()
    }

    private fun updatePlayerEpg(item: PosterItem, epg: de.kisium.tv.core.model.EpgNowNext) {
        _uiState.update { state ->
            val player = state.player ?: return@update state
            if (player.item.cardKey == item.cardKey) {
                state.copy(player = player.copy(nowNext = epg))
            } else {
                state
            }
        }
    }

    private fun enrichMovie(item: PosterItem) {
        val provider = metadataProvider() ?: return
        viewModelScope.launch {
            val enriched = provider.enrich(item)
            _uiState.update { state ->
                val detail = state.detail
                if (detail is DetailUiState.Movie && detail.item.cardKey == item.cardKey) {
                    state.copy(detail = detail.copy(item = enriched))
                } else {
                    state
                }
            }
        }
    }

    private fun enrichSeries(original: PosterItem, series: PosterItem) {
        val provider = metadataProvider() ?: return
        viewModelScope.launch {
            val enriched = provider.enrich(series)
            _uiState.update { state ->
                val detail = state.detail
                if (detail is DetailUiState.Series &&
                    detail.detail.series.cardKey == original.cardKey
                ) {
                    state.copy(detail = DetailUiState.Series(detail.detail.copy(series = enriched)))
                } else {
                    state
                }
            }
        }
    }

    private fun metadataProvider(): TmdbMetadataProvider? {
        val key = settingsRepository.current.tmdbApiKey
        if (key.isBlank()) return null
        return TmdbMetadataProvider(key, okHttpClient, gson)
    }
}
