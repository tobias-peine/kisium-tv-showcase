package de.kisium.tv.core.ui

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import de.kisium.tv.core.model.MediaKind
import de.kisium.tv.core.model.PosterItem

fun brandPalette(title: String): List<Color> {
    val palettes = listOf(
        listOf(Color(0xFF132A3A), Color(0xFF2B6570)),
        listOf(Color(0xFF2A1420), Color(0xFF7A2B24)),
        listOf(Color(0xFF17152E), Color(0xFF4B3C86)),
        listOf(Color(0xFF14251B), Color(0xFF4A7250)),
        listOf(Color(0xFF28200F), Color(0xFF7A5C23)),
    )
    return palettes[(title.hashCode() and Int.MAX_VALUE) % palettes.size]
}

@Composable
fun Artwork(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    logo: Boolean = false,
) {
    val context = LocalContext.current
    val fallback = remember(title) { brandPalette(title) }
    Box(
        modifier = modifier
            .background(
                Brush.linearGradient(
                    listOf(fallback[0], fallback[1], KisiumPalette.Ink),
                ),
            ),
    ) {
        if (!url.isNullOrBlank()) {
            val request = remember(url) {
                ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(180)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = if (logo) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (logo) 18.dp else 0.dp),
            )
        }
    }
}

@Composable
fun BrandBackdrop(
    title: String,
    logoUrl: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val palette = remember(title) { brandPalette(title) }
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                listOf(palette[0], palette[1], KisiumPalette.Ink),
            ),
        ),
    ) {
        Text(
            text = title.uppercase(),
            color = Color.White.copy(alpha = 0.07f),
            fontSize = 82.sp,
            lineHeight = 86.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .fillMaxWidth(0.62f)
                .padding(end = 44.dp, bottom = 34.dp),
        )
        if (!logoUrl.isNullOrBlank()) {
            val request = remember(logoUrl) {
                ImageRequest.Builder(context)
                    .data(logoUrl)
                    .crossfade(180)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(230.dp)
                    .height(118.dp)
                    .padding(end = 66.dp),
            )
        }
    }
}

@Composable
fun MediaCard(
    item: PosterItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val live = item.kind == MediaKind.LIVE
    val cardWidth = if (live) 300.dp else 172.dp
    val cardHeight = if (live) 169.dp else 258.dp
    val shape = RoundedCornerShape(8.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.07f else 1f,
        animationSpec = tween(140),
        label = "card-scale",
    )

    Box(
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (focused) 1f else 0.94f
            }
            .clip(shape)
            .background(KisiumPalette.Panel)
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) Color.White else Color.White.copy(alpha = 0.10f),
                shape = shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = "${item.title} öffnen",
                onClick = onClick,
            ),
    ) {
        Artwork(
            url = item.imageUrl ?: item.backdropUrl,
            title = item.title,
            modifier = Modifier.fillMaxSize(),
            logo = item.isLogo,
        )

        // Permanent scrim for legible live labels / resume bars.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.82f),
                    ),
                ),
        )

        if (item.kind == MediaKind.LIVE) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KisiumBadge("LIVE", background = KisiumPalette.Danger)
                item.subtitle?.let {
                    Text(
                        text = it,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        } else if (item.rating != null) {
            Text(
                text = "★ %.1f".format(java.util.Locale.GERMANY, item.rating),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 11.dp, end = 11.dp, bottom = if (item.resumeProgress != null) 14.dp else 10.dp),
        ) {
            if (live || focused) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = if (live) 15.sp else 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = if (live) 19.sp else 17.sp,
                )
                val subtitle = item.subtitle
                if (!live && focused && !subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = KisiumPalette.TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        item.resumeProgress?.let { progress ->
            KisiumProgressBar(
                progress = progress,
                height = 4,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
fun DisneyPlusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.045f else 1f,
        animationSpec = tween(140),
        label = "disney-scale",
    )
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .width(300.dp)
            .height(169.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF071B4D), KisiumPalette.DisneyBlue, Color(0xFF07132E)),
                ),
            )
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) Color.White else Color.White.copy(alpha = 0.16f),
                shape,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(18.dp),
    ) {
        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Text(
                text = "Disney+",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "OFFIZIELLE APP ÖFFNEN",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(38.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(Color.White.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("D+", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
        }
    }
}

@Composable
fun ServiceCard(
    title: String,
    subtitle: String,
    badge: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        if (focused) 1.045f else 1f,
        tween(140),
        label = "service-scale",
    )
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .width(300.dp)
            .height(169.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(KisiumPalette.PanelRaised, Color(0xFF30201C), KisiumPalette.Panel),
                ),
            )
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) Color.White else Color.White.copy(alpha = 0.14f),
                shape,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(18.dp),
    ) {
        KisiumBadge(badge, background = KisiumPalette.Accent.copy(alpha = 0.18f), contentColor = KisiumPalette.AccentSoft)
        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                color = KisiumPalette.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}


@Composable
fun WaipuCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.045f else 1f,
        animationSpec = tween(140),
        label = "waipu-scale",
    )
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .width(300.dp)
            .height(169.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF042B45), Color(0xFF0B6673), Color(0xFF071B2B)),
                ),
            )
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) Color.White else Color.White.copy(alpha = 0.16f),
                shape,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(18.dp),
    ) {
        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Text(
                text = "waipu.tv",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.8).sp,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "LIVE-TV · EPG · AUFNAHMEN",
                color = Color(0xFF76F5DF),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp,
            )
        }
        KisiumBadge(
            "WAIPU",
            background = Color.White.copy(alpha = 0.16f),
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}
