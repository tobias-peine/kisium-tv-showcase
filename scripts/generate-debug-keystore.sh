#!/usr/bin/env bash
# Regenerates the local debug signing key used by :app-tv.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p keystore
keytool -genkeypair \
  -keystore keystore/kisium-debug.keystore \
  -storepass android \
  -keypass android \
  -alias androiddebugkey \
  -dname "CN=Kisium TV Debug, O=Kisium, C=DE" \
  -keyalg RSA -keysize 2048 -validity 10000
echo "Created keystore/kisium-debug.keystore"
