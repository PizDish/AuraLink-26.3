#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
javac -d "$TMP" "$ROOT/src/client/java/dev/auralink/client/config/BridgePairing.java" \
  "$ROOT/src/client/java/dev/auralink/client/config/PlaylistStore.java" \
  "$ROOT/src/client/java/dev/auralink/client/config/PlayerPreferences.java" \
  "$ROOT/src/client/java/dev/auralink/client/config/MetadataStore.java" \
  "$ROOT/tests/ConfigSmokeTest.java"
java -cp "$TMP" ConfigSmokeTest
