package de.kisium.tv.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun KisiumLogo(
    modifier: Modifier = Modifier,
    fontSize: Int = 26,
    showPrivateLabel: Boolean = false,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "KISIUM",
                color = Color.White,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                lineHeight = (fontSize + 2).sp,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = "TV",
                color = KisiumPalette.Accent,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                lineHeight = (fontSize + 2).sp,
            )
        }
        if (showPrivateLabel) {
            Text(
                text = "PRIVATE STREAMING",
                color = KisiumPalette.Teal,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.2.sp,
            )
        }
    }
}

@Composable
fun KisiumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    leading: String? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.045f else 1f,
        animationSpec = tween(130),
        label = "button-scale",
    )
    val background by animateColorAsState(
        targetValue = when {
            !enabled -> KisiumPalette.PanelRaised
            primary -> if (focused) Color.White else KisiumPalette.Accent
            else -> if (focused) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.28f)
        },
        animationSpec = tween(130),
        label = "button-background",
    )
    val contentColor = when {
        !enabled -> KisiumPalette.TextMuted
        primary -> if (focused) KisiumPalette.Ink else Color.White
        else -> Color.White
    }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(background)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else Color.White.copy(alpha = 0.22f),
                shape = shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Text(
                text = leading,
                color = contentColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = text,
            color = contentColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp,
        )
    }
}

@Composable
fun KisiumTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val background by animateColorAsState(
        if (focused) Color.White.copy(alpha = 0.16f) else Color.Transparent,
        tween(120),
        label = "text-action",
    )
    Text(
        text = text,
        color = if (focused || active) Color.White else KisiumPalette.TextMuted,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(
                if (focused) 1.dp else 0.dp,
                if (focused) Color.White.copy(alpha = 0.55f) else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
fun KisiumChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(50)
    val background = when {
        focused -> Color.White
        selected -> KisiumPalette.Accent.copy(alpha = 0.92f)
        else -> KisiumPalette.PanelRaised
    }
    Text(
        text = text,
        color = if (focused) KisiumPalette.Ink else Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) Color.White else Color.White.copy(alpha = 0.14f),
                shape,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    )
}

@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(modifier) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.2).sp,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = KisiumPalette.TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
fun KisiumBadge(
    text: String,
    background: Color = KisiumPalette.Accent,
    contentColor: Color = Color.White,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = contentColor,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
fun KisiumProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Int = 4,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .background(Color.White.copy(alpha = 0.25f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(KisiumPalette.Accent),
        )
    }
}

@Composable
fun KisiumLoading(
    message: String = "Katalog wird geladen …",
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().background(KisiumPalette.Ink),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            KisiumLogo(showPrivateLabel = true)
            Spacer(Modifier.height(28.dp))
            CircularProgressIndicator(
                color = KisiumPalette.Accent,
                strokeWidth = 3.dp,
                modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = message,
                color = KisiumPalette.TextMuted,
                fontSize = 15.sp,
            )
        }
    }
}

@Composable
fun KisiumError(
    message: String,
    onRetry: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().background(
            Brush.radialGradient(
                listOf(KisiumPalette.Panel, KisiumPalette.Ink),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(620.dp),
        ) {
            KisiumLogo(showPrivateLabel = true)
            Spacer(Modifier.height(36.dp))
            Text(
                text = "Keine Verbindung zum Katalog",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                color = KisiumPalette.TextMuted,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
            )
            Spacer(Modifier.height(30.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                KisiumButton("Erneut versuchen", onRetry, leading = "↻")
                KisiumButton("Einstellungen", onSettings, primary = false)
            }
            Spacer(Modifier.height(26.dp))
            Text(
                text = "OK auswählen · ← → navigieren · Zurück",
                color = KisiumPalette.TextMuted.copy(alpha = 0.7f),
                fontSize = 12.sp,
                letterSpacing = 0.4.sp,
            )
        }
    }
}

@Composable
fun FocusHints(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "OK Auswählen     ← → Navigieren     ▲ ▼ Bereiche     Zurück",
            color = KisiumPalette.TextMuted.copy(alpha = 0.72f),
            fontSize = 12.sp,
            letterSpacing = 0.3.sp,
        )
    }
}
