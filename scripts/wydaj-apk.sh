#!/usr/bin/env bash
# Buduje telefon-v{VERSION}.apk i (opcjonalnie) tworzy GitHub Release + update.json.
# Wymaga tego samego debug.keystore, którym podpisano wcześniejsze release’y.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION_NAME=$(rg -o 'versionName = "([^"]+)"' -r '$1' app/build.gradle.kts | head -1)
VERSION_CODE=$(rg -o 'versionCode = ([0-9]+)' -r '$1' app/build.gradle.kts | head -1)
TAG="v${VERSION_NAME}"
APK_NAME="telefon-${TAG}.apk"

export ANDROID_DEBUG_KEYSTORE="${ANDROID_DEBUG_KEYSTORE:-$HOME/.android/debug.keystore}"
if [[ ! -f "$ANDROID_DEBUG_KEYSTORE" ]]; then
  echo "Brak keystore: $ANDROID_DEBUG_KEYSTORE"
  echo "Ustaw ANDROID_DEBUG_KEYSTORE na plik, którym podpisano v1.7.6."
  exit 1
fi

./gradlew :app:assembleRelease
SRC="app/build/outputs/apk/release/app-release.apk"
if [[ ! -f "$SRC" ]]; then
  SRC="app/build/outputs/apk/release/app-release-unsigned.apk"
fi
mkdir -p dist
cp -f "$SRC" "dist/${APK_NAME}"
echo "APK: dist/${APK_NAME} (versionCode=${VERSION_CODE})"

if [[ "${1:-}" == "--release" ]]; then
  gh release create "$TAG" "dist/${APK_NAME}" \
    --title "$TAG" \
    --notes "$(python3 -c "import json; print(json.load(open('update.json')).get('changelog',''))")"
  echo "Release $TAG utworzony. Upewnij się, że update.json jest na main."
fi
