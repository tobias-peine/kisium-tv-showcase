package de.kisium.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.kisium.tv.core.ui.KisiumLogo
import de.kisium.tv.core.ui.KisiumPalette

@Composable
fun KisiumSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF271316),
                        Color(0xFF0B1018),
                        KisiumPalette.Ink,
                    ),
                    radius = 1_400f,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "PRIVAT · ANDROID TV",
                color = KisiumPalette.Teal,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(22.dp))
            KisiumLogo(fontSize = 66, showPrivateLabel = false)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "DEIN KINO. DEINE REGELN.",
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 4.sp,
            )
            Spacer(Modifier.height(42.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(
                    color = KisiumPalette.Accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "Katalog wird vorbereitet",
                    color = KisiumPalette.TextMuted,
                    fontSize = 12.sp,
                )
            }
        }
        Text(
            text = "KISIUM TV · VERSION 1.0",
            color = Color.White.copy(alpha = 0.28f),
            fontSize = 10.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp),
        )
    }
}
