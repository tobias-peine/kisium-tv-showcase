# Stolperfallen und Gegenmaßnahmen

## Fire OS ohne Google Play Services

Fire OS hat kein GMS. Kisium TV verwendet deshalb bewusst **keine** Firebase-,
Cast-, Play-Billing-, Maps- oder Play-Core-Abhängigkeiten. Hilt, Room, Compose,
Coil und Media3 funktionieren ohne GMS.

Wenn du später etwas ergänzt:

- **Firebase/Crashlytics** → auf Fire OS nicht verfügbar; eigenes Logging oder
  ACRA/File-Log verwenden.
- **Google Cast** → benötigt Play Services; auf Fire TV nicht nutzbar. Alternativ
  DIAL/Remote-UI oder eigenes Protokoll.
- **Play Store** → `Intent(ACTION_VIEW, https://play.google.com/...)` ist nur ein
  Link. Es wird keine Play-Store-API aufgerufen. Auf Fire TV ist der Amazon-
  Appstore der passende Weg.
- **Google Sign-In** → nicht verfügbar. Für dieses Projekt nicht nötig.

## Fire-OS-Versionen

| Fire OS | Android-Basis | API | Typische Geräte |
|---|---|---|---|
| Fire OS 5 | Android 5.1 | 22 | alte Fire-TV-Geräte |
| Fire OS 6 | Android 7.1 | 25 | Fire TV 3. Gen |
| Fire OS 7 | Android 9 | 28 | Fire TV Stick HD (2024), Lite, 3. Gen |
| Fire OS 8 | Android 11 | 30 | Fire TV Stick 4K Max 2. Gen, 4K Plus 2025 |
| Fire OS 14 | Android 12–14 | 31–34 | neuere TVs/Boxen |
| Fire OS 16 | Android 15/16 | 35/36 | zukünftige Geräte |
| **Vega OS 2.0** | **kein Android** | – | Fire TV Stick 4K Select, 4K 3. Gen 2026 |

`minSdk 21`, `targetSdk 34` deckt Fire OS 7/8 und Android TV 11 ab. Auf API 21/22
gibt es den EncryptedSharedPreferences-Fallback (siehe unten). Neue Vega-OS-
Geräte können keine APK installieren; das ist keine Einstellung, sondern eine
andere Plattform.

## waipu.tv und Disney+ als externe Apps

Die offiziellen Apps werden per Deep Link gestartet (`waipu://tv` bzw.
Disney+-Leanback-Intent). Eine native Einbettung würde OAuth/Account-Token,
interne Stream-Endpunkte und Widevine-DRM erfordern. Kodi-Addons zeigen, dass
das technisch mit viel Reverse Engineering möglich ist, sind aber keine
stabile oder offizielle API. Kisium TV bleibt deshalb bei den Deep Links.

## Widevine

- **waipu.tv 4K Stick (SEI700WP)**: Widevine **L1**.
- **Fire TV Stick 4K Max (2. Gen)**: Widevine **L1 und L3** laut
  Amazon-Device-Spec.
- Kisium TV selbst initialisiert **kein DRM**. Xtream-Streams sind in der Regel
  unverschlüsselt (HLS/TS/MP4). Falls dein Panel DRM nutzt, müsste ein
  `DefaultDrmSessionManager` mit Lizenz-URL und Provisioning ergänzt werden.
- **Disney+** kann wegen Widevine/DRM und fehlender offizieller API nicht in der
  eigenen Oberfläche abgespielt werden. Die Kachel startet deshalb die offizielle
  App. Es gibt keinen DRM-Bypass und kein Account-Scraping in diesem Projekt.
- L1/L3 ist für die eigene Xtream-Wiedergabe irrelevant, solange kein DRM
  verwendet wird. Für Disney+ entscheidet die offizielle App über die Qualität.

## HLS, TS und Codecs

- Live wird zuerst als `<base>/live/<user>/<pass>/<id>.m3u8` versucht. Bei einem
  Playerfehler schaltet die App einmalig auf `.ts` um.
- VOD nutzt die `container_extension` aus `get_vod_streams` (meist `mp4`, `mkv`
  oder `avi`). Nicht jeder ExoPlayer/Codec kann alle Container. MP4/H.264/AAC ist
  am kompatibelsten.
- Einige Panels liefern HLS unter einer URL ohne `.m3u8` oder mit Query-Parametern.
  Die Erkennung prüft den Pfad vor `?`. Wenn dein Panel das anders macht, in
  `KisiumPlayerFactory.looksLikeHls()` ergänzen oder die URL mit `.m3u8`
  erzwingen.
- 302-Redirects und HTTPS übernimmt OkHttp. Private Zertifikate werden nicht
  akzeptiert; bei selbstsignierten Servern müsstest du ein Network-Security-
  Config mit eigenem Trust Anchor ergänzen.
- AC3/EAC3/DTS: Fire TV und waipu können je nach Modell/Receiver passthrough oder
  Software-Decoding anders behandeln. Bei fehlendem Ton zuerst eine AAC-Spur
  testen.
- 10-Bit-HEVC/AV1 kann auf älteren Sticks ruckeln oder gar nicht decodieren.
  Wenn möglich, im Panel/Stream die H.264-Variante wählen.

## RAM und schwache Hardware

- `android:largeHeap="false"` bleibt bewusst gesetzt.
- Coil: 32 MB Memory-Cache, 128 MB Disk-Cache, WebP-Artworks statt riesiger PNGs.
- Es läuft immer nur **ein** ExoPlayer zur Zeit. Die Hero-Vorschau wird beim
  Wechsel in den Player/Detail-Screen freigegeben.
- Die Home-Rails sind auf 20 Einträge begrenzt. `LazyRow`/`LazyColumn` komponieren
  nur sichtbare Karten.
- **Paging**: Die Xtream-API bietet keine serverseitigen Page-Parameter. Die App
  lädt die JSON-Liste einmal, cached sie in Room und zeigt sie lazy an. Für sehr
  große Panels (10.000+ Sender) ist das der größte RAM-Punkt. Nächster Schritt
  wäre ein clientseitiger Chunk-Pager oder eine Einschränkung auf Kategorien.
- Autoplay im Hero nach 3,6 s kostet zusätzlich Netzwerk/Decoder. Auf einem
  1-GB-Stick in den Einstellungen ausschalten.
- Debug-Builds sind größer/langsamer als Release-Builds. Für den täglichen
  Gebrauch später `assembleRelease` mit R8 und eigenem Keystore verwenden.

## EncryptedSharedPreferences auf API 21/22

`androidx.security:security-crypto:1.1.0-alpha06` unterstützt offiziell API 21.
In der Praxis kann ein alter Vendor-Keystore trotzdem fehlschlagen. Die App
fängt das ab und verwendet dann eine private, nur obfuskierte Datei. Die
Einstellungen zeigen in diesem Fall „Kompatibilitätsmodus“. Die Zielgeräte
(Android TV 11, Fire OS 7/8) sind davon nicht betroffen.

## SurfaceView überdeckt Compose

Auf dem waipu-Stick lag die Standard-`SurfaceView` von `PlayerView` über der
Compose-Oberfläche; der Screenshot war während der Hero-Vorschau komplett weiß.
Der Player wird deshalb über
`core-player/src/main/res/layout/view_kisium_player.xml` mit
`app:surface_type="texture_view"` inflatet. TextureView wird in die normale
View-Hierarchie komponiert; Hero-Texte, Fokusrahmen und Player-Overlays bleiben
sichtbar. Wenn du den Player umbaust, diese Einstellung beibehalten.

## Hintergrundwiedergabe

Aktuell **nicht** implementiert:

- `ON_STOP` pausiert den Player.
- Beim Verlassen des Player-Composables wird ExoPlayer freigegeben.
- Es gibt keinen Foreground-Service und keine MediaSession.

Das ist für einen TV-Player meist gewünscht und spart RAM. Wenn du Musik/Radio im
Hintergrund willst, brauchst du einen `MediaSessionService` mit
`FOREGROUND_SERVICE_MEDIA_PLAYBACK`, Notification-Channel und
`android:foregroundServiceType`. Auf Fire OS zusätzlich die
Background-Start-Regeln beachten.

## waipu.tv-Launcher

Der waipu-Stick nutzt die waipu-App als Home. Sideloaded Apps erscheinen dort
nicht zwangsläufig als Kachel. Das ist kein Manifest-Fehler. Starte die App per
`adb shell am start -n de.kisium.tv/.MainActivity` oder installiere einen
separaten TV-Launcher. Der `LEANBACK_LAUNCHER`-Intent und das Banner sind
vorhanden, damit Fire TV und Standard-Android-TV-Launcher die App korrekt
aufnehmen.

## Fire-TV-Homescreen / Banner

- Banner: `320 × 180` px (16:9) ist die offizielle Empfehlung; die App liefert
  320 × 180 in `drawable-xhdpi` und skaliert auf TV-Dichte.
- Zusätzlich sind `LEANBACK_LAUNCHER` und `LAUNCHER` als getrennte Intent-Filter
  gesetzt.
- Wenn die App nach der Installation nicht im Home erscheint: Fire TV neu
  starten, „Deine Apps & Kanäle“ prüfen oder per adb starten.
- Kein `android.software.leanback required="true"` – sonst wäre die APK nicht auf
  Fire-OS-Geräten ohne Leanback-Feature installierbar. Deshalb steht
  `required="false"` im Manifest.

## ADB / Sideload

- Stick und Rechner müssen im selben Subnetz sein; Gäste-WLAN/AP-Isolation
  blockiert Port 5555.
- `adb devices` muss `device` zeigen. `unauthorized` → Dialog am TV bestätigen.
- Bei Verbindungsproblemen:
  ```bash
  adb kill-server
  adb start-server
  adb connect <IP>:5555
  ```
- Nach einem Firmware-Update kann der Debug-Modus aus sein.
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE` → alte App mit anderem Key deinstallieren.
- `INSTALL_FAILED_INSUFFICIENT_STORAGE` → Cache/Apps löschen; 8-GB-Sticks haben
  oft nur wenige GB frei.

## Speicherlimits

| Gerät | Speicher | Praxis |
|---|---|---|
| waipu.tv Stick 1. Gen | 8 GB | APK + Cache + Apps; ca. 4–5 GB frei |
| Fire TV Stick HD | 8 GB | ähnlich; Debug-APK nicht zu oft versionieren |
| Fire TV Stick 4K Max 2. Gen | 16 GB | komfortabler |
| Fire TV Stick 4K Plus | 16 GB | komfortabler |

Die App hält den Disk-Cache bei 128 MB und den Memory-Cache bei 32 MB. Bei
Speicherdruck kannst du in den Einstellungen „Cache leeren“.

## Xtream-Eigenheiten

- EPG-Titel und -Beschreibungen sind häufig **Base64-kodiert**. Die App dekodiert
  sie und fällt bei nicht-druckbarem Ergebnis auf den Rohstring zurück.
- `get_short_epg` liefert nicht bei jedem Panel Daten. Dann bleibt Now/Next leer,
  die Wiedergabe funktioniert weiter.
- `category_id` kann fehlen oder numerisch sein; die App behandelt sie als String
  und überspringt Einträge ohne ID.
- `xmltv.php` kann mehrere hundert MB groß sein. Der Parser streamt, wird aber
  standardmäßig nicht automatisch geladen.
- Manche Panels liefern bei falschem Login HTTP 200 mit `user_info.auth = 0`.
  Deshalb prüft `testConnection()` beides: `auth` und `status`.
- Zugangsdaten stehen bei Xtream in der Stream-URL. Das ist bauartbedingt; die
  App loggt sie nicht, aber Netzwerk-Mitschnitte/Server-Logs können sie sehen.
  Für den privaten Gebrauch im eigenen LAN ist das üblich; ein VPN/HTTPS-Panel
  ist besser.

## Rechtliche/technische Grenze

Disney+ und andere DRM-Dienste bleiben externe Apps. Das ist eine technische
Aussage, keine Einschränkung der Xtream-Funktionen: deine eigenen Xtream-Streams
werden direkt in Kisium TV abgespielt.
