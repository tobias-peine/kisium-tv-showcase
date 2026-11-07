package de.kisium.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.kisium.tv.core.model.PosterItem
import de.kisium.tv.core.ui.FocusHints
import de.kisium.tv.core.ui.KisiumButton
import de.kisium.tv.core.ui.KisiumChip
import de.kisium.tv.core.ui.KisiumLoading
import de.kisium.tv.core.ui.KisiumLogo
import de.kisium.tv.core.ui.KisiumPalette
import de.kisium.tv.core.ui.KisiumTextAction
import de.kisium.tv.core.ui.MediaCard

@Composable
fun LiveScreen(
    live: LiveUiState,
    onSelectCategory: (String) -> Unit,
    onPlay: (PosterItem) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (live.loading && live.categories.isEmpty()) {
        KisiumLoading("Senderliste wird geladen …", modifier)
        return
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A0F17), KisiumPalette.Ink),
                ),
            ),
    ) {
        val columns = if (maxWidth >= 1_100.dp) 3 else 2
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 54.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 56.dp, end = 56.dp, top = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KisiumLogo(fontSize = 22, showPrivateLabel = true)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Live-TV",
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.8).sp,
                    )
                    Spacer(Modifier.weight(1f))
                    KisiumTextAction("← Zurück", onBack)
                }
            }
            item {
                Text(
                    text = "Sender auswählen · Im Player mit CH+ / CH− zappen · EPG Now/Next",
                    color = KisiumPalette.TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 56.dp, top = 7.dp, end = 56.dp),
                )
            }
            if (live.categories.isNotEmpty()) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 56.dp, vertical = 15.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(live.categories, key = { it.id.ifBlank { "all" } }) { category ->
                            KisiumChip(
                                text = category.name,
                                selected = category.id == live.selectedCategoryId,
                                onClick = { onSelectCategory(category.id) },
                            )
                        }
                    }
                }
            }

            if (live.error != null) {
                item {
                    Column(
                        modifier = Modifier.padding(56.dp).widthIn(max = 620.dp),
                    ) {
                        Text(
                            text = "Senderliste nicht verfügbar",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = live.error,
                            color = KisiumPalette.TextMuted,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            KisiumButton("Erneut versuchen", onRetry, leading = "↻")
                            KisiumButton("Zurück", onBack, primary = false)
                        }
                    }
                }
            } else if (live.channels.isEmpty() && !live.loading) {
                item {
                    Text(
                        text = "In dieser Kategorie sind keine Sender hinterlegt.",
                        color = KisiumPalette.TextMuted,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(56.dp),
                    )
                }
            }

            items(live.channels.chunked(columns), key = { row -> row.joinToString("|") { it.cardKey } }) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 56.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    row.forEach { channel ->
                        MediaCard(
                            item = channel,
                            onClick = { onPlay(channel) },
                        )
                    }
                }
            }

            if (live.loading && live.channels.isNotEmpty()) {
                item {
                    Text(
                        text = "Aktualisiere Sender …",
                        color = KisiumPalette.TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 56.dp, vertical = 10.dp),
                    )
                }
            }

            item {
                Spacer(Modifier.height(20.dp))
                FocusHints()
            }
        }
    }
}
