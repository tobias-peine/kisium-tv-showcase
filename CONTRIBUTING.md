# Contributing

Danke für dein Interesse an Kisium TV.

## Grundsätze

- Keine Scraper für DRM-/Account-Dienste und keine Umgehung von Login, Captcha oder Bot-Schutz.
- Keine Zugangsdaten, Tokens oder persönlichen Stream-URLs committen.
- Neue Quellen müssen öffentlich, legal und ohne Umgehungsschutz zugänglich sein.
- Änderungen an der App müssen ohne Google Play Services lauffähig bleiben.

## Entwicklung

```bash
# Android
./scripts/generate-debug-keystore.sh
./gradlew :app-tv:assembleDebug
./gradlew :source-catalog-json:testDebugUnitTest

# Catalog-Sync
cd catalog-sync
python -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt -r requirements-dev.txt
pytest -q

# Stream-Proxy
cd ../stream-proxy
python -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt -r requirements-dev.txt
pytest -q
```

## Pull Requests

- Klein und thematisch fokussiert halten.
- Tests für neue Logik ergänzen.
- Keine generierten Dateien (`build/`, `data/`, APKs, Screenshots) committen.
- `.env` bleibt lokal; dokumentiere neue Variablen in `.env.example`.
