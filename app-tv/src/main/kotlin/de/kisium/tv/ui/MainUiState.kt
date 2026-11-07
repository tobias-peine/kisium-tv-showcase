package de.kisium.tv.ui

import de.kisium.tv.core.model.AppSettings
import de.kisium.tv.core.model.Category
import de.kisium.tv.core.model.EpgNowNext
import de.kisium.tv.core.model.Episode
import de.kisium.tv.core.model.HomeFeed
import de.kisium.tv.core.model.PlaybackRequest
import de.kisium.tv.core.model.PosterItem
import de.kisium.tv.core.model.SeriesDetail

sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data object Live : Screen
    data object Categories : Screen
    data object Favorites : Screen
    data class Detail(val item: PosterItem) : Screen
}

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Ready(val feed: HomeFeed) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

sealed interface DetailUiState {
    data object Idle : DetailUiState
    data object Loading : DetailUiState
    data class Movie(val item: PosterItem, val nowNext: EpgNowNext? = null) : DetailUiState
    data class Series(val detail: SeriesDetail) : DetailUiState
    data class Error(val message: String) : DetailUiState
}

data class LiveUiState(
    val loading: Boolean = true,
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String = "",
    val channels: List<PosterItem> = emptyList(),
    val error: String? = null,
)

data class CatalogUiState(
    val loading: Boolean = true,
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String = "",
    val items: List<PosterItem> = emptyList(),
    val error: String? = null,
    val favoritesMode: Boolean = false,
)

data class PlayerSession(
    val item: PosterItem,
    val request: PlaybackRequest,
    val nowNext: EpgNowNext?,
    val liveQueue: List<PosterItem>,
    val queueIndex: Int,
)

data class MainUiState(
    val settings: AppSettings = AppSettings(),
    val secureStorage: Boolean = true,
    val screen: Screen = Screen.Home,
    val home: HomeUiState = HomeUiState.Loading,
    val detail: DetailUiState = DetailUiState.Idle,
    val live: LiveUiState = LiveUiState(),
    val catalog: CatalogUiState = CatalogUiState(),
    val favoriteIds: Set<String> = emptySet(),
    val player: PlayerSession? = null,
    val playerLoading: Boolean = false,
    val playerError: String? = null,
    val settingsBusy: Boolean = false,
    val settingsMessage: String? = null,
    val settingsMessageIsError: Boolean = false,
) {
    val isPlaying: Boolean get() = player != null || playerLoading
}

data class LastPlayback(
    val item: PosterItem,
    val episode: Episode?,
)
