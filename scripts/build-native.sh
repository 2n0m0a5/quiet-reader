#!/usr/bin/env bash
# Builds the native Android app (native/, Kotlin + Compose), checks its app ID, version and
# signing key, and copies it to dist-apk/Quiet-<version>-<layer>.apk. See docs/plan-native.md.
#   bash scripts/build-native.sh          release APK only
#   bash scripts/build-native.sh test     JVM tests + screenshots first (native/app/build/outputs/roborazzi/)
set -euo pipefail
cd "$(dirname "$0")/../native"
SDK=/home/user/android-sdk
BT=$SDK/build-tools/36.0.0
OUT=../dist-apk
export ANDROID_HOME=$SDK
[ -f local.properties ] || echo "sdk.dir=$SDK" > local.properties

if [ "${1:-}" = test ]; then ./gradlew recordRoborazziDebug --no-daemon -q; echo "Tests passed; screenshots in native/app/build/outputs/roborazzi/"; fi
./gradlew assembleRelease --no-daemon -q
APK=app/build/outputs/apk/release/app-release.apk

BADGING=$("$BT/aapt2" dump badging "$APK" | sed -n 1p)
SIGNER=$("$BT/apksigner" verify --print-certs "$APK" 2>/dev/null | awk '/SHA-256/ {print $NF; exit}')
NAME=$(echo "$BADGING" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p")
echo "$BADGING" | grep -q "name='app.quietreader.n'" || { echo "Unexpected app ID: $BADGING"; exit 1; }
[[ $SIGNER == 305b375e* ]] || { echo "Unexpected signing key: $SIGNER"; exit 1; }

mkdir -p "$OUT"
FILE="$OUT/Quiet-${NAME// /-}.apk"
cp "$APK" "$FILE"
echo "dist-apk/$(basename "$FILE")  ($(du -h "$FILE" | cut -f1), $(echo "$BADGING" | grep -o "versionCode='[0-9]*'"), signer ${SIGNER:0:8})"
