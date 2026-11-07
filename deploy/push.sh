#!/usr/bin/env bash
# Local helper: sync the project to an Ubuntu server and run deploy/install.sh.
set -euo pipefail

if [ "${1:-}" = "" ]; then
  echo "Aufruf: ./deploy/push.sh user@server [/opt/kisium-tv]" >&2
  exit 1
fi

REMOTE="$1"
REMOTE_DIR="${2:-/opt/kisium-tv}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

command -v rsync >/dev/null || { echo "FEHLER: rsync fehlt." >&2; exit 1; }
command -v ssh >/dev/null || { echo "FEHLER: ssh fehlt." >&2; exit 1; }

ssh "$REMOTE" "mkdir -p '$REMOTE_DIR'"
rsync -az --delete \
  --exclude '.git/' \
  --exclude '.env' \
  --exclude 'data/' \
  --exclude '.gradle/' \
  --exclude '.gradle-home/' \
  --exclude 'build/' \
  --exclude '*/build/' \
  --exclude 'local.properties' \
  --exclude '.idea/' \
  --exclude 'keystore/' \
  --exclude 'screenshots/' \
  --exclude '__pycache__/' \
  --exclude '.pytest_cache/' \
  "$ROOT/" "$REMOTE:$REMOTE_DIR/"

ssh "$REMOTE" "cd '$REMOTE_DIR' && chmod +x deploy/install.sh && ./deploy/install.sh"
