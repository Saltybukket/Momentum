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
MODULE_RESULT_DIR="${RESULT_FILE%.txt}"
mkdir -p "$MODULE_RESULT_DIR"

declare -A RUNNERS=(
  [core:database]="at.fitnessplatform.core.database.test/androidx.test.runner.AndroidJUnitRunner"
  [core:datastore]="at.fitnessplatform.core.datastore.test/androidx.test.runner.AndroidJUnitRunner"
  [data]="at.fitnessplatform.data.test/androidx.test.runner.AndroidJUnitRunner"
  [feature:main]="at.fitnessplatform.feature.main.test/androidx.test.runner.AndroidJUnitRunner"
  [app]="at.fitnessplatform.app.test/androidx.test.runner.AndroidJUnitRunner"
)
MODULES=("${@:-core:database core:datastore data feature:main app}")

for module in "${MODULES[@]}"; do
  if [[ -z "${RUNNERS[$module]:-}" ]]; then
    echo "Unknown instrumentation module '$module'. Supported modules: ${!RUNNERS[*]}" >&2
    exit 4
  fi
done

: > "$RESULT_FILE"
API_LEVEL="$("${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
API_RELEASE="$("${ADB[@]}" shell getprop ro.build.version.release_or_codename | tr -d '\r')"
{
  echo "ADB server socket: $ADB_SERVER_SOCKET"
  echo "Device: ${connected_devices[0]}"
  echo "API level: $API_LEVEL"
  echo "Release/codename: $API_RELEASE"
} | tee -a "$RESULT_FILE"

if [[ "$API_LEVEL" != "36" ]]; then
  echo "Connected acceptance requires a stable API 36 device; found API $API_LEVEL." | tee -a "$RESULT_FILE" >&2
  exit 5
fi

run_module() {
  local module="$1"
  local gradle_module=":$module"
  local module_path="${module//:/\/}"
  local module_name="${module##*:}"
  local app_apk="$ANDROID_DIR/$module_path/build/outputs/apk/debug/$module_name-debug.apk"
  local test_apk="$ANDROID_DIR/$module_path/build/outputs/apk/androidTest/debug/$module_name-debug-androidTest.apk"
  echo "Building and installing $gradle_module without UTP"
  "$ANDROID_DIR/gradlew" -p "$ANDROID_DIR" "${gradle_module}:assembleDebug" "${gradle_module}:assembleDebugAndroidTest" || return
  if [[ "$module" == "app" ]]; then
    "${ADB[@]}" install -r "$app_apk" || return
  fi
  "${ADB[@]}" install -r "$test_apk" || return
  echo "Running ${RUNNERS[$module]}"
  "${ADB[@]}" shell pm list instrumentation | grep -F "${RUNNERS[$module]}" || return
  timeout "$INSTRUMENTATION_TIMEOUT_SECONDS" "${ADB[@]}" shell am instrument -w -r "${RUNNERS[$module]}"
}

parser_args=()
for module in "${MODULES[@]}"; do
  module_log="$MODULE_RESULT_DIR/${module//:/-}.txt"
  set +e
  run_module "$module" 2>&1 | tee "$module_log" | tee -a "$RESULT_FILE"
  module_status="${PIPESTATUS[0]}"
  set -e
  if [[ "$module_status" -eq 124 ]]; then
    echo "MOMENTUM_RUNNER_TIMEOUT" | tee -a "$module_log" "$RESULT_FILE"
  fi
  echo "MOMENTUM_RUNNER_EXIT=$module_status" | tee -a "$module_log" "$RESULT_FILE"
  parser_args+=("$module=$module_log")
done

set +e
python3 "$ROOT_DIR/scripts/parse_android_connected_results.py" \
  --api-level "$API_LEVEL" \
  --expected-api 36 \
  "${parser_args[@]}" | tee -a "$RESULT_FILE"
parser_status="${PIPESTATUS[0]}"
set -e
echo "Raw result: $RESULT_FILE"
exit "$parser_status"
