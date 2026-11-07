package de.kisium.tv.core.player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import de.kisium.tv.core.model.EpgNowNext
import de.kisium.tv.core.model.PlaybackRequest
import de.kisium.tv.core.ui.KisiumBadge
import de.kisium.tv.core.ui.KisiumButton
import de.kisium.tv.core.ui.KisiumPalette
import de.kisium.tv.core.ui.KisiumProgressBar
import okhttp3.OkHttpClient

@UnstableApi
@Composable
fun KisiumPlayerScreen(
    request: PlaybackRequest,
    nowNext: EpgNowNext?,
    okHttpClient: OkHttpClient,
    onBack: () -> Unit,
    onProgress: (positionMs: Long, durationMs: Long) -> Unit,
    onNextChannel: (() -> Unit)? = null,
    onPreviousChannel: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val player = remember(request.itemKey, request.url) { KisiumPlayerFactory.create(context) }
    var useAlternate by remember(request.itemKey) { mutableStateOf(false) }
    var errorMessage by remember(request.itemKey) { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var controlsVisible by remember(request.itemKey) { mutableStateOf(true) }
    var interactionTick by remember { mutableStateOf(0) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    val latestProgress = rememberUpdatedState(onProgress)
    val latestNext = rememberUpdatedState(onNextChannel)
    val latestPrevious = rememberUpdatedState(onPreviousChannel)

    LaunchedEffect(request.itemKey, request.url) {
        buffering = true
        errorMessage = null
        useAlternate = false
        KisiumPlayerFactory.prepare(
            player = player,
            request = request,
            client = okHttpClient,
            startPositionMs = request.startPositionMs,
        )
    }

    LaunchedEffect(useAlternate) {
        if (useAlternate) {
            val alt = request.alternateUrl ?: return@LaunchedEffect
            buffering = true
            errorMessage = null
            KisiumPlayerFactory.prepare(
                player = player,
                request = request,
                client = okHttpClient,
                urlOverride = alt,
                startPositionMs = 0L,
            )
        }
    }

    LaunchedEffect(request.itemKey, controlsVisible, interactionTick) {
        if (controlsVisible) {
            kotlinx.coroutines.delay(4_000)
            controlsVisible = false
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!useAlternate && request.alternateUrl != null) {
                    useAlternate = true
                } else {
                    errorMessage = error.localizedMessage ?: "Die Wiedergabe ist fehlgeschlagen."
                    buffering = false
                }
            }
        }
        player.addListener(listener)
        onDispose {
            runCatching {
                val duration = player.duration
                if (duration > 0L) latestProgress.value(player.currentPosition, duration)
            }
            player.removeListener(listener)
            player.release()
        }
    }

    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }

    DisposableEffect(lifecycle, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(player, request.isLive) {
        while (true) {
            kotlinx.coroutines.delay(15_000)
            nowMillis = System.currentTimeMillis()
            val duration = player.duration
            if (!request.isLive && duration > 0L) {
                latestProgress.value(player.currentPosition, duration)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    controlsVisible = true
                    interactionTick++
                }
                false
            },
    ) {
        AndroidView(
            factory = { ctx ->
                createKisiumPlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    controllerShowTimeoutMs = 3_500
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setKeepContentOnPlayerReset(true)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    isFocusable = true
                    isFocusableInTouchMode = true
                    isClickable = true
                    setOnClickListener { controlsVisible = true }
                    requestFocus()
                    setOnKeyListener { _, keyCode, event ->
                        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                            controlsVisible = true
                        }
                        if (event.action == android.view.KeyEvent.ACTION_UP) {
                            controlsVisible = true
                            when (keyCode) {
                                android.view.KeyEvent.KEYCODE_CHANNEL_UP,
                                android.view.KeyEvent.KEYCODE_MEDIA_NEXT,
                                -> {
                                    latestNext.value?.invoke()
                                    true
                                }

                                android.view.KeyEvent.KEYCODE_CHANNEL_DOWN,
                                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                                -> {
                                    latestPrevious.value?.invoke()
                                    true
                                }

                                else -> false
                            }
                        } else {
                            false
                        }
                    }
                }
            },
            update = { view -> view.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        // Top overlay: title, live state and EPG now/next.
        if (controlsVisible) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 42.dp, vertical = 26.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (request.isLive) {
                    KisiumBadge("LIVE", background = KisiumPalette.Danger)
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = request.title,
                    color = Color.White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val subtitle = request.subtitle
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            val current = nowNext?.now
            if (request.isLive && current != null) {
                Row(modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        text = "${current.timeRange()}  ${current.title}",
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    nowNext?.next?.let { next ->
                        Text(
                            text = "   Danach: ${next.title}",
                            color = Color.White.copy(alpha = 0.52f),
                            fontSize = 14.sp,
                        )
                    }
                }
                KisiumProgressBar(
                    progress = current.progressAt(nowMillis),
                    height = 3,
                    modifier = Modifier.padding(top = 6.dp).fillMaxWidth(0.45f),
                )
            }
        }
        }

        // Bottom gradient + control hints.
        if (controlsVisible) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.72f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.82f),
                    ),
                )
                .padding(bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                text = buildString {
                    append("Zurück")
                    if (request.isLive && onNextChannel != null) append("     CH+ / CH− Sender wechseln")
                    append("     Menü Steuerung")
                },
                color = Color.White.copy(alpha = 0.58f),
                fontSize = 12.sp,
            )
        }
        }

        if (buffering && errorMessage == null) {
            Text(
                text = "Stream wird geladen …",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.86f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Wiedergabe fehlgeschlagen",
                        color = Color.White,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = errorMessage ?: "",
                        color = KisiumPalette.TextMuted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(22.dp))
                    Row {
                        KisiumButton(
                            text = "Erneut versuchen",
                            onClick = {
                                errorMessage = null
                                useAlternate = false
                                KisiumPlayerFactory.prepare(
                                    player = player,
                                    request = request,
                                    client = okHttpClient,
                                    urlOverride = if (useAlternate) request.alternateUrl else null,
                                )
                            },
                            leading = "↻",
                        )
                        Spacer(Modifier.width(12.dp))
                        KisiumButton(
                            text = "Zurück",
                            onClick = onBack,
                            primary = false,
                        )
                    }
                }
            }
        }
    }
}

@UnstableApi
@Composable
fun HeroPreview(
    url: String,
    okHttpClient: OkHttpClient,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember(url) { KisiumPlayerFactory.create(context) }
    var failed by remember(url) { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(url) {
        val request = PlaybackRequest(
            itemKey = "hero-preview",
            title = "Preview",
            url = url,
            kind = de.kisium.tv.core.model.MediaKind.MOVIE,
            isLive = true,
        )
        KisiumPlayerFactory.prepare(player, request, okHttpClient)
        player.volume = 0f
        player.repeatMode = Player.REPEAT_MODE_ONE
    }
    if (!failed) {
        AndroidView(
            factory = { ctx ->
                createKisiumPlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setKeepContentOnPlayerReset(true)
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    isFocusable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
            },
            update = { it.player = player },
            modifier = modifier.fillMaxSize(),
        )
    }
}


@UnstableApi
private fun createKisiumPlayerView(context: android.content.Context): PlayerView {
    return LayoutInflater.from(context)
        .inflate(R.layout.view_kisium_player, null, false) as PlayerView
}
