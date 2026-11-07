#!/usr/bin/env bash
# Server-side installer/updater. Run from the project root or via deploy/push.sh.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if ! command -v docker >/dev/null 2>&1; then
  echo "FEHLER: Docker ist nicht installiert." >&2
  exit 1
fi
if ! docker compose version >/dev/null 2>&1; then
  echo "FEHLER: Docker-Compose-Plugin fehlt (docker compose)." >&2
  exit 1
fi

if [ ! -f .env ]; then
  cp .env.example .env
  chmod 600 .env
  echo "Es wurde .env aus .env.example erzeugt."
  echo "Bitte jetzt VOD_FEED_URL, PROXY_* und ADMIN_* eintragen und erneut starten:"
  echo "  nano .env"
  echo "  ./deploy/install.sh"
  exit 1
fi

if grep -Eq 'dein_(user|passwort)|bitte_langes_passwort_setzen|VOD_FEED_URL=https://example\.com' .env; then
  echo "FEHLER: .env enthält noch Platzhalter. Bitte alle Secrets ersetzen." >&2
  exit 1
fi

for key in VOD_FEED_URL PROXY_PORT PROXY_USERNAME PROXY_PASSWORD ADMIN_USERNAME ADMIN_PASSWORD; do
  if ! grep -Eq "^${key}=.+" .env; then
    echo "FEHLER: ${key} fehlt in .env." >&2
    exit 1
  fi
done

chmod 600 .env
mkdir -p data
chmod 700 data
export KISIUM_UID="${KISIUM_UID:-$(id -u)}"
export KISIUM_GID="${KISIUM_GID:-$(id -g)}"

echo "Starte Kisium TV Stack (UID ${KISIUM_UID}:${KISIUM_GID}) ..."
docker compose config --quiet
docker compose up -d --build --remove-orphans

echo "Warte auf den Stream-Proxy-Healthcheck ..."
healthy=0
for _ in $(seq 1 90); do
  state="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' kisium-stream-proxy 2>/dev/null || true)"
  if [ "$state" = "healthy" ]; then
    healthy=1
    break
  fi
  sleep 2
done

docker compose ps
if [ "$healthy" -ne 1 ]; then
  echo "FEHLER: stream-proxy wurde nicht healthy. Logs:" >&2
  docker compose logs --tail=80 stream-proxy >&2
  exit 1
fi

PORT="$(grep -E '^PROXY_PORT=' .env | head -1 | cut -d= -f2 | tr -d '[:space:]')"
PORT="${PORT:-5000}"
echo
echo "Kisium Stack läuft."
echo "  Admin:   http://<SERVER-IP>:${PORT}/admin"
echo "  Health:  http://<SERVER-IP>:${PORT}/healthz"
echo "  Katalog: http://<SERVER-IP>:${PORT}/catalog.json"
echo "  Logs:   docker compose logs -f stream-proxy catalog-sync"
echo
echo "Autostart: Docker muss beim Booten aktiv sein:"
echo "  sudo systemctl enable --now docker"
