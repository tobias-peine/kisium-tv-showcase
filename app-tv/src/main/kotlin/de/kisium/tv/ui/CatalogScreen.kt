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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
fun CatalogScreen(
    title: String,
    subtitle: String,
    state: CatalogUiState,
    onSelectCategory: (String) -> Unit,
    onItemClick: (PosterItem) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.loading && state.items.isEmpty()) {
        KisiumLoading("$title werden geladen …", modifier)
        return
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A0F17), KisiumPalette.Ink))),
    ) {
        val columns = if (maxWidth >= 1_500.dp) 6 else 5
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
                        text = title,
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
                    text = subtitle,
                    color = KisiumPalette.TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 56.dp, top = 7.dp, end = 56.dp),
                )
            }
            if (state.categories.isNotEmpty()) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 56.dp, vertical = 15.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.categories, key = { it.id.ifBlank { "all" } }) { category ->
                            KisiumChip(
                                text = category.name,
                                selected = category.id == state.selectedCategoryId,
                                onClick = { onSelectCategory(category.id) },
                            )
                        }
                    }
                }
            }

            if (state.error != null) {
                item {
                    Column(modifier = Modifier.padding(56.dp).widthIn(max = 620.dp)) {
                        Text(
                            text = "Katalog nicht verfügbar",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = state.error,
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
            } else if (state.items.isEmpty() && !state.loading) {
                item {
                    Text(
                        text = if (state.favoritesMode) {
                            "Noch keine Favoriten. Öffne einen Film und tippe auf „☆ Favorit“."
                        } else {
                            "In dieser Kategorie sind keine Filme hinterlegt."
                        },
                        color = KisiumPalette.TextMuted,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(56.dp),
                    )
                }
            }

            items(state.items.chunked(columns), key = { row -> row.joinToString("|") { it.cardKey } }) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 56.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    row.forEach { movie ->
                        MediaCard(item = movie, onClick = { onItemClick(movie) })
                    }
                }
            }

            item {
                Spacer(Modifier.height(20.dp))
                FocusHints()
            }
        }
    }
}
