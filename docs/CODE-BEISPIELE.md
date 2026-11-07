# Codebeispiele

Die Dateien sind vollständig im Projekt; hier die Stellen, die beim Umbauen am
wichtigsten sind.

## 1. Xtream-API-Client

Retrofit-Interface: [`source-xtream/.../XtreamApi.kt`](../source-xtream/src/main/kotlin/de/kisium/tv/source/xtream/XtreamApi.kt)

```kotlin
interface XtreamApi {
    @GET("player_api.php")
    suspend fun authenticate(
        @Query("username") username: String,
        @Query("password") password: String,
    ): XtreamAuthDto

    @GET("player_api.php")
    suspend fun liveStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_streams",
        @Query("category_id") categoryId: String? = null,
    ): List<XtreamLiveStreamDto>

    @GET("player_api.php")
    suspend fun shortEpg(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_short_epg",
        @Query("stream_id") streamId: String,
        @Query("limit") limit: Int = 8,
    ): XtreamEpgResponse
}
```

Factory und stream-URL-Bau: [`XtreamApiFactory.kt`](../source-xtream/src/main/kotlin/de/kisium/tv/source/xtream/XtreamApiFactory.kt)

```kotlin
class XtreamApiFactory(
    private val gson: Gson,
    private val client: OkHttpClient,
) {
    fun create(config: XtreamConfig): XtreamApi =
        Retrofit.Builder()
            .baseUrl(config.baseUrl)              // endet auf "/"
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(XtreamApi::class.java)

    fun liveStreamUrl(config: XtreamConfig, id: Int, ext: String = "m3u8"): String =
        config.baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("live")
            .addPathSegment(config.username)
            .addPathSegment(config.password)
            .addPathSegment("$id.$ext")
            .build()
            .toString()
}
```

Wichtig: `addPathSegment` kodiert Benutzername/Passwort URL-sicher. Die
Server-URL wird in `XtreamConfig.normalizeServerUrl()` von `/player_api.php`
usw. befreit.

Repository-Aufruf mit Cache-Fallback:

```kotlin
override suspend fun loadCategoryItems(kind: MediaKind, categoryId: String?): List<PosterItem> {
    val cacheKey = "${config.sourceId}:items:${kind.name}:${categoryId ?: "all"}"
    cache.readItems(cacheKey)?.let { return it }

    return try {
        val items = when (kind) {
            MediaKind.LIVE -> api.liveStreams(
                config.username, config.password, "get_live_streams", categoryId,
            ).mapNotNull { it.toPosterItem() }
            // ...
        }
        cache.writeItems(cacheKey, items)
        items
    } catch (t: Throwable) {
        cache.readItems(cacheKey, Long.MAX_VALUE) ?: throw t
    }
}
```

## 2. Karussell mit D-Pad-Fokus

Vereinfacht aus [`core-ui/.../Cards.kt`](../core-ui/src/main/kotlin/de/kisium/tv/core/ui/Cards.kt):

```kotlin
@Composable
fun MediaCard(item: PosterItem, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, tween(140))
    val shape = RoundedCornerShape(8.dp)

    Box(
        Modifier
            .width(172.dp)
            .height(258.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(KisiumPalette.Panel)
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) Color.White else Color.White.copy(alpha = 0.10f),
                shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = "${item.title} öffnen",
                onClick = onClick,          // D-Pad-OK löst das aus
            ),
    ) {
        Artwork(item.imageUrl, item.title, Modifier.fillMaxSize())
    }
}

@Composable
fun Rail(items: List<PosterItem>, onClick: (PosterItem) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 56.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(items, key = { it.cardKey }) { item ->
            MediaCard(item) { onClick(item) }
        }
    }
}
```

Kein `pointerInput`, kein Touch: `clickable` ist gleichzeitig der Fokus- und
OK-Handler. `InteractionSource.collectIsFocusedAsState()` liefert den Zustand für
Scale, Border und Titel-Overlay.

## 3. ExoPlayer mit HLS und TS-Fallback

Aus [`core-player/.../KisiumPlayerFactory.kt`](../core-player/src/main/kotlin/de/kisium/tv/core/player/KisiumPlayerFactory.kt):

```kotlin
fun mediaSource(request: PlaybackRequest, client: OkHttpClient): MediaSource {
    val dataSource = OkHttpDataSource.Factory(client)
        .setUserAgent("KisiumTV/1.0 (Android TV)")
        .setDefaultRequestProperties(request.headers)

    val item = MediaItem.Builder()
        .setUri(request.url)
        .setMediaId(request.itemKey)
        .build()

    return if (request.url.substringBefore('?').endsWith(".m3u8")) {
        HlsMediaSource.Factory(dataSource)
            .setAllowChunklessPreparation(true)
            .createMediaSource(item)
    } else {
        ProgressiveMediaSource.Factory(
            dataSource,
            DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true),
        ).createMediaSource(item)
    }
}

fun prepare(player: ExoPlayer, request: PlaybackRequest, client: OkHttpClient) {
    player.setMediaSource(mediaSource(request, client))
    player.prepare()
    player.playWhenReady = true
}
```

Der Player-Screen hört auf Fehler und wechselt einmalig auf TS:

```kotlin
override fun onPlayerError(error: PlaybackException) {
    if (!useAlternate && request.alternateUrl != null) {
        useAlternate = true          // LaunchedEffect baut die TS-Quelle
    } else {
        errorMessage = error.localizedMessage
    }
}
```

Live-Request enthält beide URLs:

```kotlin
PlaybackRequest(
    itemKey = item.cardKey,
    title = item.title,
    url = "${base}/live/$user/$pass/$id.m3u8",
    alternateUrl = "${base}/live/$user/$pass/$id.ts",
    kind = MediaKind.LIVE,
    isLive = true,
)
```

## 4. Disney+-Deep-Link

Aus [`app-tv/.../DisneyPlusLauncher.kt`](../app-tv/src/main/kotlin/de/kisium/tv/DisneyPlusLauncher.kt):

```kotlin
object DisneyPlusLauncher {
    private val packages = listOf(
        "com.disney.disneyplus",
        "com.disney.disneyplus.androidtv",
    )

    fun open(context: Context): Boolean {
        val pm = context.packageManager
        for (pkg in packages) {
            val intent = pm.getLeanbackLaunchIntentForPackage(pkg)
                ?: pm.getLaunchIntentForPackage(pkg)
                ?: continue
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
        }
        return false
    }
}
```

`<queries>` im Manifest ist nötig, damit Android 11+ die Disney-Pakete sehen
darf. Der Fallback öffnet `https://play.google.com/store/apps/details?id=...`
bzw. den Amazon-Appstore. Keine GMS-API, kein DRM.

## 5. Verschlüsselte Settings

Aus [`core-data/.../SettingsRepository.kt`](../core-data/src/main/kotlin/de/kisium/tv/core/data/SettingsRepository.kt):

```kotlin
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

val prefs = EncryptedSharedPreferences.create(
    context,
    "kisium_secure_settings",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
)

prefs.edit()
    .putString("server_url", serverUrl)
    .putString("username", username)
    .putString("password", password)
    .apply()
```

Das Passwort wird nie geloggt. Der API-Client hat bewusst keinen
`HttpLoggingInterceptor`; Query-Strings mit Zugangsdaten würden sonst im
Logcat landen.

## 6. EPG Base64 dekodieren

Xtream liefert EPG-Texte oft Base64-kodiert:

```kotlin
private fun decodeEpgText(value: String?): String? {
    if (value.isNullOrBlank()) return null
    val decoded = runCatching {
        String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8).trim()
    }.getOrNull() ?: return value.trim()

    val printable = decoded.count {
        it.isLetterOrDigit() || it.isWhitespace() || it.isPunctuation()
    }
    return if (printable >= decoded.length * 0.85) decoded else value.trim()
}
```

## 7. Endpunkt-Matrix

| Zweck | Endpunkt |
|---|---|
| Login/Test | `player_api.php?username=…&password=…` |
| Kategorien | `player_api.php?…&action=get_live_categories` / `get_vod_categories` / `get_series_categories` |
| Live | `player_api.php?…&action=get_live_streams[&category_id=]` |
| VOD | `player_api.php?…&action=get_vod_streams[&category_id=]` |
| Serien | `player_api.php?…&action=get_series[&category_id=]` |
| Serien-Details | `player_api.php?…&action=get_series_info&series_id=…` |
| Now/Next | `player_api.php?…&action=get_short_epg&stream_id=…&limit=8` |
| M3U | `get.php?username=…&password=…&type=m3u_plus&output=ts` |
| XMLTV | `xmltv.php?username=…&password=…` |

Die vollständigen Parser liegen in
[`M3uParser.kt`](../source-xtream/src/main/kotlin/de/kisium/tv/source/xtream/M3uParser.kt)
und
[`XmltvParser.kt`](../source-xtream/src/main/kotlin/de/kisium/tv/source/xtream/XmltvParser.kt).
