#!/usr/bin/env bash
set -Eeuo pipefail

# Momentum snapshot creator
#
# Usage:
#   ./momentum_snapshot.sh              -> next main version, e.g. 15
#   ./momentum_snapshot.sh --same       -> next revision of latest version, e.g. 14B -> 14C
#   ./momentum_snapshot.sh 14           -> 14, or 14B/14C if 14 already exists
#   ./momentum_snapshot.sh 15A          -> exact name, aborts if it already exists
#
# Optional:
#   INCLUDE_GIT_BUNDLE=1 ./momentum_snapshot.sh
#   OUTPUT_DIR=/some/path ./momentum_snapshot.sh
#
# Default output directory:
#   /home/student/projects/momentum_snapshots
#
# Run this script from the Momentum repository root.

PROJECT_NAME="momentum_current"
OUTPUT_DIR="${OUTPUT_DIR:-/home/student/projects/momentum_snapshots}"
INCLUDE_GIT_BUNDLE="${INCLUDE_GIT_BUNDLE:-0}"
METADATA_DIR=".snapshot_metadata"

die() {
    printf 'Error: %s\n' "$*" >&2
    exit 1
}

command -v zip >/dev/null 2>&1 || die "'zip' is not installed. On Ubuntu/WSL: sudo apt install zip"
command -v git >/dev/null 2>&1 || die "'git' is not installed."
git rev-parse --show-toplevel >/dev/null 2>&1 || die "Run this script inside the Momentum Git repository."

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"
mkdir -p "$OUTPUT_DIR"

# Convert a revision number to letters:
# 1 -> B, 2 -> C, ... 25 -> Z, 26 -> AA, 27 -> AB
revision_suffix() {
    local n="$1"
    local result=""
    local rem

    while (( n > 0 )); do
        rem=$(( (n - 1) % 26 ))
        result="$(printf "\\$(printf '%03o' $((65 + rem)))")${result}"
        n=$(( (n - 1) / 26 ))
    done

    printf '%s' "$result"
}

# List versions found in OUTPUT_DIR.
# Supports names like:
# momentum_current_01.zip
# momentum_current_14.zip
# momentum_current_14B.zip
list_existing() {
    find "$OUTPUT_DIR" -maxdepth 1 -type f \
        -regextype posix-extended \
        -regex ".*/${PROJECT_NAME}_[0-9]+([A-Z]+)?\.zip" \
        -printf '%f\n' 2>/dev/null || true
}

latest_major() {
    list_existing \
        | sed -E "s/^${PROJECT_NAME}_0*([0-9]+)[A-Z]*\.zip$/\1/" \
        | sort -n \
        | tail -n 1
}

format_major() {
    # Two digits for 1..99, then natural width for 100+
    local n="$1"
    if (( n < 100 )); then
        printf '%02d' "$n"
    else
        printf '%d' "$n"
    fi
}

next_name_for_major() {
    local major="$1"
    local formatted
    local base
    local candidate
    local rev=0

    formatted="$(format_major "$major")"
    base="${PROJECT_NAME}_${formatted}"
    candidate="${base}.zip"

    if [[ ! -e "$OUTPUT_DIR/$candidate" ]]; then
        printf '%s' "$candidate"
        return
    fi

    # First revision is B, matching the requested 14 -> 14B convention.
    rev=1
    while :; do
        candidate="${base}$(revision_suffix "$rev").zip"
        [[ ! -e "$OUTPUT_DIR/$candidate" ]] && {
            printf '%s' "$candidate"
            return
        }
        ((rev++))
    done
}

requested="${1:-}"
archive_name=""

case "$requested" in
    "")
        current="$(latest_major)"
        if [[ -z "$current" ]]; then
            current=1
        else
            current=$((10#$current + 1))
        fi
        archive_name="$(next_name_for_major "$current")"
        ;;
    --same)
        current="$(latest_major)"
        [[ -n "$current" ]] || die "No previous snapshot exists in: $OUTPUT_DIR"
        archive_name="$(next_name_for_major "$((10#$current))")"
        ;;
    [0-9]*)
        if [[ "$requested" =~ ^([0-9]+)([A-Z]+)?$ ]]; then
            major=$((10#${BASH_REMATCH[1]}))
            suffix="${BASH_REMATCH[2]:-}"

            if [[ -n "$suffix" ]]; then
                archive_name="${PROJECT_NAME}_$(format_major "$major")${suffix}.zip"
                [[ ! -e "$OUTPUT_DIR/$archive_name" ]] \
                    || die "Archive already exists: $OUTPUT_DIR/$archive_name"
            else
                archive_name="$(next_name_for_major "$major")"
            fi
        else
            die "Invalid version. Examples: 14, 14B, 15, --same"
        fi
        ;;
    *)
        die "Usage: $0 [--same|VERSION]"
        ;;
esac

cleanup() {
    rm -rf "$REPO_ROOT/$METADATA_DIR"
}
trap cleanup EXIT

rm -rf "$METADATA_DIR"
mkdir -p "$METADATA_DIR"

# Git/repository state for later reconstruction and debugging.
git rev-parse --show-toplevel > "$METADATA_DIR/repository-root.txt"
git rev-parse HEAD > "$METADATA_DIR/head-commit.txt"
git branch --show-current > "$METADATA_DIR/branch.txt"
git status --short --branch > "$METADATA_DIR/git-status.txt"
git log --graph --decorate --oneline -n 50 > "$METADATA_DIR/git-log.txt"
git diff --binary > "$METADATA_DIR/working-tree.patch"
git diff --cached --binary > "$METADATA_DIR/staged.patch"
git diff --stat > "$METADATA_DIR/working-tree-stat.txt"
git diff --cached --stat > "$METADATA_DIR/staged-stat.txt"
git ls-files --others --exclude-standard > "$METADATA_DIR/untracked-files.txt"
git submodule status --recursive > "$METADATA_DIR/submodules.txt" 2>&1 || true

# Project structure, excluding noisy/generated folders.
find . \
    \( -path './.git' \
       -o -path './.gradle' \
       -o -path './.idea' \
       -o -path './.venv' \
       -o -path './venv' \
       -o -path './node_modules' \
       -o -path './snapshots' \
       -o -path '*/build' \
       -o -path '*/__pycache__' \
       -o -path '*/.pytest_cache' \
       -o -path '*/.mypy_cache' \
       -o -path '*/.ruff_cache' \
       -o -path '*/.coverage' \
       -o -path '*/htmlcov' \
    \) -prune -o -printf '%P\n' \
    | sort > "$METADATA_DIR/project-tree.txt"

# Useful tool versions. Failures are recorded instead of aborting.
{
    printf 'Created: %s\n' "$(date --iso-8601=seconds)"
    printf 'Host: %s\n' "$(uname -a)"
    printf '\n--- Git ---\n'
    git --version 2>&1 || true
    printf '\n--- Java ---\n'
    java -version 2>&1 || true
    printf '\n--- Python ---\n'
    python3 --version 2>&1 || true
    printf '\n--- Docker ---\n'
    docker --version 2>&1 || true
    printf '\n--- Gradle wrapper ---\n'
    if [[ -x fitness-platform/android/gradlew ]]; then
        (
            cd fitness-platform/android
            ./gradlew --version --no-daemon
        ) 2>&1 || true
    elif [[ -x ./gradlew ]]; then
        ./gradlew --version --no-daemon 2>&1 || true
    fi
} > "$METADATA_DIR/environment.txt"

# Optional Git history bundle. It can make the archive much larger and can
# include secrets that existed in earlier commits.
if [[ "$INCLUDE_GIT_BUNDLE" == "1" ]]; then
    git bundle create "$METADATA_DIR/repository.bundle" HEAD
fi

archive_path="$OUTPUT_DIR/$archive_name"

# Keep source code, tests, migrations, lockfiles, docs and existing log files.
# Exclude generated caches, IDE state, secrets and large reproducible outputs.
zip -r -q "$archive_path" . \
    -x \
    '.git/*' \
    '.gradle/*' \
    '.idea/*' \
    '.venv/*' \
    'venv/*' \
    'node_modules/*' \
    'snapshots/*' \
    '*/build/*' \
    '*/.gradle/*' \
    '*/__pycache__/*' \
    '*/.pytest_cache/*' \
    '*/.mypy_cache/*' \
    '*/.ruff_cache/*' \
    '*/.cache/*' \
    '*/htmlcov/*' \
    '*/coverage/*' \
    '*/dist/*' \
    '*/target/*' \
    '*.pyc' \
    '*.pyo' \
    '*.class' \
    '*.apk' \
    '*.aab' \
    '*.dex' \
    '*.so' \
    '*.o' \
    '*.obj' \
    '*.tmp' \
    '*.swp' \
    '*.swo' \
    '.env' \
    '.env.*' \
    'local.properties' \
    '*.keystore' \
    '*.jks' \
    '*.pem' \
    '*.key' \
    '*.p12' \
    '*.pfx' \
    '*.zip'

printf 'Created: %s\n' "$archive_path"
printf 'Size:    %s\n' "$(du -h "$archive_path" | cut -f1)"
printf 'Branch:  %s\n' "$(git branch --show-current)"
printf 'Commit:  %s\n' "$(git rev-parse --short HEAD)"