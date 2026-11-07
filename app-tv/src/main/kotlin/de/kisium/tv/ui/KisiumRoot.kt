@file:Suppress("UnsafeOptInUsageError")

package de.kisium.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kisium.tv.DisneyPlusLauncher
import de.kisium.tv.WaipuLauncher
import de.kisium.tv.core.player.HeroPreview
import de.kisium.tv.core.player.KisiumPlayerScreen
import de.kisium.tv.core.ui.HomeContent
import de.kisium.tv.core.ui.KisiumButton
import de.kisium.tv.core.ui.KisiumError
import de.kisium.tv.core.ui.KisiumLoading
import de.kisium.tv.core.ui.KisiumPalette
import de.kisium.tv.core.ui.KisiumTheme
import de.kisium.tv.feature.settings.SettingsScreen
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

@Composable
fun KisiumRoot(
    okHttpClient: OkHttpClient,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showSplash by rememberSaveable { mutableStateOf(true) }
    var showDisneyHelp by rememberSaveable { mutableStateOf(false) }
    var showWaipuHelp by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(650)
        showSplash = false
    }

    BackHandler(
        enabled = state.screen != Screen.Home ||
            state.player != null ||
            state.playerLoading ||
            state.playerError != null,
    ) {
        viewModel.onBack()
    }

    KisiumTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(KisiumPalette.Ink),
        ) {
            when {
                state.playerError != null -> PlayerErrorScreen(
                    message = state.playerError ?: "",
                    onRetry = viewModel::retryPlayback,
                    onBack = viewModel::dismissPlayer,
                )

                state.player != null -> {
                    val session = state.player!!
                    key(session.request.itemKey, session.request.url) {
                        KisiumPlayerScreen(
                            request = session.request,
                            nowNext = session.nowNext,
                            okHttpClient = okHttpClient,
                            onBack = viewModel::dismissPlayer,
                            onProgress = { position, duration ->
                                viewModel.saveProgress(session.item, position, duration)
                            },
                            onNextChannel = if (session.liveQueue.size > 1) viewModel::nextChannel else null,
                            onPreviousChannel = if (session.liveQueue.size > 1) viewModel::previousChannel else null,
                        )
                    }
                }

                state.playerLoading -> KisiumLoading("Stream wird vorbereitet …")

                else -> when (val screen = state.screen) {
                    Screen.Home -> HomeScreen(
                        state = state,
                        okHttpClient = okHttpClient,
                        viewModel = viewModel,
                        onDisneyPlus = {
                            if (!DisneyPlusLauncher.open(context)) {
                                showDisneyHelp = true
                            }
                        },
                        onWaipu = {
                            if (!WaipuLauncher.openLiveTv(context)) {
                                showWaipuHelp = true
                            }
                        },
                    )

                    Screen.Settings -> SettingsScreen(
                        settings = state.settings,
                        secureStorage = state.secureStorage,
                        busy = state.settingsBusy,
                        message = state.settingsMessage,
                        messageIsError = state.settingsMessageIsError,
                        onSave = viewModel::saveSettings,
                        onTest = viewModel::testSettings,
                        onClearCache = viewModel::clearCache,
                        onBack = viewModel::onBack,
                    )

                    Screen.Categories -> CatalogScreen(
                        title = "Kategorien",
                        subtitle = "Genres und Themen aus dem Netzkino-Katalog",
                        state = state.catalog.copy(favoritesMode = false),
                        onSelectCategory = viewModel::selectCatalogCategory,
                        onItemClick = viewModel::onItemClick,
                        onBack = viewModel::onBack,
                        onRetry = viewModel::openCategories,
                    )

                    Screen.Favorites -> CatalogScreen(
                        title = "Favoriten",
                        subtitle = "Deine gespeicherten Filme",
                        state = state.catalog.copy(favoritesMode = true),
                        onSelectCategory = {},
                        onItemClick = viewModel::onItemClick,
                        onBack = viewModel::onBack,
                        onRetry = viewModel::openFavorites,
                    )

                    Screen.Live -> LiveScreen(
                        live = state.live,
                        onSelectCategory = viewModel::selectLiveCategory,
                        onPlay = viewModel::playItem,
                        onBack = viewModel::onBack,
                        onRetry = viewModel::openLive,
                    )

                    is Screen.Detail -> DetailScreen(
                        item = screen.item,
                        state = state.detail,
                        onBack = viewModel::onBack,
                        onPlayMovie = { viewModel.playItem(it) },
                        onPlayEpisode = { series, episode -> viewModel.playItem(series, episode) },
                        onRetry = { viewModel.openDetail(screen.item) },
                        isFavorite = viewModel.isFavorite(screen.item),
                        onToggleFavorite = { viewModel.toggleFavorite(screen.item) },
                    )
                }
            }

            AnimatedVisibility(
                visible = showSplash,
                enter = fadeIn(tween(250)),
                exit = fadeOut(tween(350)),
            ) {
                KisiumSplashScreen()
            }

            if (showWaipuHelp) {
                WaipuHelpDialog(
                    onDismiss = { showWaipuHelp = false },
                    onPlayStore = {
                        if (!WaipuLauncher.openPlayStore(context)) {
                            WaipuLauncher.openAmazonAppStore(context)
                        }
                        showWaipuHelp = false
                    },
                    onAmazon = {
                        WaipuLauncher.openAmazonAppStore(context)
                        showWaipuHelp = false
                    },
                )
            }

            if (showDisneyHelp) {
                DisneyHelpDialog(
                    onDismiss = { showDisneyHelp = false },
                    onPlayStore = {
                        if (!DisneyPlusLauncher.openPlayStore(context)) {
                            DisneyPlusLauncher.openAmazonAppStore(context)
                        }
                        showDisneyHelp = false
                    },
                    onAmazon = {
                        DisneyPlusLauncher.openAmazonAppStore(context)
                        showDisneyHelp = false
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: MainUiState,
    okHttpClient: OkHttpClient,
    viewModel: MainViewModel,
    onDisneyPlus: () -> Unit,
    onWaipu: () -> Unit,
) {
    when (val home = state.home) {
        HomeUiState.Loading -> KisiumLoading()
        is HomeUiState.Error -> KisiumError(
            message = home.message,
            onRetry = viewModel::loadHome,
            onSettings = viewModel::openSettings,
        )

        is HomeUiState.Ready -> HomeContent(
            feed = home.feed,
            autoplayPreview = state.settings.autoplayPreview,
            onItemClick = viewModel::onItemClick,
            onPlay = viewModel::playHero,
            onDetails = viewModel::openDetail,
            onSettings = viewModel::openSettings,
            onDisneyPlus = onDisneyPlus,
            onWaipu = onWaipu,
            onLiveTv = viewModel::openLive,
            onCategories = viewModel::openCategories,
            onFavorites = viewModel::openFavorites,
            preview = { item ->
                item.previewUrl?.let { url ->
                    HeroPreview(url = url, okHttpClient = okHttpClient)
                }
            },
        )
    }
}

@Composable
private fun PlayerErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(KisiumPalette.Ink),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Wiedergabe nicht möglich",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = message,
                color = KisiumPalette.TextMuted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp, bottom = 24.dp).widthIn(max = 620.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KisiumButton("Erneut versuchen", onRetry, leading = "↻")
                KisiumButton("Zurück", onBack, primary = false)
            }
        }
    }
}

@Composable
private fun DisneyHelpDialog(
    onDismiss: () -> Unit,
    onPlayStore: () -> Unit,
    onAmazon: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focus.requestFocus() }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(640.dp)
                    .background(KisiumPalette.Panel, RoundedCornerShape(14.dp))
                    .padding(32.dp),
            ) {
                Text(
                    text = "Disney+",
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "Die offizielle Disney+-App ist auf diesem Gerät nicht installiert. " +
                        "Kisium TV kann DRM-geschützte Disney+-Inhalte technisch nicht in die " +
                        "eigene Oberfläche einbetten. Öffne die App direkt – dein Abo und deine " +
                        "Wiedergabeliste bleiben dort, wo sie sind.",
                    color = KisiumPalette.TextMuted,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(top = 10.dp, bottom = 22.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KisiumButton(
                        text = "Play Store",
                        onClick = onPlayStore,
                        modifier = Modifier.focusRequester(focus),
                    )
                    KisiumButton(
                        text = "Amazon Appstore",
                        onClick = onAmazon,
                        primary = false,
                    )
                    KisiumButton(
                        text = "Schließen",
                        onClick = onDismiss,
                        primary = false,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Auf Fire TV ist der Amazon Appstore der passende Weg.",
                    color = KisiumPalette.TextMuted.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                )
            }
        }
    }
}


@Composable
private fun WaipuHelpDialog(
    onDismiss: () -> Unit,
    onPlayStore: () -> Unit,
    onAmazon: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focus.requestFocus() }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(620.dp)
                    .background(KisiumPalette.Panel, RoundedCornerShape(14.dp))
                    .padding(32.dp),
            ) {
                Text(
                    text = "waipu.tv",
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "Die offizielle waipu.tv-App ist auf diesem Gerät nicht installiert. " +
                        "Kisium TV kann waipu.tv-Inhalte wegen Account- und DRM-Bindung nicht in " +
                        "die eigene Oberfläche einbetten. Auf dem waipu.tv-Stick ist die App " +
                        "vorinstalliert; auf anderen Geräten installierst du sie über den " +
                        "passenden Appstore.",
                    color = KisiumPalette.TextMuted,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(top = 10.dp, bottom = 22.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KisiumButton(
                        text = "Play Store",
                        onClick = onPlayStore,
                        modifier = Modifier.focusRequester(focus),
                    )
                    KisiumButton(
                        text = "Amazon Appstore",
                        onClick = onAmazon,
                        primary = false,
                    )
                    KisiumButton(
                        text = "Schließen",
                        onClick = onDismiss,
                        primary = false,
                    )
                }
            }
        }
    }
}
