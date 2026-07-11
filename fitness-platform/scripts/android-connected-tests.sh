#!/usr/bin/env bash
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
ADB_SERVER_SOCKET="${ADB_SERVER_SOCKET:-tcp:127.0.0.1:5037}"
INSTRUMENTATION_TIMEOUT_SECONDS="${INSTRUMENTATION_TIMEOUT_SECONDS:-180}"
export ANDROID_HOME ADB_SERVER_SOCKET
export PATH="$ANDROID_HOME/platform-tools:$PATH"

if [[ ! -x "$ANDROID_HOME/platform-tools/adb" ]]; then
  echo "Android platform-tools were not found at $ANDROID_HOME/platform-tools. Set ANDROID_HOME or ANDROID_SDK_ROOT." >&2
  exit 2
fi

mapfile -t connected_devices < <(adb devices | awk '$2 == "device" { print $1 }')
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  connected_devices=("$ANDROID_SERIAL")
fi
if [[ "${#connected_devices[@]}" -ne 1 ]]; then
  echo "Expected exactly one usable Android device through ADB_SERVER_SOCKET=$ADB_SERVER_SOCKET; found ${#connected_devices[@]}." >&2
  echo "Start the stable API 36 emulator or set ANDROID_SERIAL explicitly, then verify with: adb devices" >&2
  exit 3
fi
ADB=(adb -s "${connected_devices[0]}")

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="$ROOT_DIR/android"
RESULT_DIR="$ROOT_DIR/android/build/connected-test-results"
mkdir -p "$RESULT_DIR"
RESULT_FILE="$RESULT_DIR/$(date -u +%Y%m%dT%H%M%SZ).txt"

declare -A RUNNERS=(
  [core:database]="at.fitnessplatform.core.database.test/androidx.test.runner.AndroidJUnitRunner"
  [data]="at.fitnessplatform.data.test/androidx.test.runner.AndroidJUnitRunner"
  [app]="at.fitnessplatform.app.test/androidx.test.runner.AndroidJUnitRunner"
)
MODULES=("${@:-core:database data app}")

for module in "${MODULES[@]}"; do
  if [[ -z "${RUNNERS[$module]:-}" ]]; then
    echo "Unknown instrumentation module '$module'. Supported modules: ${!RUNNERS[*]}" >&2
    exit 4
  fi
done

{
  echo "ADB server socket: $ADB_SERVER_SOCKET"
  "${ADB[@]}" devices
  for module in "${MODULES[@]}"; do
    gradle_module=":$module"
    module_path="${module//:/\/}"
    module_name="${module##*:}"
    app_apk="$ANDROID_DIR/$module_path/build/outputs/apk/debug/$module_name-debug.apk"
    test_apk="$ANDROID_DIR/$module_path/build/outputs/apk/androidTest/debug/$module_name-debug-androidTest.apk"
    echo "Building and installing $gradle_module without UTP"
    "$ANDROID_DIR/gradlew" -p "$ANDROID_DIR" "${gradle_module}:assembleDebug" "${gradle_module}:assembleDebugAndroidTest"
    if [[ "$module" == "app" ]]; then
      "${ADB[@]}" install -r "$app_apk"
    fi
    "${ADB[@]}" install -r "$test_apk"
    echo "Running ${RUNNERS[$module]}"
    "${ADB[@]}" shell pm list instrumentation | grep -F "${RUNNERS[$module]}"
    timeout "$INSTRUMENTATION_TIMEOUT_SECONDS" "${ADB[@]}" shell am instrument -w -r "${RUNNERS[$module]}"
  done
} 2>&1 | tee "$RESULT_FILE"

test_count="$(grep -Eo 'OK \([0-9]+ tests?\)' "$RESULT_FILE" | sed -E 's/[^0-9]//g' | awk '{sum += $1} END {print sum + 0}')"
echo "Instrumentation tests passed: $test_count"
echo "Raw result: $RESULT_FILE"
