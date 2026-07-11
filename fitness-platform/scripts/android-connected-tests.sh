#!/usr/bin/env bash
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
ADB_SERVER_SOCKET="${ADB_SERVER_SOCKET:-tcp:127.0.0.1:5037}"
INSTRUMENTATION_TIMEOUT_SECONDS="${INSTRUMENTATION_TIMEOUT_SECONDS:-600}"
export ANDROID_HOME ADB_SERVER_SOCKET
export PATH="$ANDROID_HOME/platform-tools:$PATH"

if [[ ! -x "$ANDROID_HOME/platform-tools/adb" ]]; then
  echo "Android platform-tools were not found at $ANDROID_HOME/platform-tools. Set ANDROID_HOME or ANDROID_SDK_ROOT." >&2
  exit 2
fi

if ! adb get-state 2>/dev/null | grep -qx device; then
  echo "No usable Android device is connected through ADB_SERVER_SOCKET=$ADB_SERVER_SOCKET." >&2
  echo "Start a Windows emulator, then verify from WSL with: adb devices" >&2
  exit 3
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="$ROOT_DIR/android"
RESULT_DIR="$ROOT_DIR/android/build/connected-test-results"
mkdir -p "$RESULT_DIR"
RESULT_FILE="$RESULT_DIR/$(date -u +%Y%m%dT%H%M%SZ).txt"

declare -A RUNNERS=(
  [core:database]="at.fitnessplatform.core.database.test/androidx.test.runner.AndroidJUnitRunner"
  [app]="at.fitnessplatform.app.test/androidx.test.runner.AndroidJUnitRunner"
)
MODULES=("${@:-core:database app}")

for module in "${MODULES[@]}"; do
  if [[ -z "${RUNNERS[$module]:-}" ]]; then
    echo "Unknown instrumentation module '$module'. Supported modules: ${!RUNNERS[*]}" >&2
    exit 4
  fi
done

{
  echo "ADB server socket: $ADB_SERVER_SOCKET"
  adb devices
  for module in "${MODULES[@]}"; do
    gradle_module=":$module"
    module_path="${module//:/\/}"
    module_name="${module##*:}"
    app_apk="$ANDROID_DIR/$module_path/build/outputs/apk/debug/$module_name-debug.apk"
    test_apk="$ANDROID_DIR/$module_path/build/outputs/apk/androidTest/debug/$module_name-debug-androidTest.apk"
    echo "Building and installing $gradle_module without UTP"
    "$ANDROID_DIR/gradlew" -p "$ANDROID_DIR" "${gradle_module}:assembleDebug" "${gradle_module}:assembleDebugAndroidTest"
    if [[ "$module" == "app" ]]; then
      adb install -r "$app_apk"
    fi
    adb install -r "$test_apk"
    echo "Running ${RUNNERS[$module]}"
    adb shell pm list instrumentation | grep -F "${RUNNERS[$module]}"
    timeout "$INSTRUMENTATION_TIMEOUT_SECONDS" adb shell am instrument -w -r "${RUNNERS[$module]}"
  done
} 2>&1 | tee "$RESULT_FILE"

test_count="$(grep -Eo 'OK \([0-9]+ tests?\)' "$RESULT_FILE" | sed -E 's/[^0-9]//g' | awk '{sum += $1} END {print sum + 0}')"
echo "Instrumentation tests passed: $test_count"
echo "Raw result: $RESULT_FILE"
