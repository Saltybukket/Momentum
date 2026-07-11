#!/usr/bin/env bash
set -euo pipefail

missing=0
connected_missing=0
strict_connected=false
[[ "${1:-}" == "--connected" ]] && strict_connected=true
check() {
  local command=$1
  local hint=$2
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "MISSING: $command — $hint" >&2
    missing=1
  else
    echo "OK: $command ($(command -v "$command"))"
  fi
}

check python3 "Install Python 3.12 or newer."
check docker "Enable Docker Desktop WSL integration."
check java "Install JDK 17."
if [[ -n "${ANDROID_HOME:-}" && -x "$ANDROID_HOME/platform-tools/adb" ]]; then
  export PATH="$ANDROID_HOME/platform-tools:$PATH"
fi
check adb "Set ANDROID_HOME and install platform-tools."

if [[ -n "${ANDROID_HOME:-}" && -d "$ANDROID_HOME/platforms/android-36" ]]; then
  echo "OK: WSL Android platform API 36 at $ANDROID_HOME"
else
  echo "MISSING: WSL Android platform API 36 — install platform;android-36." >&2
  missing=1
fi

windows_sdk="${WINDOWS_ANDROID_SDK_ROOT:-}"
if [[ -z "$windows_sdk" ]]; then
  for candidate in /mnt/c/Users/*/AppData/Local/Android/Sdk; do
    [[ -d "$candidate" ]] && windows_sdk="$candidate" && break
  done
fi
if [[ -z "$windows_sdk" ]]; then
  echo "MISSING: Windows Android SDK — set WINDOWS_ANDROID_SDK_ROOT." >&2
  missing=1
else
  echo "OK: Windows Android SDK at $windows_sdk"
  image_dir="$windows_sdk/system-images/android-36/google_apis/x86_64"
  if [[ -d "$image_dir" ]]; then
    echo "OK: Windows stable API-36 Google APIs x86_64 system image"
    windows_home="${windows_sdk%/AppData/Local/Android/Sdk}"
    if find "$windows_home/.android/avd" -name config.ini -type f -exec grep -l 'target=android-36' {} + 2>/dev/null | grep -q .; then
      echo "OK: Windows stable API-36 AVD"
    else
      echo "$([[ "$strict_connected" == true ]] && echo MISSING || echo WARNING): Windows stable API-36 AVD — create one using the API-36 Google APIs x86_64 image." >&2
      connected_missing=1
    fi
  else
    echo "$([[ "$strict_connected" == true ]] && echo MISSING || echo WARNING): Windows stable API-36 Google APIs x86_64 system image — install it with sdkmanager.bat \"system-images;android-36;google_apis;x86_64\"." >&2
    connected_missing=1
  fi
fi

if [[ -x "$HOME/.local/bin/uv" ]] || command -v uv >/dev/null 2>&1; then
  echo "OK: uv"
else
  echo "MISSING: uv — install with pipx install uv." >&2
  missing=1
fi

if [[ "$strict_connected" == "true" ]]; then
  exit $((missing || connected_missing))
fi
exit "$missing"
