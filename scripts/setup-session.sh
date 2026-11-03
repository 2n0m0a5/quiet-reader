#!/usr/bin/env bash
# Gets a fresh cloud session ready to build and test Quiet. Safe to run again.
#   bash scripts/setup-session.sh
set -euo pipefail
cd "$(dirname "$0")/.."
SDK=/home/user/android-sdk

echo "== npm packages"; [ -d node_modules ] || npm ci

echo "== Android SDK ($SDK)"
if [ ! -x "$SDK/build-tools/36.0.0/apksigner" ]; then
  mkdir -p "$SDK/cmdline-tools"
  curl -sSL -o /tmp/clt.zip https://dl.google.com/android/repository/commandlinetools-linux-9862592_latest.zip
  rm -rf "$SDK/cmdline-tools/latest" && unzip -q /tmp/clt.zip -d "$SDK/cmdline-tools" && mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
  yes | ANDROID_HOME=$SDK "$SDK/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null 2>&1 || true
  ANDROID_HOME=$SDK "$SDK/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-36" "build-tools;36.0.0" >/dev/null
fi

# Maven Central often answers 429 to cloud machines; Google's mirror of it doesn't.
echo "== Gradle mirror"
mkdir -p ~/.gradle/init.d
cat > ~/.gradle/init.d/maven-mirror.gradle <<'GRADLE'
// Session-only: Maven Central rate-limits cloud containers (429), so try Google's mirror of it first.
def mirror = 'https://maven-central.storage-download.googleapis.com/maven2/'
settingsEvaluated { s -> s.pluginManagement.repositories { maven { url mirror } } }
allprojects {
  buildscript.repositories { maven { url mirror } }
  repositories { maven { url mirror } }
}
GRADLE

echo "== Sample books"; python3 tests/gen/mkbooks.py >/dev/null
echo "Ready. Build: npm run build · Tests: node tests/run-all.cjs · APK: bash scripts/build-apk.sh"
