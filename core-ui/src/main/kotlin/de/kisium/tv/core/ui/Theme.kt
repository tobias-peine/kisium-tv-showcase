package de.kisium.tv.core.ui

import androidx.compose.material3.Typography as Material3Typography
import androidx.compose.material3.darkColorScheme as materialDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography as TvTypography
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme

object KisiumPalette {
    val Ink = Color(0xFF06080D)
    val InkSoft = Color(0xFF0B0F16)
    val Panel = Color(0xFF121822)
    val PanelRaised = Color(0xFF1A2230)
    val Text = Color(0xFFF5F7FB)
    val TextMuted = Color(0xFF9EA9BA)
    val Accent = Color(0xFFFF5A36)
    val AccentSoft = Color(0xFFFF8B57)
    val Teal = Color(0xFF24E0C1)
    val DisneyBlue = Color(0xFF113CCF)
    val DisneyGlow = Color(0xFF2A6BFF)
    val Danger = Color(0xFFE5484D)
    val Border = Color(0xFF2A3444)
}

private val tvTypography = TvTypography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 58.sp,
        lineHeight = 62.sp,
        letterSpacing = (-1.5).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.6.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 1.sp,
    ),
)

private val materialTypography = Material3Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 58.sp,
        lineHeight = 62.sp,
        letterSpacing = (-1.5).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.6.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 1.sp,
    ),
)

@Composable
fun KisiumTheme(content: @Composable () -> Unit) {
    // The app is TV-only and deliberately always dark.
    val tvScheme = tvDarkColorScheme(
        primary = KisiumPalette.Accent,
        onPrimary = Color.White,
        primaryContainer = KisiumPalette.Accent.copy(alpha = 0.22f),
        onPrimaryContainer = KisiumPalette.Text,
        secondary = KisiumPalette.Teal,
        onSecondary = KisiumPalette.Ink,
        background = KisiumPalette.Ink,
        onBackground = KisiumPalette.Text,
        surface = KisiumPalette.Panel,
        onSurface = KisiumPalette.Text,
        surfaceVariant = KisiumPalette.PanelRaised,
        onSurfaceVariant = KisiumPalette.TextMuted,
        border = KisiumPalette.Border,
        borderVariant = KisiumPalette.Border,
        error = KisiumPalette.Danger,
        onError = Color.White,
    )
    val m3Scheme = materialDarkColorScheme(
        primary = KisiumPalette.Accent,
        onPrimary = Color.White,
        primaryContainer = KisiumPalette.Accent.copy(alpha = 0.22f),
        onPrimaryContainer = KisiumPalette.Text,
        secondary = KisiumPalette.Teal,
        onSecondary = KisiumPalette.Ink,
        background = KisiumPalette.Ink,
        onBackground = KisiumPalette.Text,
        surface = KisiumPalette.Panel,
        onSurface = KisiumPalette.Text,
        surfaceVariant = KisiumPalette.PanelRaised,
        onSurfaceVariant = KisiumPalette.TextMuted,
        outline = KisiumPalette.Border,
        error = KisiumPalette.Danger,
        onError = Color.White,
    )
    androidx.tv.material3.MaterialTheme(
        colorScheme = tvScheme,
        typography = tvTypography,
    ) {
        androidx.compose.material3.MaterialTheme(
            colorScheme = m3Scheme,
            typography = materialTypography,
        ) {
            content()
        }
    }
}
