# Kisium TV auf dem Stick installieren

## 1. Bauen

```bash
cd kisium-tv
export ANDROID_HOME="$HOME/android-sdk"
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
./gradlew :app-tv:assembleDebug
```

APK:

```text
app-tv/build/outputs/apk/debug/app-tv-debug.apk
```

Der Debug-Build ist mit `keystore/kisium-debug.keystore` signiert. Wenn der Key
fehlt:

```bash
./scripts/generate-debug-keystore.sh
```

Prüfen, ob die TV-Metadaten in der APK stecken:

```bash
$ANDROID_HOME/build-tools/34.0.0/aapt dump badging \
  app-tv/build/outputs/apk/debug/app-tv-debug.apk | grep -E \
  "package:|application-label:|launchable-activity:|leanback|banner"
```

Erwartet werden unter anderem:

```text
package: name='de.kisium.tv'
application-label:'Kisium TV'
launchable-activity: name='de.kisium.tv.MainActivity'
uses-feature: name='android.software.leanback'
```

## 2. waipu.tv Stick

### Debugging einschalten

1. Stick und Rechner ins **gleiche WLAN** bringen. Gastnetz/AP-Isolation
   vermeiden.
2. Am Stick: **Einstellungen → System → Info** (je nach Firmware
   „Geräte-Einstellungen → Info“) und siebenmal auf **Build** tippen.
3. **Einstellungen → System → Entwickleroptionen** öffnen.
4. **USB-Debugging** bzw. **Netzwerk-Debugging** aktivieren.
5. IP-Adresse des Sticks notieren
   (Einstellungen → Netzwerk & Internet → WLAN → Netzwerk).

### Verbinden und installieren

```bash
adb connect 192.168.1.50:5555
adb devices
# muss "device" zeigen, nicht "unauthorized"

adb install -r app-tv/build/outputs/apk/debug/app-tv-debug.apk
adb shell monkey -p de.kisium.tv \
  -c android.intent.category.LEANBACK_LAUNCHER 1
```

Beim ersten Verbinden erscheint am TV ein Autorisierungsdialog. Erst nach
„Zulassen“ ist `adb devices` bereit.

### Waipu-Launcher prüfen

Der waipu.tv-Stick verwendet die waipu-App als Home-Oberfläche. Je nach
Firmware werden sideloaded Apps dort **nicht** als Kachel angezeigt – das ist
eine Launcher-Einschränkung, kein Fehler der APK. Der zuverlässige Start ist:

```bash
adb shell am start -n de.kisium.tv/.MainActivity
```

Wenn die App im Home erscheinen soll, hilft ein separater Android-TV-Launcher
(z. B. ATV Launcher) oder ein Firmware-Stand, der sideloaded Leanback-Apps
listet. Der `LEANBACK_LAUNCHER`-Intent und das Banner sind in der APK korrekt
gesetzt.

### Installation prüfen

```bash
adb shell pm list packages | grep de.kisium.tv
adb shell dumpsys package de.kisium.tv | grep -i -E "versionName|leanback|banner"
adb shell screencap -p /sdcard/kisium.png
adb pull /sdcard/kisium.png .
```

## 3. Amazon Fire TV Stick

### Fire OS vorbereiten

1. **Einstellungen → My Fire TV → Info** öffnen.
2. Siebenmal auf den Gerätenamen tippen, bis die Entwickleroptionen freigeschaltet
   sind.
3. **Einstellungen → My Fire TV → Entwickleroptionen → ADB-Debugging** auf
   **Ein** stellen.
4. Für die Downloader-App zusätzlich **Apps aus unbekannten Quellen** für
   Downloader erlauben.

### Per adb installieren

```bash
adb connect 192.168.1.60:5555
adb devices
adb install -r app-tv/build/outputs/apk/debug/app-tv-debug.apk
adb shell am start -n de.kisium.tv/.MainActivity
```

Fire TV kann beim ersten `adb connect` einen Dialog zur Autorisierung zeigen.
Bei „App nicht installiert“ zuerst Speicherplatz prüfen:

```bash
adb shell df -h /data
adb shell pm uninstall de.kisium.tv   # nur bei Bedarf
```

### Per Downloader installieren

1. Die APK im LAN bereitstellen, z. B. `python3 -m http.server 8000` im
   `app-tv/build/outputs/apk/debug/`-Verzeichnis.
2. Downloader auf dem Fire TV installieren.
3. Im Downloader die URL `http://192.168.1.20:8000/app-tv-debug.apk` öffnen.
4. Installation bestätigen.

Der Fire-TV-Homescreen nutzt den Leanback-Banner aus
`app-tv/src/main/res/drawable-nodpi/tv_banner.png` und den
`LEANBACK_LAUNCHER`-Intent. Sideloaded Apps erscheinen je nach Fire-OS-Version
unter „Deine Apps & Kanäle“ oder in der zuletzt verwendeten Zeile.

### Wichtig: Vega OS

Die 2025/2026 erschienenen **Fire TV Stick 4K Select** und **Fire TV Stick 4K
(3. Gen)** laufen mit **Vega OS 2.0**, nicht mit Fire OS/Android. Dort lässt sich
keine APK installieren. Unterstützt sind Fire-OS-7/8-Geräte (Android 9/11).

## 4. Laufzeit prüfen

```bash
adb logcat -c
adb logcat | grep -E "AndroidRuntime|Kisium|ExoPlayer|MediaCodec"
```

Typische Checks:

- App startet im Querformat, Splash und Home erscheinen.
- D-Pad: Fokus wandert, OK öffnet Karten, Zurück führt zurück.
- Einstellungen: Server/User/Passwort speichern, Verbindung testen.
- Live-Kanal: HLS startet; bei Fehler wird automatisch TS versucht.
- Im Player: EPG Now/Next sichtbar, CH+/CH− wechselt Sender.
- Disney+-Kachel: installierte App wird geöffnet, sonst Hinweisdialog.
- Nach App-Wechsel: keine Hintergrundwiedergabe (gewollt), Player wird freigegeben.

## 5. Deinstallieren

```bash
adb uninstall de.kisium.tv
```

## 6. Signatur / Updates

Die Debug-APK ist mit dem lokalen Key signiert. Für ein Update muss dieselbe
Signatur verwendet werden. `adb install -r` behält Daten und Einstellungen.
Wenn du den Key verlierst, musst du die App deinstallieren; Einstellungen und
Watch-History gehen dabei verloren.
