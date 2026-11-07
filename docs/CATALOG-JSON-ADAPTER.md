# Kotlin-Adapter für `catalog.json`

Der Adapter ist als eigenes Modul umgesetzt:

```text
source-catalog-json/
  CatalogJsonDto.kt
  CatalogJsonParser.kt
  CatalogJsonLoader.kt
  CatalogJsonContentRepository.kt
```

Er liest `catalog.json` per **HTTP(S), content:// oder Dateipfad**, parst das
Schema und kombiniert die Filme mit dem bestehenden Playback-Repository
(im Netzkino-Setup ist das die Proxy-URL).

## Datenfluss

```text
catalog-sync (Docker)
        │  catalog.json
        ▼
CatalogJsonLoader          Dispatchers.IO
        │
        ▼
CatalogJsonParser          schema_version = 1
        │
        ▼
CatalogJsonContentRepository
  ├─ Filme: JSON-Katalog (Netzkino-VOD-Sync)
  ├─ Live/Serien: Fallback bleibt leer
  ├─ Weiterschauen: Room-History
  └─ Playback: Proxy (/movie/<id>.<ext>)
        │
        ▼
ContentRepositoryProvider → MainViewModel → Compose-UI
```

## Wo der Adapter eingebunden ist

| Datei | Aufgabe |
|---|---|
| [`source-catalog-json/.../CatalogJsonDto.kt`](../source-catalog-json/src/main/kotlin/de/kisium/tv/source/catalogjson/CatalogJsonDto.kt) | Gson-Datenklassen für Schema 1 |
| [`CatalogJsonParser.kt`](../source-catalog-json/src/main/kotlin/de/kisium/tv/source/catalogjson/CatalogJsonParser.kt) | Validierung, Mapping auf `PosterItem` |
| [`CatalogJsonLoader.kt`](../source-catalog-json/src/main/kotlin/de/kisium/tv/source/catalogjson/CatalogJsonLoader.kt) | HTTP-/Datei-/Content-URI-Lesen auf `Dispatchers.IO`, 32-MB-Limit |
| [`CatalogJsonContentRepository.kt`](../source-catalog-json/src/main/kotlin/de/kisium/tv/source/catalogjson/CatalogJsonContentRepository.kt) | Home-Rails, Kategorien, Fallback, 5-Minuten-Cache |
| [`ContentRepositoryProvider.kt`](../source-xtream/src/main/kotlin/de/kisium/tv/source/xtream/ContentRepositoryProvider.kt) | schaltet JSON + Playback-Repository zusammen |
| [`AppModule.kt`](../app-tv/src/main/kotlin/de/kisium/tv/di/AppModule.kt) | Hilt-Provider für Loader/Parser |
| [`Models.kt`](../core-model/src/main/kotlin/de/kisium/tv/core/model/Models.kt) | `useJsonCatalog`, `catalogJsonUri` |
| [`SettingsRepository.kt`](../core-data/src/main/kotlin/de/kisium/tv/core/data/SettingsRepository.kt) | verschlüsselte Persistenz der Felder |
| [`SettingsScreen.kt`](../feature-settings/src/main/kotlin/de/kisium/tv/feature/settings/SettingsScreen.kt) | URL-/Pfadfeld und Aktivierungsschalter |
| [`MainViewModel.kt`](../app-tv/src/main/kotlin/de/kisium/tv/ui/MainViewModel.kt) | Speichern und optionaler Verbindungstest |

## Aktivieren

1. JSON-Katalog erzeugen bzw. bereitstellen.
2. Kisium TV → **Einstellungen**.
3. Als Server-URL den Stream-Proxy eintragen; standardmäßig ist
   `https://your-proxy.example` hinterlegt (anpassen).
4. Unter **KATALOG-JSON** ist standardmäßig
   `https://your-proxy.example/catalog.json` eingetragen (anpassen).
5. **JSON-Katalog verwenden** einschalten.
6. **Demo-Modus** ausschalten.
7. **Speichern**.

Danach zeigt der Home-Screen die Netzkino-Filme in „Für dich“, „Neu“ und „Filme“.
Live-TV und Serien sind in diesem VOD-Setup nicht separat modelliert.

## HTTP-Beispiel

Der Proxy liefert die Datei direkt aus dem Daten-Volume:

```text
https://your-proxy.example/catalog.json
```

Das Tablet/der Stick muss diese LAN-Adresse erreichen können. Der Endpunkt
liefert nur Metadaten, keine Stream-URLs, und ist ohne Proxy-Basic-Auth
erreichbar.

## Datei-Beispiel

Der Loader unterstützt rohe Pfade, `file://` und `content://`.

Für die Debug-App ist der App-private Pfad am einfachsten:

```bash
adb push data/catalog.json /data/local/tmp/catalog.json
adb shell run-as de.kisium.tv cp /data/local/tmp/catalog.json files/catalog.json
```

Im Feld eintragen:

```text
/data/user/0/de.kisium.tv/files/catalog.json
```

`run-as` funktioniert nur bei debuggable Builds. Für einen Release-Build müsste
die Datei über SAF/`content://` oder einen HTTP-Server kommen.

## Schema

```json
{
  "schema_version": 1,
  "generated_at": "2026-11-07T11:38:24Z",
  "source": { "id": "netzkino:af8f69086d1c", "type": "netzkino" },
  "pagination": { "page_size": 100, "total": 2 },
  "movies": [
    {
      "id": "1682145648",
      "stream_id": 1682145648,
      "source_id": "netzkino:af8f69086d1c",
      "kind": "MOVIE",
      "title": "100.000 verdammte Dollar",
      "category_id": "Actionkino",
      "category_name": "Actionkino",
      "image_url": "https://images.example/cover.jpg",
      "backdrop_url": "https://images.example/wide.jpg",
      "plot": "Ein Revolverheld gerät zwischen Gier, Gold und Verrat.",
      "year": 1967,
      "rating": null,
      "added": null,
      "container_extension": "mp4"
    }
  ]
}
```

Nur `schema_version: 1` wird akzeptiert. Unbekannte Felder ignoriert Gson.
Filme ohne `stream_id`/`id` oder ohne Titel werden übersprungen; doppelte
`stream_id`s werden dedupliziert.

## Verhalten

- **Hintergrund-I/O**: `CatalogJsonLoader.load()` nutzt `withContext(Dispatchers.IO)`.
- **Kein UI-Block**: `CatalogJsonContentRepository` lädt JSON und Playback-Repository
  parallel; der Fallback wird nach 2 s abgebrochen, damit ein langsamer
  Proxy/Panel die Film-Rails nicht blockiert.
- **Cache**: 5 Minuten im Repository; ein neuer Settings-Save erzeugt eine neue
  Repository-Instanz und damit einen frischen Ladevorgang.
- **Fallback**: Ist `catalog.json` nicht erreichbar, bleibt ein ggf. vorhandener
  Direkt-Katalog sichtbar (im Netzkino-Setup ist er leer). Ist der Proxy nicht
  erreichbar, bleiben die JSON-Filme sichtbar.
- **Playback**: Der JSON-Katalog enthält absichtlich keine Stream-URLs. Die App
  baut sie über das Playback-Repository als
  `http://proxy:5000/movie/<user>/<pass>/<stream_id>.<ext>`; der Proxy
  antwortet mit 302 auf die Netzkino-MP4-URL. Ohne Proxy ist keine Wiedergabe
  möglich.
- **Dateigröße**: maximal 32 MB. Das reicht für große Kataloge, ohne auf einem
  1–2-GB-Stick unkontrolliert Speicher zu belegen.

## Tests

```bash
./gradlew :source-catalog-json:testDebugUnitTest
```

Abgedeckt: Schema-Validierung, Mapping, Sortierung, Deduplizierung, ungültige
Einträge. Zusätzlich wurde der Adapter auf dem waipu-Stick mit einer lokalen
`catalog.json` getestet: Die Filme erschienen als Hero und in den Rails, der
Quellen-Label zeigte „JSON-Katalog + Proxy“.

## Erweiterungsideen

- Zusätzliche Rails aus `category_name` (z. B. „Doku“, „Action“).
- ETag/Last-Modified für HTTP-Kataloge.
- `content://`-Auswahl per Android-TV-Dateiauswahl.
- Ein optionaler HTTP-Server im `catalog-sync`-Container, damit die App den
  Katalog direkt vom NAS laden kann.
