package de.kisium.tv.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.kisium.tv.core.model.Episode
import de.kisium.tv.core.model.PosterItem
import de.kisium.tv.core.ui.Artwork
import de.kisium.tv.core.ui.BrandBackdrop
import de.kisium.tv.core.ui.FocusHints
import de.kisium.tv.core.ui.KisiumBadge
import de.kisium.tv.core.ui.KisiumButton
import de.kisium.tv.core.ui.KisiumChip
import de.kisium.tv.core.ui.KisiumLoading
import de.kisium.tv.core.ui.KisiumPalette
import de.kisium.tv.core.ui.KisiumTextAction
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun DetailScreen(
    item: PosterItem,
    state: DetailUiState,
    onBack: () -> Unit,
    onPlayMovie: (PosterItem) -> Unit,
    onPlayEpisode: (PosterItem, Episode) -> Unit,
    onRetry: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when (state) {
        DetailUiState.Loading, DetailUiState.Idle -> KisiumLoading("Details werden geladen …", modifier)
        is DetailUiState.Error -> DetailError(state.message, onRetry, onBack, modifier)
        is DetailUiState.Movie -> MovieDetail(
            item = state.item,
            onBack = onBack,
            onPlay = { onPlayMovie(state.item) },
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            modifier = modifier,
        )

        is DetailUiState.Series -> SeriesDetailView(
            detail = state.detail,
            onBack = onBack,
            onPlayEpisode = onPlayEpisode,
            modifier = modifier,
        )
    }
}

@Composable
private fun MovieDetail(
    item: PosterItem,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(item.cardKey) {
        delay(260)
        runCatching { playFocus.requestFocus() }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().background(KisiumPalette.Ink),
        contentPadding = PaddingValues(bottom = 54.dp),
    ) {
        item {
            DetailHeader(
                item = item,
                eyebrow = "FILM",
                onBack = onBack,
            ) {
                KisiumButton(
                    text = "Film abspielen",
                    onClick = onPlay,
                    leading = "▶",
                    modifier = Modifier.focusRequester(playFocus),
                )
                Spacer(Modifier.width(12.dp))
                KisiumButton(
                    text = if (isFavorite) "★ Favorit" else "☆ Favorit",
                    onClick = onToggleFavorite,
                    primary = false,
                )
            }
        }
        item {
            Column(
                modifier = Modifier.padding(horizontal = 58.dp, vertical = 26.dp).widthIn(max = 980.dp),
            ) {
                Text(
                    text = "Über diesen Film",
                    color = Color.White,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = item.plot?.takeIf { it.isNotBlank() }
                        ?: "Für diesen Titel liefert die Quelle noch keine Beschreibung.",
                    color = KisiumPalette.TextMuted,
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        item { FocusHints(Modifier.padding(top = 26.dp)) }
    }
}

@Composable
private fun SeriesDetailView(
    detail: de.kisium.tv.core.model.SeriesDetail,
    onBack: () -> Unit,
    onPlayEpisode: (PosterItem, Episode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val series = detail.series
    var selectedSeason by remember(detail) {
        mutableStateOf(detail.seasons.firstOrNull()?.season ?: 1)
    }
    val playFocus = remember { FocusRequester() }
    val episodes = detail.seasons.firstOrNull { it.season == selectedSeason }?.episodes.orEmpty()
    val firstEpisode = detail.seasons.firstOrNull()?.episodes?.firstOrNull()

    LaunchedEffect(detail) {
        selectedSeason = detail.seasons.firstOrNull()?.season ?: 1
        delay(260)
        runCatching { playFocus.requestFocus() }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(KisiumPalette.Ink),
        contentPadding = PaddingValues(bottom = 54.dp),
    ) {
        item {
            DetailHeader(
                item = series,
                eyebrow = "SERIE",
                onBack = onBack,
            ) {
                KisiumButton(
                    text = "Erste Folge abspielen",
                    onClick = { firstEpisode?.let { onPlayEpisode(series, it) } },
                    leading = "▶",
                    enabled = firstEpisode != null,
                    modifier = Modifier.focusRequester(playFocus),
                )
            }
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 58.dp, vertical = 25.dp)) {
                Text(
                    text = series.plot?.takeIf { it.isNotBlank() }
                        ?: "Für diese Serie liefert die Quelle noch keine Beschreibung.",
                    color = KisiumPalette.TextMuted,
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                    modifier = Modifier.widthIn(max = 980.dp),
                )
            }
        }
        item {
            Text(
                text = "Folgen",
                color = Color.White,
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 58.dp, end = 58.dp, top = 8.dp),
            )
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 58.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                items(detail.seasons, key = { it.season }) { season ->
                    KisiumChip(
                        text = "Staffel ${season.season}",
                        selected = season.season == selectedSeason,
                        onClick = { selectedSeason = season.season },
                    )
                }
            }
        }
        items(episodes, key = { it.id }) { episode ->
            EpisodeRow(
                episode = episode,
                onClick = { onPlayEpisode(series, episode) },
            )
        }
        if (episodes.isEmpty()) {
            item {
                Text(
                    text = "Für diese Staffel sind keine Folgen hinterlegt.",
                    color = KisiumPalette.TextMuted,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(58.dp),
                )
            }
        }
        item { FocusHints(Modifier.padding(top = 26.dp)) }
    }
}

@Composable
private fun DetailHeader(
    item: PosterItem,
    eyebrow: String,
    onBack: () -> Unit,
    buttons: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(470.dp),
    ) {
        if (item.isLogo) {
            BrandBackdrop(
                title = item.title,
                logoUrl = item.imageUrl,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Artwork(
                url = item.backdropUrl ?: item.imageUrl,
                title = item.title,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.35f),
                        0.42f to Color.Transparent,
                        0.82f to KisiumPalette.Ink.copy(alpha = 0.92f),
                        1f to KisiumPalette.Ink,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to KisiumPalette.Ink.copy(alpha = 0.75f),
                        0.55f to Color.Transparent,
                    ),
                ),
        )
        KisiumTextAction(
            text = "← Zurück",
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 48.dp, top = 24.dp),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.8f)
                .padding(start = 58.dp, bottom = 34.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KisiumBadge(eyebrow, background = KisiumPalette.Accent)
                item.rating?.let {
                    KisiumBadge(
                        "★ %.1f".format(Locale.GERMANY, it),
                        background = Color.White.copy(alpha = 0.14f),
                        contentColor = Color.White,
                    )
                }
                item.year?.let {
                    KisiumBadge(
                        it,
                        background = Color.White.copy(alpha = 0.10f),
                        contentColor = KisiumPalette.TextMuted,
                    )
                }
            }
            Spacer(Modifier.height(11.dp))
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 52.sp,
                lineHeight = 55.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1.2).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.subtitle?.let {
                Text(
                    text = it,
                    color = KisiumPalette.TextMuted,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                buttons()
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.015f else 1f, tween(120), label = "episode-scale")
    val background by animateColorAsState(
        if (focused) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.035f),
        tween(120),
        label = "episode-bg",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 58.dp, vertical = 6.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(background)
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.07f),
                shape,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(190.dp)
                .height(107.dp)
                .clip(RoundedCornerShape(5.dp)),
        ) {
            Artwork(
                url = episode.stillUrl,
                title = episode.title,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(42.dp)
                    .height(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(Color.Black.copy(alpha = 0.52f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("▶", color = Color.White, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "S${episode.season} · F${episode.number}" +
                    (episode.duration?.let { "  ·  $it" } ?: ""),
                color = KisiumPalette.AccentSoft,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
            )
            Text(
                text = episode.title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = episode.plot ?: "Keine Beschreibung hinterlegt.",
                color = KisiumPalette.TextMuted,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = "▶",
            color = if (focused) KisiumPalette.Accent else KisiumPalette.TextMuted,
            fontSize = 20.sp,
            modifier = Modifier.padding(end = 10.dp),
        )
    }
}

@Composable
private fun DetailError(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().background(KisiumPalette.Ink),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Details nicht verfügbar",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = message,
                color = KisiumPalette.TextMuted,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 22.dp).widthIn(max = 620.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KisiumButton("Erneut versuchen", onRetry, leading = "↻")
                KisiumButton("Zurück", onBack, primary = false)
            }
        }
    }
}
