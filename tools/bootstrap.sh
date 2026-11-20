#!/bin/bash
# Sets up everything needed to build Glass Chat in a fresh Linux workspace:
#   1. Go >= 1.26 (built from GitHub source if the installed Go is older)
#   2. Go module sources, cloned from GitHub into ./deps/mods (no Go proxy needed)
#   3. gomobile/gobind
#   4. Android SDK command-line tools, platform 36, build-tools, NDK
# Usage: bash tools/bootstrap.sh   (from the project root)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEPS="$ROOT/deps"
mkdir -p "$DEPS/mods"

# --- 1. Go ------------------------------------------------------------------
need_go=1
if command -v go >/dev/null; then
  v=$(go env GOVERSION | sed 's/go//' | cut -d. -f2)
  [ "${v:-0}" -ge 26 ] && need_go=0
fi
if [ -x "$DEPS/goroot/bin/go" ]; then need_go=0; fi
if [ $need_go = 1 ]; then
  echo "== building Go 1.27.3 from source"
  git clone -q --depth 1 --branch go1.27.3 https://github.com/golang/go "$DEPS/goroot"
  (cd "$DEPS/goroot/src" && GOROOT_BOOTSTRAP="$(go env GOROOT)" ./make.bash >/dev/null)
fi
[ -x "$DEPS/goroot/bin/go" ] && export PATH="$DEPS/goroot/bin:$PATH"
go version

# --- 2. Go module sources ----------------------------------------------------
while read -r mod repo ref; do
  [ -z "${mod:-}" ] && continue
  dest="$DEPS/mods/$mod"
  [ -e "$dest/go.mod" ] && continue
  mkdir -p "$(dirname "$dest")"
  if [ "$ref" = "HEAD" ]; then
    git clone -q --depth 1 "https://github.com/$repo" "$dest"
  elif [[ "$ref" =~ ^[0-9a-f]{40}$ ]]; then
    git init -q "$dest"
    git -C "$dest" fetch -q --depth 1 "https://github.com/$repo" "$ref"
    git -C "$dest" checkout -q FETCH_HEAD
  else
    git clone -q --depth 1 --branch "$ref" "https://github.com/$repo" "$dest"
  fi
  echo "   $mod"
done < "$ROOT/tools/go-deps.txt"

# --- 3. gomobile -------------------------------------------------------------
export GOTOOLCHAIN=local GOPROXY=off GOFLAGS=-mod=mod GOSUMDB=off GOWORK=off
mkdir -p "$DEPS/bin"
(cd "$ROOT/bridge" && go build -o "$DEPS/bin/gomobile" golang.org/x/mobile/cmd/gomobile && go build -o "$DEPS/bin/gobind" golang.org/x/mobile/cmd/gobind)
export PATH="$DEPS/bin:$PATH"

# --- 4. Android SDK ----------------------------------------------------------
export ANDROID_HOME="${ANDROID_HOME:-$DEPS/android-sdk}"
if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "== downloading Android command-line tools"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  curl -sSfL -o /tmp/cmdtools.zip "https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"
  unzip -q /tmp/cmdtools.zip -d "$ANDROID_HOME/cmdline-tools"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null || true
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --install "platforms;android-36" "build-tools;36.0.0" "platform-tools" "ndk;27.2.12479018" >/dev/null
echo "sdk.dir=$ANDROID_HOME" > "$ROOT/android/local.properties"

cat <<MSG
== ready. Build with:
export PATH="$DEPS/goroot/bin:$DEPS/bin:\$PATH" ANDROID_HOME="$ANDROID_HOME" ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.2.12479018"
export GOTOOLCHAIN=local GOPROXY=off GOFLAGS=-mod=mod GOSUMDB=off GOWORK=off
bash tools/build.sh
MSG
