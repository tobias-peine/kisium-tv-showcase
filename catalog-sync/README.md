# Kisium Netzkino VOD Sync

Docker-Sidecar für den **kostenlosen Netzkino-VOD-Katalog**. Die offizielle
GraphQL-API liefert echte Spielfilme mit Cover, Widescreen-Bild, Beschreibung,
Jahr, Genre und direkter MP4-URL.

Keine Xtream-API, keine M3U-Live-Sender, keine Scraper, keine DRM-Inhalte.

## Pipeline

```text
VOD_FEED_URL (Netzkino GraphQL)
   │  POST, Retry/Backoff, Rate-Limit
   ▼
NetzkinoParser
   ├─ allCmsMovies, isStreamable=true, hasDrm=false
   ├─ title, plot, year, rating, genre
   ├─ coverImage + widescreenImage
   └─ videoSource.pmdUrl → https://pmd.netzkino-seite.netzkino.de/...
        │
        ▼
KisiumCatalogSource
   ├─ GraphQL-Pagination (Cursor)
   ├─ stabile stream_id = SHA-256(Netzkino-ID)
   └─ /data/catalog.json + /data/catalog.sqlite
```

## Schnellstart

```bash
cd kisium-tv
cp .env.example .env
chmod 600 .env
# VOD_FEED_URL, PROXY_* und ADMIN_* eintragen
mkdir -p data
docker compose up -d --build
docker compose logs -f catalog-sync
```

Einmalig:

```bash
docker run --rm \
  --user "$(id -u):$(id -g)" \
  --env-file .env \
  -v "$PWD/data:/data" \
  kisium-catalog-sync:local --once
```

## Konfiguration

| Variable | Default | Bedeutung |
|---|---|---|
| `VOD_FEED_URL` | – | `https://data.netzkino.de/netzkino/graphql` |
| `VOD_FETCH_PAGE_SIZE` | `100` | GraphQL-Seitengröße |
| `VOD_MAX_PAGES` | `100` | Sicherheitslimit gegen Endlosschleifen |
| `PAGE_SIZE` | `100` | lokale Seitengröße im JSON |
| `SYNC_INTERVAL_SECONDS` | `21600` | Loop-Intervall |
| `OUTPUT_DIR` | `/data` | Zielverzeichnis |
| `OUTPUT_FORMAT` | `both` | `json`, `sqlite`, `both` |
| `REQUEST_TIMEOUT_SECONDS` | `30` | Read-Timeout |
| `CONNECT_TIMEOUT_SECONDS` | `10` | Connect-Timeout |
| `MAX_RETRIES` | `5` | Retries bei transienten Fehlern |
| `MIN_REQUEST_INTERVAL_SECONDS` | `0.4` | Mindestabstand |
| `VERIFY_TLS` | `true` | TLS prüfen |
| `HEADER_ROTATION` | `true` | Client-Profile pro Retry |
| `LOG_LEVEL` | `INFO` | JSON-Logs |

Beispiel:

```dotenv
VOD_FEED_URL=https://data.netzkino.de/netzkino/graphql
```

## Netzkino-API

Abfrage (gekürzt):

```graphql
query KisiumMovies($first: Int!, $after: Cursor) {
  allCmsMovies(
    first: $first
    after: $after
    filter: {isStreamable: {equalTo: true}, hasDrm: {equalTo: false}}
    orderBy: [TITLE_ASC]
  ) {
    totalCount
    pageInfo { hasNextPage endCursor }
    nodes {
      id title longSynopsis mediumSynopsis shortSynopsis
      productionYear rating runtimeInSeconds
      coverImage { masterUrl }
      widescreenImage { masterUrl }
      videoSource { pmdUrl hlsUrl dashUrl }
      cmsMovieContentCategoriesByContentMovieId(first: 5) {
        nodes { category { title slug } }
      }
    }
  }
}
```

Die API ist öffentlich erreichbar; es ist kein API-Key nötig. Der Sync sendet
die üblichen `Origin`-/`Referer`-Header der Netzkino-Website.

## Datenfelder

`catalog.json` enthält:

```json
{
  "title": "100.000 verdammte Dollar",
  "category_name": "Actionkino",
  "image_url": "https://.../cover.jpg",
  "backdrop_url": "https://.../wide.jpg",
  "plot": "New Mexico, staubig und gesetzlos ...",
  "year": 1967,
  "rating": null,
  "container_extension": "mp4",
  "is_logo": false
}
```

`source_url` wird **nicht** in `catalog.json` veröffentlicht, sondern nur in
SQLite für den 302-Proxy gespeichert.

## Tests

```bash
cd catalog-sync
docker build --target test -t kisium-catalog-sync:test .
docker run --rm kisium-catalog-sync:test
```

Aktuell **14 Tests**: Netzkino-Mapping, DASH-Filter, stabile IDs, Pagination,
Cache, JSON-Vertrag, GraphQL-Fehler, Config, Retry, Header-Rotation und SQLite.

Live getestet: **2.186 Filme** aus 22 GraphQL-Seiten.

## Stream-Proxy und Deployment

Der Proxy [`stream-proxy`](../stream-proxy/README.md) liest dieselbe
`catalog.sqlite` und leitet `/movie/...` per HTTP 302 auf die Netzkino-MP4-URL
weiter.

Deployment: [`docs/DEPLOYMENT.md`](../docs/DEPLOYMENT.md).
