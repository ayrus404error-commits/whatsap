#!/bin/bash
# Builds the Go engine into an Android library, then the signed release APK.
# Run tools/bootstrap.sh once first and export the variables it prints.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/bridge"
# gomobile works from a temp dir, so point the local replaces at absolute
# paths for the duration of the build, then put go.mod back as it was.
cp go.mod "$ROOT/deps/go.mod.bak"
trap 'cp "$ROOT/deps/go.mod.bak" "$ROOT/bridge/go.mod"' EXIT
sed -i "s|=> \.\./deps/|=> $ROOT/deps/|" go.mod
go test -count=1 .
mkdir -p "$ROOT/android/app/libs"
gomobile bind -target=android/arm64 -androidapi 29 -javapkg com.glasschat.go -ldflags="-s -w" -o "$ROOT/android/app/libs/wa.aar" .
cd "$ROOT/android"
gradle assembleRelease --no-daemon --warning-mode=none
ls -la app/build/outputs/apk/release/
