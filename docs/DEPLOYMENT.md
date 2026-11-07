# Server-Deployment (Ubuntu)

Der Stack besteht aus zwei Containern:

| Dienst | Aufgabe | Port |
|---|---|---|
| `kisium-catalog-sync` | Netzkino-VOD → `data/catalog.json` + `data/catalog.sqlite` | – |
| `kisium-stream-proxy` | 302-Redirects + Admin-GUI | `${PROXY_PORT:-5000}` |

Beide laufen mit `restart: unless-stopped`, nicht als root, mit read-only
Root-Dateisystem und Docker-Logrotation.

## 1. Server vorbereiten

```bash
sudo apt update
sudo apt install -y docker.io docker-compose-v2 rsync
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"
# einmal aus- und wieder einloggen, damit die Gruppe aktiv wird
docker compose version
```

Falls Ubuntu kein `docker-compose-v2` anbietet, die offizielle Docker-Engine
mit Compose-Plugin installieren. Der Stack braucht nur Docker + Compose v2.

## 2. Projekt übertragen

Vom lokalen Rechner:

```bash
cd /pfad/zu/kisium-tv
./deploy/push.sh user@server /opt/kisium-tv
```

Das Skript:

- legt `/opt/kisium-tv` an,
- überträgt das Projekt per `rsync`,
- schützt vorhandene `.env` und `data/` vor dem Löschen,
- ruft auf dem Server `deploy/install.sh` auf.

Beim ersten Lauf existiert noch keine `.env`. Der Installer erzeugt sie aus
`.env.example` und bricht mit einer Anleitung ab.

Alternativ manuell:

```bash
rsync -az --delete \
  --exclude '.env' --exclude 'data/' --exclude '.git/' \
  --exclude 'build/' --exclude '*/build/' --exclude '.gradle/' \
  ./ user@server:/opt/kisium-tv/
ssh user@server
cd /opt/kisium-tv
```

## 3. `.env` initialisieren

```bash
cd /opt/kisium-tv
cp .env.example .env
chmod 600 .env
nano .env
```

Pflichtwerte:

```dotenv
VOD_FEED_URL=https://data.netzkino.de/netzkino/graphql

PROXY_PORT=5000
PROXY_BIND=0.0.0.0
PROXY_AUTH_MODE=env
PROXY_USERNAME=...
PROXY_PASSWORD=...

ADMIN_USERNAME=...
ADMIN_PASSWORD=...   # langes, zufälliges Passwort
```

`ADMIN_PASSWORD` ist kein Default-Passwort. Ohne gesetzte Admin-Werte ist
`/admin` deaktiviert. Für den Zugriff aus dem LAN bleibt `PROXY_BIND=0.0.0.0`;
für einen vorgeschalteten Nginx-Reverse-Proxy auf `127.0.0.1` setzen.

## 4. Stack starten

```bash
./deploy/install.sh
```

Der Installer prüft Docker/Compose, ersetzt keine Secrets, setzt `KISIUM_UID`
auf den aktuellen Server-User, baut die Images und wartet auf den Healthcheck.

Manuell identisch:

```bash
export KISIUM_UID="$(id -u)" KISIUM_GID="$(id -g)"
docker compose up -d --build --remove-orphans
docker compose ps
```

## 5. Prüfen

```bash
curl -fsS http://127.0.0.1:5000/healthz
docker compose ps
docker compose logs --tail=50 stream-proxy
```

Im LAN:

```text
http://<SERVER-IP>:5000/healthz
http://<SERVER-IP>:5000/catalog.json
http://<SERVER-IP>:5000/admin
```

Die Admin-GUI fragt nach `ADMIN_USERNAME`/`ADMIN_PASSWORD`.

## 6. Autostart nach Server-Neustart

Die Container haben `restart: unless-stopped`. Damit sie nach einem Reboot
automatisch starten, muss der Docker-Daemon aktiviert sein:

```bash
sudo systemctl enable --now docker
docker compose ps
```

Ein eigener systemd-Service ist nicht nötig. Wenn Container manuell mit
`docker compose stop` gestoppt wurden, bleiben sie auch nach einem Reboot
gestoppt – das ist das erwartete Verhalten von `unless-stopped`.

## 7. Logs und Rotation

Docker rotiert pro Dienst:

```yaml
logging:
  driver: json-file
  options:
    max-size: "10m"
    max-file: "3"
```

Ansehen:

```bash
docker compose logs -f --tail=100 stream-proxy
docker compose logs -f --tail=100 catalog-sync
```

Der Uvicorn-Access-Log ist **deaktiviert**, weil der `/movie/...`-Pfad
Benutzername/Passwort enthält. Stattdessen schreibt die Anwendung
strukturierte JSON-Events wie `stream_redirect`, `stream_target_resolved`,
`manual_movie_added` und `sync_completed`. Diese Events enthalten keine
Passwörter.

## 8. Sicherheit

- **Admin-GUI:** Basic Auth + CSRF, standardmäßig nur mit gesetzten
  `ADMIN_*`-Werten aktiv. Starkes Passwort verwenden.
- **Transport:** Im reinen LAN ist HTTP üblich. Für Zugriff über das Internet
  einen TLS-Reverse-Proxy (Beispiel: [`deploy/nginx.conf.example`](../deploy/nginx.conf.example))
  oder WireGuard/Tailscale davor setzen. Dann `PROXY_BIND=127.0.0.1`.
- **Port 5000:** Nur ins eigene LAN veröffentlichen. Docker veröffentlicht
  Ports über iptables; eine reine UFW-Regel kann dadurch umgangen werden.
  Besser am Router/Firewall beschränken oder `PROXY_BIND` auf die LAN-IP
  setzen.
- **`.env`:** `chmod 600`, nie committen. Der Push-Sync schützt die
  Server-`.env` vor Überschreiben.
- **Daten:** `data/` enthält `catalog.sqlite` und ggf. manuelle Quell-URLs.
  Regelmäßig sichern:
  ```bash
  tar czf "kisium-data-$(date +%F).tar.gz" data
  ```

## 9. Update ausrollen

Lokal:

```bash
./deploy/push.sh user@server /opt/kisium-tv
```

Der Push aktualisiert den Code, lässt `.env` und `data/` unangetastet und
startet den Stack auf dem Server neu. Danach:

```bash
ssh user@server 'cd /opt/kisium-tv && docker compose ps'
```

## 10. Admin-Einträge und Katalog-Sync

- Der Sync ersetzt nur die Tabelle `movies`.
- Manuelle Admin-Einträge liegen in `manual_movies` und überleben den Sync.
- Der Proxy liest `catalog.sqlite` für Stream-Anfragen mit
  `file:...?mode=ro`.
- Die Admin-Schreibvorgänge nutzen WAL, `BEGIN IMMEDIATE` und
  `busy_timeout`.

## Dateien

- [`docker-compose.yml`](../docker-compose.yml)
- [`.env.example`](../.env.example)
- [`deploy/install.sh`](../deploy/install.sh)
- [`deploy/push.sh`](../deploy/push.sh)
- [`deploy/nginx.conf.example`](../deploy/nginx.conf.example)
- [`stream-proxy/README.md`](../stream-proxy/README.md)
