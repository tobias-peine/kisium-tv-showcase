package de.kisium.tv.feature.settings

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.kisium.tv.core.model.AppSettings
import de.kisium.tv.core.ui.KisiumBadge
import de.kisium.tv.core.ui.KisiumButton
import de.kisium.tv.core.ui.KisiumLogo
import de.kisium.tv.core.ui.KisiumPalette
import de.kisium.tv.core.ui.KisiumTextAction
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    settings: AppSettings,
    secureStorage: Boolean,
    busy: Boolean,
    message: String?,
    messageIsError: Boolean,
    onSave: (AppSettings) -> Unit,
    onTest: (AppSettings) -> Unit,
    onClearCache: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var serverUrl by remember(settings) { mutableStateOf(settings.serverUrl) }
    var username by remember(settings) { mutableStateOf(settings.username) }
    var password by remember(settings) { mutableStateOf(settings.password) }
    var tmdbKey by remember(settings) { mutableStateOf(settings.tmdbApiKey) }
    var autoplay by remember(settings) { mutableStateOf(settings.autoplayPreview) }
    var demoMode by remember(settings) { mutableStateOf(settings.demoMode) }
    var useJsonCatalog by remember(settings) { mutableStateOf(settings.useJsonCatalog) }
    var catalogJsonUri by remember(settings) { mutableStateOf(settings.catalogJsonUri) }

    val serverFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(250)
        runCatching { serverFocus.requestFocus() }
    }

    fun currentSettings() = AppSettings(
        serverUrl = serverUrl,
        username = username,
        password = password,
        tmdbApiKey = tmdbKey,
        autoplayPreview = autoplay,
        demoMode = if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) true else demoMode,
        useJsonCatalog = useJsonCatalog && catalogJsonUri.isNotBlank(),
        catalogJsonUri = catalogJsonUri.trim(),
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(KisiumPalette.Ink, Color(0xFF0A111A), KisiumPalette.Ink),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 58.dp, end = 58.dp, top = 22.dp, bottom = 54.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KisiumLogo(fontSize = 22, showPrivateLabel = true)
                Spacer(Modifier.width(20.dp))
                Text(
                    text = "Einstellungen",
                    color = KisiumPalette.TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.weight(1f))
                KisiumTextAction("← Zurück", onBack)
            }

            Spacer(Modifier.height(28.dp))
            Text(
                text = "Quellen & Wiedergabe",
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp,
            )
            Text(
                text = "Alles bleibt lokal auf diesem Gerät. Zugangsdaten werden nur für deinen " +
                    "Proxy-Server verwendet.",
                color = KisiumPalette.TextMuted,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(28.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(38.dp),
            ) {
                Column(modifier = Modifier.widthIn(min = 300.dp, max = 390.dp)) {
                    Text(
                        text = "SERVER & KATALOG",
                        color = KisiumPalette.Teal,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Filme kommen aus catalog.json; der Proxy liefert den Katalog und " +
                            "leitet /movie/... per 302 an die M3U-Quelle weiter. Xtream-direkt " +
                            "bleibt optional, Live-TV/Serien sind im M3U-Modus nicht separat modelliert.",
                        color = KisiumPalette.TextMuted,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                    )
                    Spacer(Modifier.height(18.dp))
                    StatusCard(
                        title = if (settings.isConfigured && !settings.demoMode) "Proxy aktiv" else "Demo-Modus",
                        detail = if (settings.isConfigured && !settings.demoMode) {
                            "Verbunden als ${settings.username}"
                        } else {
                            "Ohne Zugangsdaten startet Kisium TV mit dem eingebauten Demo-Katalog."
                        },
                        accent = if (settings.isConfigured && !settings.demoMode) KisiumPalette.Teal else KisiumPalette.AccentSoft,
                    )
                    Spacer(Modifier.height(14.dp))
                    StatusCard(
                        title = if (secureStorage) "Sicher gespeichert" else "Kompatibilitätsmodus",
                        detail = if (secureStorage) {
                            "Passwort mit AES-256-GCM über den Android Keystore verschlüsselt."
                        } else {
                            "Auf diesem alten Android-Stand ist kein sicherer Keystore verfügbar. " +
                                "Bitte Gerät auf Android 7+ aktualisieren."
                        },
                        accent = if (secureStorage) KisiumPalette.Teal else KisiumPalette.Danger,
                    )
                    if (tmdbKey.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        StatusCard(
                            title = "TMDB-Anreicherung",
                            detail = "Metadaten werden bei Bedarf für Filme und Serien ergänzt.",
                            accent = KisiumPalette.DisneyGlow,
                        )
                    }
                    if (useJsonCatalog && catalogJsonUri.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        StatusCard(
                            title = "JSON-Katalog aktiv",
                            detail = "Filme werden aus catalog.json geladen; die Wiedergabe läuft " +
                                "über den 302-Proxy.",
                            accent = KisiumPalette.Teal,
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f).widthIn(max = 650.dp)) {
                    SettingsField(
                        label = "SERVER-URL",
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        placeholder = "http://192.168.1.20:8080",
                        keyboardType = KeyboardType.Uri,
                        modifier = Modifier.fillMaxWidth().focusRequester(serverFocus),
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        SettingsField(
                            label = "BENUTZERNAME",
                            value = username,
                            onValueChange = { username = it },
                            placeholder = "username",
                            modifier = Modifier.weight(1f),
                        )
                        SettingsField(
                            label = "PASSWORT",
                            value = password,
                            onValueChange = { password = it },
                            placeholder = "••••••••",
                            password = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    SettingsField(
                        label = "TMDB API-KEY (OPTIONAL)",
                        value = tmdbKey,
                        onValueChange = { tmdbKey = it },
                        placeholder = "Für deutsche Beschreibungen und Backdrops",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(14.dp))
                    SettingsField(
                        label = "KATALOG-JSON (URL ODER DATEIPFAD)",
                        value = catalogJsonUri,
                        onValueChange = { catalogJsonUri = it },
                        placeholder = "https://your-proxy.example/catalog.json",
                        keyboardType = KeyboardType.Uri,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    ToggleRow(
                        title = "JSON-Katalog verwenden",
                        description = "Filme aus catalog.json; Live/Serien bleiben aus Xtream. " +
                            "Die Wiedergabe benötigt weiterhin die Xtream-Zugangsdaten.",
                        checked = useJsonCatalog,
                        onCheckedChange = { useJsonCatalog = it },
                    )
                    Spacer(Modifier.height(17.dp))
                    ToggleRow(
                        title = "Autoplay-Vorschau",
                        description = "Stumme Vorschau im Hero-Banner. Auf sehr schwachen Geräten besser aus.",
                        checked = autoplay,
                        onCheckedChange = { autoplay = it },
                    )
                    Spacer(Modifier.height(10.dp))
                    ToggleRow(
                        title = "Demo-Modus",
                        description = "Testkatalog mit lokalen Artworks und öffentlichen Teststreams.",
                        checked = demoMode,
                        onCheckedChange = { demoMode = it },
                    )

                    if (message != null) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = message,
                            color = if (messageIsError) Color(0xFFFF8A8D) else KisiumPalette.Teal,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        )
                    }

                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KisiumButton(
                            text = if (busy) "Bitte warten …" else "Verbindung testen",
                            onClick = { onTest(currentSettings()) },
                            primary = false,
                            enabled = !busy,
                        )
                        KisiumButton(
                            text = "Speichern",
                            onClick = { onSave(currentSettings()) },
                            enabled = !busy,
                        )
                        KisiumButton(
                            text = "Cache leeren",
                            onClick = onClearCache,
                            primary = false,
                            enabled = !busy,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = if (serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                            "Zum Aktivieren der echten Quelle den Demo-Modus ausschalten und speichern."
                        } else {
                            "Tipp: JSON per HTTP bereitstellen (z. B. http://host:8088/catalog.json) " +
                                "oder als App-Datei nach /sdcard/Android/data/de.kisium.tv/files/ legen."
                        },
                        color = KisiumPalette.TextMuted.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    detail: String,
    accent: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .padding(13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(8.dp)
                .background(accent, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = detail,
                color = KisiumPalette.TextMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun SettingsField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    Column(modifier) {
        Text(
            text = label,
            color = if (focused) Color.White else KisiumPalette.TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            ),
            cursorBrush = SolidColor(KisiumPalette.Accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (password) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) keyboard?.show()
                }
                .clip(RoundedCornerShape(7.dp))
                .background(KisiumPalette.Panel)
                .border(
                    if (focused) 2.dp else 1.dp,
                    if (focused) Color.White else Color.White.copy(alpha = 0.12f),
                    RoundedCornerShape(7.dp),
                )
                .padding(horizontal = 15.dp, vertical = 13.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = KisiumPalette.TextMuted.copy(alpha = 0.55f),
                            fontSize = 16.sp,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.035f))
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(8.dp),
            )
            .clickable(interactionSource = interaction, indication = null) {
                onCheckedChange(!checked)
            }
            .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                color = KisiumPalette.TextMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(18.dp))
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(24.dp)
                .clip(CircleShape)
                .background(if (checked) KisiumPalette.Accent else KisiumPalette.PanelRaised),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(18.dp)
                    .background(Color.White, CircleShape),
            )
        }
    }
}
