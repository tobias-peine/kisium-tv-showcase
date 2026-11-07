# Architektur

## Modulbild

```text
                    ┌──────────────────────────────┐
                    │            app-tv            │
                    │ Hilt, MainActivity, VM,      │
                    │ Screens, Disney+-Deep-Link   │
                    └──────┬───────┬───────┬───────┘
                           │       │       │
             ┌─────────────┘       │       └──────────────┐
             ▼                     ▼                      ▼
    ┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐
    │   core-player   │   │    core-ui      │   │ feature-settings│
    │ Media3, HLS/TS  │   │ Compose for TV  │   │ TV-Formular     │
    └────────┬────────┘   └────────┬────────┘   └────────┬────────┘
             │                     │                     │
             └──────────┬──────────┴──────────┬──────────┘
                        ▼                     ▼
               ┌─────────────────┐   ┌─────────────────┐
               │   source-xtream │   │ source-metadata │
               │ Retrofit, EPG,  │   │ optional TMDB   │
               │ Demo, M3U/XMLTV │   └─────────────────┘
               └────────┬────────┘
                        ▼
               ┌─────────────────┐
               │    core-data    │
               │ Room, Settings  │
               └────────┬────────┘
                        ▼
               ┌─────────────────┐
               │   core-model    │
               │ reine Modelle   │
               └─────────────────┘
```

`core-model` ist ein reines Kotlin-JVM-Modul. Dadurch kann `source-xtream` die
Domänenmodelle nutzen, ohne von `core-ui` abhängig zu werden. `core-data` enthält
die Persistenz und hängt nur von `core-model` ab.

## Datenfluss

```text
MainViewModel
  ├─ SettingsRepository (StateFlow<AppSettings>)
  ├─ ContentRepositoryProvider.current()
  │    ├─ DemoContentRepository     (wenn demoMode oder unvollständig)
  │    └─ XtreamRepository          (sonst)
  ├─ WatchHistoryRepository
  └─ TmdbMetadataProvider (optional, nur bei Key)

XtreamRepository
  ├─ XtreamApi (Retrofit)
  ├─ CatalogCache (Room, TTL 6 h)
  ├─ EpgCache (Room)
  └─ WatchHistoryRepository (Weiterschauen-Rail)
```

Der Provider cached die zuletzt erzeugte Repository-Instanz und wechselt sie,
sobald sich `serverUrl`, `username` oder `password` ändern. Die `sourceId` ist
ein SHA-1-Fingerabdruck aus Server und Benutzername, damit Cache-Einträge
verschiedener Panels nicht vermischt werden.

## Persistenz

### Room

Datenbank `kisium-tv.db` (Version 1, `exportSchema = false`):

| Tabelle | Zweck |
|---|---|
| `catalog_cache` | JSON-Blobs für Kategorien und Poster-Listen, Key inkl. Quelle |
| `epg_cache` | Now/Next-Einträge pro Sender, Primary Key `(sourceId, channelId, startMillis)` |
| `watch_progress` | Resume-Punkt, JSON des PosterItem, Fortschritt in Prozent |

Der Katalog-Cache hat eine TTL von sechs Stunden. Ist der Server nicht
erreichbar, wird bewusst der veraltete Cache als Fallback benutzt, damit die App
nicht mit einem leeren Home-Screen stehen bleibt.

### Verschlüsselte Einstellungen

`SettingsRepository` verwendet `EncryptedSharedPreferences` mit
`MasterKey AES256_GCM`. Gespeichert werden Server-URL, Benutzername, Passwort,
TMDB-Key, Autoplay und Demo-Flag. Das Passwort erscheint nie in Logs.

Auf API 21/22 (oder bei einem defekten Vendor-Keystore) fällt die Implementierung
auf eine private SharedPreferences-Datei mit Base64-Obfuskation zurück und meldet
`secureStorage = false` in der UI. Kein Zielgerät (Android TV 11 / Fire OS 7/8)
benötigt diesen Fallback.

## UI

- `KisiumTheme` setzt **TV Material 3** und **Compose Material 3** auf dieselbe
  dunkle Palette. Dadurch funktionieren TV-Komponenten und normale
  Material-3-Texte konsistent.
- Fokus wird nicht über Leanback, sondern über `Modifier.clickable` +
  `InteractionSource.collectIsFocusedAsState()` geführt. D-Pad-OK löst `onClick`
  aus; es gibt keine Touch-Interaktion.
- Karten skalieren auf 1.07, erhalten einen weißen Rand und blenden Titel/Metadaten
  ein. `LazyRow`/`LazyColumn` halten den Fokus-Scroll automatisch im Bild.
- Der Hero startet nach 3,6 s eine stumme Vorschau, sofern
  `autoplayPreview` aktiv und `previewUrl` vorhanden ist. Beim Verlassen des
  Composable wird der ExoPlayer freigegeben.
- Der Home-Screen ist eine einzige `LazyColumn`; die App-Leiste liegt als Overlay
  darüber. So bleibt der Hero randlos, ohne verschachtelte Scroll-Container.

## Player

- `KisiumPlayerFactory` baut ExoPlayer mit kleinem LoadControl (12 s min, 32 s max).
- HLS wird über `HlsMediaSource` erkannt (`.m3u8`); alles andere läuft über
  `ProgressiveMediaSource` (MP4/TS).
- Live bekommt primär die HLS-URL und eine TS-URL als `alternateUrl`. Bei
  `onPlayerError` wechselt der Screen einmalig auf TS.
- Der Player wird pro `itemKey`/URL in `remember` gehalten und in
  `DisposableEffect` freigegeben. Beim Senderwechsel wird die alte Instanz
  entsorgt, die neue über einen `key(...)`-Block aufgebaut.
- Fortschritt wird alle 15 s und beim Verlassen gespeichert. Unter 2 % oder über
  95 % wird der Eintrag entfernt.
- Lifecycle: `ON_STOP` pausiert. Es gibt bewusst **keinen** Hintergrunddienst.

## Fehlerbehandlung

- Katalogfehler → `HomeUiState.Error` mit Retry/Settings.
- Detailfehler → `DetailUiState.Error` mit Retry/Zurück.
- Playerfehler → HLS→TS-Fallback, danach Fehler-Overlay mit Retry.
- Settings-Test → `ConnectionResult` mit lesbarer Meldung, ohne Passwort im Text.
- HTTP-/IO-Fehler werden in `XtreamException` mit kurzer deutscher Meldung
  übersetzt.

## Erweiterungspunkte

- **Voll-EPG**: `XmltvParser` streamt XMLTV; ein periodischer Worker könnte die
  Tabelle `epg_cache` befüllen. Wegen RAM/Netz ist das nicht standardmäßig aktiv.
- **M3U-Fallback**: `M3uParser` akzeptiert direkte Stream-URLs; ein Panel ohne
  brauchbare player_api kann damit beliefert werden.
- **Hintergrundwiedergabe**: `MediaSessionService` + Foreground-Service ergänzen;
  aktuell bewusst nicht enthalten.
- **Weitere Quellen**: `ContentRepository` implementieren und im Provider
  registrieren.
- **Release-Signing**: eigenen Keystore in `app-tv/build.gradle.kts` eintragen;
  Debug-Key nicht für eine Verteilung außerhalb des eigenen Geräts verwenden.
