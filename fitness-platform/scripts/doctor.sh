#!/usr/bin/env bash
set -euo pipefail

missing=0
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
  echo "OK: Android API 36 at $ANDROID_HOME"
else
  echo "MISSING: Android API 36 — install platform;android-36 and a stable Google APIs x86_64 image." >&2
  missing=1
fi

if [[ -x "$HOME/.local/bin/uv" ]] || command -v uv >/dev/null 2>&1; then
  echo "OK: uv"
else
  echo "MISSING: uv — install with pipx install uv." >&2
  missing=1
fi

exit "$missing"
