#!/usr/bin/env bash
# Builds the Android APK, checks its version and signing key, and copies it to Quiet-<version>.apk.
#   bash scripts/build-apk.sh [out_dir]      (default: dist-apk/, git-ignored)
set -euo pipefail
cd "$(dirname "$0")/.."
SDK=/home/user/android-sdk
BT=$SDK/build-tools/36.0.0
OUT=${1:-dist-apk}
VERSION=$(node -p "require('./package.json').version")

npm run android
(cd android && ANDROID_HOME=$SDK ./gradlew assembleDebug --no-daemon -q)
APK=android/app/build/outputs/apk/debug/app-debug.apk

# (Read whole outputs: an early-closing pipe would trip pipefail.)
BADGING=$("$BT/aapt2" dump badging "$APK" | sed -n 1p)
SIGNER=$("$BT/apksigner" verify --print-certs "$APK" 2>/dev/null | awk '/SHA-256/ {print $NF; exit}')
echo "$BADGING" | grep -q "versionName='$VERSION'" || { echo "Version mismatch: $BADGING"; exit 1; }
[[ $SIGNER == 305b375e* ]] || { echo "Unexpected signing key: $SIGNER"; exit 1; }

mkdir -p "$OUT"
cp "$APK" "$OUT/Quiet-$VERSION.apk"
echo "$OUT/Quiet-$VERSION.apk  ($(du -h "$OUT/Quiet-$VERSION.apk" | cut -f1), $(echo "$BADGING" | grep -o "versionCode='[0-9]*'"), signer ${SIGNER:0:8})"
