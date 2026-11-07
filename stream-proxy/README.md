# Kisium Stream Proxy + Admin

Leichtgewichtiger FastAPI-Redirect-Dienst für **direkte Netzkino-VOD-
MP4-URLs**. Der Proxy schlägt die `stream_id` in `catalog.sqlite` nach und
antwortet mit HTTP 302 auf die dort gespeicherte `source_url`.

Kein Hoster-Resolving, kein Scraping und kein DRM-Bypass: Netzkino liefert direkte, frei abspielbare MP4-Streams.

## Endpunkte

| Methode | Pfad | Zweck |
|---|---|---|
| `GET` | `/movie/{username}/{password}/{stream_id}.{ext}` | 302-Redirect zur `source_url` |
| `GET` | `/catalog.json` | `catalog.json` aus dem Daten-Volume (ohne Auth) |
| `GET` | `/healthz` | DB-/Service-Healthcheck |
| `GET` | `/admin` | Admin-GUI (Basic Auth + CSRF) |
| `POST` | `/admin/add` | manuelle Quell-URL hinzufügen |
| `POST` | `/admin/delete/{stream_id}` | manuellen Eintrag löschen |

## Ablauf

```text
Kisium TV
   │  /movie/<proxy-user>/<proxy-pass>/<stream_id>.mp4
   ▼
FastAPI Stream Proxy
   ├─ prüft Proxy-Credentials (env oder Pass-Through)
   ├─ liest stream_id
   ├─ SELECT source_url FROM movies / manual_movies
   └─ HTTP 302 Location: source_url
          │
          ▼
Player lädt direkt vom Netzkino-MP4-Host
```

Der Proxy sieht keine Videobytes. Er benötigt keine Provider-Logins mehr;
`PROXY_USERNAME`/`PROXY_PASSWORD` schützen nur den Proxy selbst.

## SQLite / WAL

- `catalog.sqlite` wird für Stream-Lookups mit `file:...?mode=ro` geöffnet.
- `catalog-sync` schreibt `movies` inklusive Netzkino-`source_url`.
- `manual_movies` gehört zur Admin-GUI und überlebt den Sync.
- Admin-Schreibvorgänge nutzen WAL, `BEGIN IMMEDIATE` und
  `busy_timeout=10000`.
- Der Proxy-Container braucht `./data:/data` **rw**, weil die Admin-GUI
  schreiben muss. Die Stream-Abfragen bleiben SQLite-`mode=ro`.
- `source_url` ist für VOD-Filme Pflicht. Fehlt sie, antwortet der Proxy mit
  404.

## Konfiguration

| Variable | Default | Bedeutung |
|---|---|---|
| `PROXY_HOST` | `0.0.0.0` | Bind-Adresse im Container |
| `PROXY_PORT` | `5000` | Port |
| `PROXY_BIND` | `0.0.0.0` | Host-Bind in Compose |
| `PROXY_AUTH_MODE` | `env` | `env` = prüfen, `off` = Pass-Through |
| `PROXY_USERNAME` / `PROXY_PASSWORD` | – | Proxy-Login |
| `SQLITE_DB_PATH` | `/data/catalog.sqlite` | DB-Pfad |
| `CATALOG_JSON_PATH` | `/data/catalog.json` | Katalog für die App |
| `CATALOG_CACHE_SECONDS` | `60` | Cache-TTL für `/catalog.json` |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | – | Admin-Login; ohne Werte ist `/admin` deaktiviert |
| `ADMIN_COOKIE_SECURE` | `false` | `true` bei ausschließlichem HTTPS |
| `WEB_CONCURRENCY` | `2` | Uvicorn-Worker |
| `UVICORN_LOG_LEVEL` | `info` | Uvicorn-Loglevel |
| `FORWARDED_ALLOW_IPS` | `127.0.0.1` | vertrauenswürdige Reverse-Proxys |

## Start

Aus der Projektwurzel:

```bash
cd kisium-tv
cp .env.example .env
chmod 600 .env
# VOD_FEED_URL, PROXY_* und ADMIN_* eintragen

mkdir -p data
docker compose up -d --build
docker compose logs -f stream-proxy
```

Server-Deployment inklusive Autostart, Logrotation und TLS-Optionen:
[`docs/DEPLOYMENT.md`](../docs/DEPLOYMENT.md).

Ohne Compose-Plugin:

```bash
cd kisium-tv
docker build -t kisium-catalog-sync:local ./catalog-sync
docker build -t kisium-stream-proxy:local ./stream-proxy

docker run -d --name kisium-stream-proxy \
  --user "$(id -u):$(id -g)" \
  --env-file .env \
  -p 5000:5000 \
  -v "$PWD/data:/data" \
  kisium-stream-proxy:local
```

## App-Konfiguration

Für Netzkino-Filme über den Proxy:

1. In Kisium TV als **Server-URL** den Proxy eintragen:
   ```text
   https://your-proxy.example
   ```
2. Proxy-Benutzer/Passwort als Xtream-Zugangsdaten verwenden.
3. **JSON-Katalog** aktivieren; standardmäßig ist
   `https://your-proxy.example/catalog.json` eingetragen.
4. Demo-Modus aus, speichern.

Die App baut Wiedergabe-URLs als
`http://proxy:5000/movie/<user>/<pass>/<stream_id>.<ext>` und der Proxy
antwortet mit dem 302 zur Netzkino-`source_url`.

Der Proxy implementiert nur den `/movie/...`-Pfad, nicht `player_api.php`.
Für die JSON-Filmbibliothek ist das ausreichend; Live-TV/Serien sind in der
Netzkino-VOD-Katalog nicht als separate App-Bereiche modelliert.

## Admin-GUI

`http://<proxy-host>:5000/admin` (Basic Auth).

- Quell-URL eingeben → `add_movie_to_db(source_url)`
- Titel wird aus dem Dateinamen abgeleitet
- stream_id eindeutig ab `2.000.000.000`
- Tabelle zeigt Netzkino-Sync-Filme (schreibgeschützt) und manuelle Filme
- manuelle Einträge haben einen Löschen-Button
- CSRF über HttpOnly-Cookie + Formular-Token
- CSP, `X-Frame-Options: DENY`, `no-store`

Netzkino-Einträge werden beim nächsten Sync ersetzt und sind deshalb nicht
administrativ löschbar. Manuelle Einträge bleiben erhalten.

## Produktionsbetrieb

Das Runtime-Image startet **Uvicorn mit mehreren Workern**:

```text
uvicorn stream_proxy.asgi:app --workers ${WEB_CONCURRENCY:-2}
```

Der Uvicorn-Access-Log ist deaktiviert, weil der `/movie/...`-Pfad
Benutzername und Passwort enthält. Die Anwendung schreibt strukturierte
JSON-Events (`stream_redirect`, `manual_movie_added`) nach stdout. Docker
rotiert diese Logs über `max-size: 10m`, `max-file: 3`:

```bash
docker compose logs -f --tail=100 stream-proxy
```

Alle Dienste haben `restart: unless-stopped`; bei aktiviertem Docker-Dienst
starten sie nach einem Server-Neustart automatisch.

## Sicherheit

- Admin nur mit gesetzten `ADMIN_*`-Werten; starkes Passwort.
- Basic Auth nur über LAN oder HTTPS-Reverse-Proxy.
- `.env` mit `chmod 600`.
- Keine Zugangsdaten in JSON/SQLite außer der Netzkino-`source_url`.
- Access-Log aus, damit Pfad-Credentials nicht in Logs landen.
- `source_url` wird im Admin-HTML escaped.
- Port 5000 nur ins eigene LAN veröffentlichen.

## Tests

```bash
cd stream-proxy
docker build --target test -t kisium-stream-proxy:test .
docker run --rm kisium-stream-proxy:test
```

Aktuell **20 Tests**:

- 302 aus DB-`source_url`
- `/catalog.json` wird ausgeliefert und 60 s gecacht
- ETag/304 und Cache-Refresh
- fehlende `source_url` → 404
- unbekannte stream_id → 404
- falsche Credentials → 401
- DB nicht verfügbar → 503
- Admin Basic Auth, CSRF, Add/List/Delete
- Duplikat-URL
- manueller Stream über den Proxy
- Read-only-SQLite/WAL

Zusätzlich wurde der Container end-to-end mit WAL-DB getestet: Healthcheck,
302-Redirect und Admin-Add funktionierten.
