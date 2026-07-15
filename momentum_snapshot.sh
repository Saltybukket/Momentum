#!/usr/bin/env bash
set -Eeuo pipefail

# Momentum snapshot creator
#
# Creates a compact review snapshot of the current working tree:
# - tracked files and non-ignored untracked files
# - staged/unstaged patches and Git metadata
# - compact existing test evidence (JUnit/lint/connected result text)
# - no .git directory, build caches, virtualenvs, local secrets, APKs or ZIPs
#
# Usage:
#   ./momentum_snapshot.sh              -> next major version, e.g. 15
#   ./momentum_snapshot.sh --same       -> next revision, e.g. 14 -> 14B -> 14C
#   ./momentum_snapshot.sh 14           -> 14, or 14B/14C if 14 already exists
#   ./momentum_snapshot.sh 15A          -> exact name; aborts if it exists
#
# Optional environment variables:
#   OUTPUT_DIR=/some/path
#   INCLUDE_GIT_BUNDLE=1               # includes history; may include old secrets
#   INCLUDE_TEST_EVIDENCE=1             # default: 1
#   INCLUDE_IDEA_ASSETS=1               # include large visual idea PNGs; default: 0
#
# Run from anywhere inside the Momentum repository.

PROJECT_NAME="momentum_current"
OUTPUT_DIR="${OUTPUT_DIR:-/home/student/projects/momentum_snapshots}"
INCLUDE_GIT_BUNDLE="${INCLUDE_GIT_BUNDLE:-0}"
INCLUDE_TEST_EVIDENCE="${INCLUDE_TEST_EVIDENCE:-1}"
INCLUDE_IDEA_ASSETS="${INCLUDE_IDEA_ASSETS:-0}"

STAGING_DIR=""

 die() {
    printf 'Error: %s\n' "$*" >&2
    exit 1
}

cleanup() {
    [[ -n "${STAGING_DIR:-}" ]] && rm -rf -- "$STAGING_DIR"
}
trap cleanup EXIT

for cmd in zip git python3 cp find sha256sum stat; do
    command -v "$cmd" >/dev/null 2>&1 || die "Required command not found: $cmd"
done

git rev-parse --show-toplevel >/dev/null 2>&1 \
    || die "Run this script inside the Momentum Git repository."

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"
mkdir -p -- "$OUTPUT_DIR"
OUTPUT_DIR="$(cd "$OUTPUT_DIR" && pwd -P)"

# 1 -> B, 2 -> C, ... 25 -> Z, 26 -> AA, 27 -> AB
revision_suffix() {
    local n="$1"
    local result=""
    local rem

    (( n > 0 )) || die "Revision number must be positive."
    n=$((n + 1))

    while (( n > 0 )); do
        rem=$(( (n - 1) % 26 ))
        result="$(printf "\\$(printf '%03o' $((65 + rem)))")${result}"
        n=$(( (n - 1) / 26 ))
    done

    printf '%s' "$result"
}

list_existing() {
    find "$OUTPUT_DIR" -maxdepth 1 -type f \
        -regextype posix-extended \
        -regex ".*/${PROJECT_NAME}_[0-9]+([A-Z]+)?\\.zip" \
        -printf '%f\n' 2>/dev/null || true
}

latest_major() {
    list_existing \
        | sed -E "s/^${PROJECT_NAME}_0*([0-9]+)[A-Z]*\\.zip$/\\1/" \
        | sort -n \
        | tail -n 1
}

format_major() {
    local n="$1"
    if (( n < 100 )); then
        printf '%02d' "$n"
    else
        printf '%d' "$n"
    fi
}

next_name_for_major() {
    local major="$1"
    local formatted base candidate rev

    formatted="$(format_major "$major")"
    base="${PROJECT_NAME}_${formatted}"
    candidate="${base}.zip"

    if [[ ! -e "$OUTPUT_DIR/$candidate" ]]; then
        printf '%s' "$candidate"
        return
    fi

    rev=1
    while :; do
        candidate="${base}$(revision_suffix "$rev").zip"
        if [[ ! -e "$OUTPUT_DIR/$candidate" ]]; then
            printf '%s' "$candidate"
            return
        fi
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

archive_path="$OUTPUT_DIR/$archive_name"
STAGING_DIR="$(mktemp -d "${TMPDIR:-/tmp}/momentum-snapshot.XXXXXX")"
METADATA_DIR="$STAGING_DIR/.snapshot_metadata"
mkdir -p -- "$METADATA_DIR"

# Never create metadata inside the repository: Git status remains truthful.
git rev-parse --show-toplevel > "$METADATA_DIR/repository-root.txt"
git rev-parse HEAD > "$METADATA_DIR/head-commit.txt"
git branch --show-current > "$METADATA_DIR/branch.txt"
if git rev-parse '@{upstream}' >/dev/null 2>&1; then
    git rev-parse '@{upstream}' > "$METADATA_DIR/upstream-commit.txt"
    git rev-parse --abbrev-ref '@{upstream}' > "$METADATA_DIR/upstream-ref.txt"
else
    : > "$METADATA_DIR/upstream-commit.txt"
    : > "$METADATA_DIR/upstream-ref.txt"
fi

git status --short --branch --untracked-files=all > "$METADATA_DIR/git-status.txt"
git status --porcelain=v2 --branch --untracked-files=all > "$METADATA_DIR/git-status-v2.txt"
git log --graph --decorate --oneline -n 50 > "$METADATA_DIR/git-log.txt"
git diff --binary > "$METADATA_DIR/working-tree.patch"
git diff --cached --binary > "$METADATA_DIR/staged.patch"
git diff --stat > "$METADATA_DIR/working-tree-stat.txt"
git diff --cached --stat > "$METADATA_DIR/staged-stat.txt"
git diff --name-status > "$METADATA_DIR/working-tree-name-status.txt"
git diff --cached --name-status > "$METADATA_DIR/staged-name-status.txt"
git diff --summary > "$METADATA_DIR/working-tree-summary.txt"
git diff --cached --summary > "$METADATA_DIR/staged-summary.txt"
git ls-files --others --exclude-standard > "$METADATA_DIR/untracked-files.txt"
git ls-files --stage > "$METADATA_DIR/tracked-index.txt"
git submodule status --recursive > "$METADATA_DIR/submodules.txt" 2>&1 || true

set +e
git diff --check > "$METADATA_DIR/diff-check.txt" 2>&1
diff_check_status=$?
git diff --cached --check > "$METADATA_DIR/staged-diff-check.txt" 2>&1
staged_diff_check_status=$?
set -e
printf '%s\n' "$diff_check_status" > "$METADATA_DIR/diff-check-exit-code.txt"
printf '%s\n' "$staged_diff_check_status" > "$METADATA_DIR/staged-diff-check-exit-code.txt"

# Return success for safe review templates and explicit examples only.
is_allowed_env_template() {
    case "$1" in
        .env.example|.env.sample|.env.template) return 0 ;;
        *) return 1 ;;
    esac
}

# Return success when a path must not enter the snapshot.
should_exclude_path() {
    local path="$1"
    local base="${path##*/}"
    local lower="${base,,}"
    local wrapped="/$path/"

    if [[ "$INCLUDE_IDEA_ASSETS" != "1" && "$wrapped" == */icon_and_image_ideas/* ]]; then
        return 0
    fi

    case "$wrapped" in
        */.git/*|*/.gradle/*|*/.idea/*|*/.venv/*|*/venv/*|*/node_modules/*|\
        */snapshots/*|*/build/*|*/__pycache__/*|*/.pytest_cache/*|\
        */.mypy_cache/*|*/.ruff_cache/*|*/.cache/*|*/htmlcov/*|\
        */coverage/*|*/dist/*|*/target/*)
            return 0
            ;;
    esac

    if [[ "$base" == ".env" ]]; then
        return 0
    fi
    if [[ "$base" == .env.* ]] && ! is_allowed_env_template "$base"; then
        return 0
    fi
    if [[ "$base" == "local.properties" ]]; then
        return 0
    fi

    case "$lower" in
        *.keystore|*.jks|*.pem|*.key|*.p12|*.pfx|\
        *.pyc|*.pyo|*.class|*.apk|*.aab|*.dex|*.so|*.o|*.obj|\
        *.tmp|*.swp|*.swo|*.zip)
            return 0
            ;;
    esac

    return 1
}

candidate_list="$STAGING_DIR/candidates.zlist"
selected_list="$METADATA_DIR/selected-files.txt"
excluded_list="$METADATA_DIR/excluded-files.txt"
: > "$selected_list"
: > "$excluded_list"

git ls-files -co --exclude-standard -z > "$candidate_list"

while IFS= read -r -d '' path; do
    [[ -e "$path" || -L "$path" ]] || {
        printf 'missing\t%s\n' "$path" >> "$excluded_list"
        continue
    }

    if should_exclude_path "$path"; then
        printf 'excluded\t%s\n' "$path" >> "$excluded_list"
        continue
    fi

    mkdir -p -- "$STAGING_DIR/$(dirname -- "$path")"
    cp -a -- "$path" "$STAGING_DIR/$path"
    printf '%s\n' "$path" >> "$selected_list"
done < "$candidate_list"

sort -u -o "$selected_list" "$selected_list"
sort -u -o "$excluded_list" "$excluded_list"
cp -- "$selected_list" "$METADATA_DIR/project-tree.txt"

# Compact evidence from already-existing test runs. These files may be stale;
# their original path, mtime and hash are recorded for honest interpretation.
TEST_EVIDENCE_DIR="$METADATA_DIR/test-evidence"
TEST_EVIDENCE_INDEX="$METADATA_DIR/test-evidence-index.tsv"
: > "$TEST_EVIDENCE_INDEX"

copy_test_evidence() {
    local source="$1"
    local destination="$TEST_EVIDENCE_DIR/$source"
    mkdir -p -- "$(dirname -- "$destination")"
    cp -a -- "$source" "$destination"
    printf '%s\t%s\t%s\t%s\n' \
        "$source" \
        "$(stat -c '%s' -- "$source")" \
        "$(stat -c '%y' -- "$source")" \
        "$(sha256sum -- "$source" | cut -d' ' -f1)" \
        >> "$TEST_EVIDENCE_INDEX"
}

if [[ "$INCLUDE_TEST_EVIDENCE" == "1" ]]; then
    while IFS= read -r -d '' evidence; do
        copy_test_evidence "$evidence"
    done < <(
        find fitness-platform -type f \
            \( -path '*/.venv/*' -o -path '*/venv/*' -o -path '*/node_modules/*' \) -prune -o \
            \( \
                -path '*/build/test-results/*' -a -name '*.xml' -o \
                -path '*/build/reports/*' -a -name 'lint-results-*.xml' -o \
                -path '*/build/reports/*' -a -name 'lint-results-*.txt' -o \
                -path '*/build/outputs/androidTest-results/*' -a -name '*.xml' -o \
                -path '*/build/outputs/androidTest-results/*' -a -name 'test-result.textproto' -o \
                -name 'junit*.xml' -o -name 'test-results*.xml' -o \
                -name 'coverage.xml' -o -name 'coverage.json' \
            \) -print0 2>/dev/null
    )
fi

sort -u -o "$TEST_EVIDENCE_INDEX" "$TEST_EVIDENCE_INDEX"

{
    printf 'Created: %s\n' "$(date --iso-8601=seconds)"
    printf 'Host: %s\n' "$(uname -a)"
    printf 'Archive format: compact working-tree review snapshot v2\n'
    printf 'Test evidence included: %s (existing files only; may be stale)\n' "$INCLUDE_TEST_EVIDENCE"
    printf 'Visual idea assets included: %s\n' "$INCLUDE_IDEA_ASSETS"
    printf '\n--- Git ---\n'
    git --version 2>&1 || true
    printf '\n--- Java ---\n'
    java -version 2>&1 || true
    printf '\n--- Python ---\n'
    python3 --version 2>&1 || true
    printf '\n--- Docker ---\n'
    docker --version 2>&1 || true
    printf '\n--- zip ---\n'
    zip -v 2>&1 | head -n 3 || true
} > "$METADATA_DIR/environment.txt"

if [[ "$INCLUDE_GIT_BUNDLE" == "1" ]]; then
    git bundle create "$METADATA_DIR/repository.bundle" HEAD
fi

# Hash/mode manifest for everything that will be archived.
python3 - "$STAGING_DIR" > "$METADATA_DIR/archive-manifest.tsv" <<'PY'
import hashlib
import os
import stat
import sys
from pathlib import Path

root = Path(sys.argv[1])
rows = []
for path in root.rglob('*'):
    rel = path.relative_to(root).as_posix()
    if rel == '.snapshot_metadata/archive-manifest.tsv':
        continue
    st = path.lstat()
    mode = stat.S_IMODE(st.st_mode)
    if path.is_symlink():
        kind = 'symlink'
        payload = os.readlink(path).encode('utf-8', 'surrogateescape')
        size = len(payload)
    elif path.is_file():
        kind = 'file'
        payload = path.read_bytes()
        size = st.st_size
    elif path.is_dir():
        continue
    else:
        kind = 'other'
        payload = b''
        size = st.st_size
    digest = hashlib.sha256(payload).hexdigest()
    rows.append((rel, kind, f'{mode:04o}', str(size), digest))

for row in sorted(rows):
    print('\t'.join(row))
PY

rm -f -- "$archive_path"
(
    cd "$STAGING_DIR"
    zip -r -q -y "$archive_path" .
)

# Final self-check: prohibited local files must not be present.
python3 - "$archive_path" <<'PY'
import sys
import zipfile
from pathlib import PurePosixPath

archive = sys.argv[1]
violations = []
with zipfile.ZipFile(archive) as zf:
    for name in zf.namelist():
        if name.endswith('/'):
            continue
        path = PurePosixPath(name)
        base = path.name.lower()
        parts = set(path.parts)
        allowed_template = base in {'.env.example', '.env.sample', '.env.template'}
        if (
            base == '.env'
            or (base.startswith('.env.') and not allowed_template)
            or base == 'local.properties'
            or base.endswith(('.keystore', '.jks', '.pem', '.key', '.p12', '.pfx'))
            or any(part in {'.git', '.venv', 'venv', 'node_modules', 'build'} for part in parts)
        ):
            # Test evidence intentionally lives below .snapshot_metadata and may
            # preserve an original path containing a build component.
            if name.startswith('.snapshot_metadata/test-evidence/') and 'build' in parts:
                continue
            violations.append(name)

if violations:
    print('Snapshot self-check failed; prohibited entries:', file=sys.stderr)
    for item in violations:
        print(f'  {item}', file=sys.stderr)
    raise SystemExit(1)
PY

printf 'Created: %s\n' "$archive_path"
printf 'Size:    %s\n' "$(du -h "$archive_path" | cut -f1)"
printf 'Files:   %s\n' "$(unzip -Z1 "$archive_path" | grep -vc '/$')"
printf 'Branch:  %s\n' "$(git branch --show-current)"
printf 'Commit:  %s\n' "$(git rev-parse HEAD)"
printf 'SHA-256: %s\n' "$(sha256sum "$archive_path" | cut -d' ' -f1)"
printf 'Note:    Existing test evidence is metadata and may be older than HEAD.\n'