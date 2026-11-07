package de.kisium.tv.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import de.kisium.tv.core.model.HomeFeed
import de.kisium.tv.core.model.HomeRail
import de.kisium.tv.core.model.MediaKind
import de.kisium.tv.core.model.PosterItem
import de.kisium.tv.core.model.RailKind
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeContent(
    feed: HomeFeed,
    autoplayPreview: Boolean,
    onItemClick: (PosterItem) -> Unit,
    onPlay: (PosterItem) -> Unit,
    onDetails: (PosterItem) -> Unit,
    onSettings: () -> Unit,
    onDisneyPlus: () -> Unit,
    onWaipu: () -> Unit,
    onLiveTv: () -> Unit,
    onCategories: () -> Unit,
    onFavorites: () -> Unit,
    preview: @Composable BoxScope.(PosterItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val heroVisible by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 140
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KisiumPalette.Ink),
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 54.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            feed.hero?.let { hero ->
                item(key = "hero-${hero.cardKey}") {
                    HeroBanner(
                        item = hero,
                        autoplayPreview = autoplayPreview && heroVisible,
                        onPlay = { onPlay(hero) },
                        onDetails = { onDetails(hero) },
                        preview = preview,
                    )
                }
            }

            item(key = "services") {
                ServiceRail(
                    isDemo = feed.isDemo,
                    onDisneyPlus = onDisneyPlus,
                    onWaipu = onWaipu,
                    onLiveTv = onLiveTv,
                    onSettings = onSettings,
                )
            }

            items(
                items = feed.rails,
                key = { "${it.kind.name}-${it.title}" },
            ) { rail ->
                ContentRail(
                    rail = rail,
                    onItemClick = onItemClick,
                )
            }

            item(key = "footer") {
                Spacer(Modifier.height(10.dp))
                FocusHints(Modifier.padding(top = 18.dp))
                Text(
                    text = "Kisium TV · ${feed.sourceLabel} · privat, nur für den Eigenbedarf",
                    color = KisiumPalette.TextMuted.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        HomeTopBar(
            sourceLabel = feed.sourceLabel,
            isDemo = feed.isDemo,
            onSettings = onSettings,
            onLiveTv = onLiveTv,
            onCategories = onCategories,
            onFavorites = onFavorites,
        )
    }
}

@Composable
fun HeroBanner(
    item: PosterItem,
    autoplayPreview: Boolean,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    preview: @Composable BoxScope.(PosterItem) -> Unit,
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    var showPreview by remember(item.cardKey) { mutableStateOf(false) }
    val canPreview = autoplayPreview && !item.previewUrl.isNullOrBlank()

    LaunchedEffect(item.cardKey) {
        delay(280)
        runCatching { focusRequester.requestFocus() }
    }
    LaunchedEffect(item.cardKey, canPreview) {
        showPreview = false
        if (canPreview) {
            delay(3600)
            showPreview = true
        }
    }

    val image = remember(item.cardKey) {
        ImageRequest.Builder(context)
            .data(item.backdropUrl ?: item.imageUrl)
            .crossfade(260)
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(480.dp),
    ) {
        if (item.isLogo) {
            BrandBackdrop(
                title = item.title,
                logoUrl = item.imageUrl,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        AnimatedVisibility(
            visible = showPreview,
            enter = fadeIn(tween(650)),
            exit = fadeOut(tween(250)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                preview(item)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to KisiumPalette.Ink.copy(alpha = 0.97f),
                        0.32f to KisiumPalette.Ink.copy(alpha = 0.74f),
                        0.68f to Color.Transparent,
                        1f to Color.Transparent,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.42f),
                        0.32f to Color.Transparent,
                        0.72f to Color.Transparent,
                        1f to KisiumPalette.Ink,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.68f)
                .padding(start = 58.dp, bottom = 38.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    item.kind == MediaKind.LIVE -> KisiumBadge("LIVE", background = KisiumPalette.Danger)
                    item.isDemo -> KisiumBadge("KISIUM ORIGINAL", background = KisiumPalette.Accent)
                    item.kind == MediaKind.SERIES -> KisiumBadge("SERIE", background = KisiumPalette.Accent)
                    else -> KisiumBadge("FILM", background = KisiumPalette.Accent)
                }
                if (item.isDemo) {
                    KisiumBadge(
                        "DEMO",
                        background = Color.White.copy(alpha = 0.14f),
                        contentColor = Color.White.copy(alpha = 0.85f),
                    )
                }
                if (showPreview) {
                    KisiumBadge("VORSCHAU", background = KisiumPalette.Teal, contentColor = KisiumPalette.Ink)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 58.sp,
                lineHeight = 60.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1.4).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            val year = item.year
            val subtitle = item.subtitle
            val meta = buildList {
                subtitle?.let { add(it) }
                if (year != null && subtitle?.contains(year) != true) add(year)
                item.duration?.let { add(it) }
                if (subtitle?.contains("★") != true) {
                    item.rating?.let { add("★ %.1f".format(Locale.GERMANY, it)) }
                }
            }.joinToString("  ·  ")
            Text(
                text = meta,
                color = KisiumPalette.TextMuted,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            val plot = item.plot
            if (!plot.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = plot,
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(640.dp),
                )
            }
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KisiumButton(
                    text = if (item.kind == MediaKind.LIVE) "Jetzt ansehen" else "Abspielen",
                    onClick = onPlay,
                    leading = "▶",
                    modifier = Modifier.focusRequester(focusRequester),
                )
                if (item.kind != MediaKind.LIVE) {
                    KisiumButton(
                        text = "Mehr Infos",
                        onClick = onDetails,
                        primary = false,
                        leading = "ⓘ",
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceRail(
    isDemo: Boolean,
    onDisneyPlus: () -> Unit,
    onWaipu: () -> Unit,
    onLiveTv: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)) {
        SectionTitle(
            title = "Dienste",
            subtitle = if (isDemo) "Demo-Katalog aktiv · Xtream in den Einstellungen verbinden" else null,
            modifier = Modifier.padding(horizontal = 56.dp),
        )
        Spacer(Modifier.height(14.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item(key = "disney") {
                DisneyPlusCard(onClick = onDisneyPlus)
            }
            item(key = "waipu") {
                WaipuCard(onClick = onWaipu)
            }
            item(key = "live") {
                ServiceCard(
                    title = "Live-TV",
                    subtitle = "Senderliste, EPG und Zapping",
                    badge = "LIVE",
                    onClick = onLiveTv,
                )
            }
            item(key = "settings") {
                ServiceCard(
                    title = "Quellen",
                    subtitle = "Xtream, TMDB und Wiedergabe",
                    badge = "SETUP",
                    onClick = onSettings,
                )
            }
        }
    }
}

@Composable
fun ContentRail(
    rail: HomeRail,
    onItemClick: (PosterItem) -> Unit,
) {
    if (rail.items.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle(
                title = rail.title,
                subtitle = if (rail.kind == RailKind.CONTINUE) "Auf diesem Gerät fortgesetzt" else null,
            )
            Spacer(Modifier.width(12.dp))
            if (rail.kind == RailKind.LIVE) {
                KisiumBadge(
                    "JETZT",
                    background = KisiumPalette.Danger.copy(alpha = 0.9f),
                )
            }
        }
        Spacer(Modifier.height(13.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 56.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(
                items = rail.items,
                key = { it.cardKey },
            ) { item ->
                MediaCard(
                    item = item,
                    onClick = { onItemClick(item) },
                )
            }
        }
    }
}

@Composable
fun HomeTopBar(
    sourceLabel: String,
    isDemo: Boolean,
    onSettings: () -> Unit,
    onLiveTv: () -> Unit,
    onCategories: () -> Unit,
    onFavorites: () -> Unit,
) {
    var clock by remember { mutableStateOf(currentClock()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            clock = currentClock()
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(KisiumPalette.Ink.copy(alpha = 0.96f))
            .padding(start = 56.dp, end = 56.dp, top = 24.dp, bottom = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KisiumLogo(showPrivateLabel = false)
        Spacer(Modifier.width(18.dp))
        Text(
            text = sourceLabel,
            color = KisiumPalette.TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        if (isDemo) {
            Spacer(Modifier.width(8.dp))
            KisiumBadge(
                "DEMO",
                background = KisiumPalette.Teal.copy(alpha = 0.18f),
                contentColor = KisiumPalette.Teal,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = clock,
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(end = 20.dp),
        )
        KisiumTextAction("Kategorien", onCategories)
        Spacer(Modifier.width(8.dp))
        KisiumTextAction("Favoriten", onFavorites)
        Spacer(Modifier.width(8.dp))
        KisiumTextAction("Live-TV", onLiveTv)
        Spacer(Modifier.width(8.dp))
        KisiumTextAction("Einstellungen", onSettings)
    }
}

private fun currentClock(): String =
    SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date())
