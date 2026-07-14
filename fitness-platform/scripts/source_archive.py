from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
OUTPUT = REPOSITORY_ROOT / "fitness-platform" / "build" / "source-archive" / "momentum-source.zip"
FIXED_TIMESTAMP = (2026, 7, 13, 0, 0, 0)
GENERATOR_VERSION = "2"
REPOSITORY_NAME = "Momentum"
MANIFEST_PATH = PurePosixPath("SOURCE_ARCHIVE_MANIFEST.json")
FORBIDDEN_PARTS = {
    ".git",
    ".gradle",
    ".idea",
    ".venv",
    "__pycache__",
    "build",
}
FORBIDDEN_NAMES = {".env", "local.properties", ".coverage"}
FORBIDDEN_SUFFIXES = {".apk", ".db", ".log", ".sqlite", ".sqlite3", ".tar", ".gz", ".zip"}
BINARY_ALLOWLIST = {"fitness-platform/android/gradle/wrapper/gradle-wrapper.jar"}


@dataclass(frozen=True)
class TreeEntry:
    mode: int
    object_id: str
    path: PurePosixPath


def git_output(repository_root: Path, *arguments: str) -> bytes:
    git = shutil.which("git")
    if git is None:
        raise RuntimeError("git is required to build the source archive")
    return subprocess.run(  # noqa: S603 - resolved executable and fixed command family
        [git, *arguments],
        cwd=repository_root,
        check=True,
        capture_output=True,
    ).stdout


def resolve_commit(repository_root: Path, reference: str) -> str:
    return (
        git_output(repository_root, "rev-parse", "--verify", f"{reference}^{{commit}}")
        .decode()
        .strip()
    )


def tree_entries(repository_root: Path, commit: str) -> list[TreeEntry]:
    raw_entries = git_output(repository_root, "ls-tree", "-rz", "--full-tree", commit)
    entries: list[TreeEntry] = []
    for raw_entry in raw_entries.split(b"\0"):
        if not raw_entry:
            continue
        metadata, raw_path = raw_entry.split(b"\t", maxsplit=1)
        raw_mode, object_type, raw_object_id = metadata.split(b" ")
        if object_type != b"blob":
            raise RuntimeError(f"Unsupported Git tree object type: {object_type.decode()}")
        path = PurePosixPath(raw_path.decode("utf-8"))
        if is_approved(path):
            entries.append(TreeEntry(int(raw_mode, 8), raw_object_id.decode(), path))
    return sorted(entries, key=lambda entry: entry.path.as_posix())


def is_approved(path: PurePosixPath) -> bool:
    if path == MANIFEST_PATH or path.is_absolute() or ".." in path.parts:
        return False
    if any(part in FORBIDDEN_PARTS for part in path.parts):
        return False
    if path.name in FORBIDDEN_NAMES or path.suffix.lower() in FORBIDDEN_SUFFIXES:
        return False
    if path.suffix.lower() in {".jar", ".bin", ".so", ".dll", ".exe"}:
        return path.as_posix() in BINARY_ALLOWLIST
    return True


def blob_bytes(repository_root: Path, object_id: str) -> bytes:
    return git_output(repository_root, "cat-file", "blob", object_id)


def archive_info(path: PurePosixPath, executable: bool) -> ZipInfo:
    info = ZipInfo(path.as_posix(), FIXED_TIMESTAMP)
    info.create_system = 3
    info.compress_type = ZIP_DEFLATED
    info.external_attr = (0o755 if executable else 0o644) << 16
    return info


def manifest_bytes(commit: str, file_count: int) -> bytes:
    return (
        json.dumps(
            {
                "commit": commit,
                "file_count": file_count,
                "generator_version": GENERATOR_VERSION,
                "repository": REPOSITORY_NAME,
            },
            indent=2,
            sort_keys=True,
        )
        + "\n"
    ).encode()


def build_archive(
    output: Path = OUTPUT,
    repository_root: Path = REPOSITORY_ROOT,
    reference: str = "HEAD",
) -> tuple[int, str, str]:
    repository_root = repository_root.resolve()
    commit = resolve_commit(repository_root, reference)
    entries = tree_entries(repository_root, commit)
    file_count = len(entries) + 1
    output.parent.mkdir(parents=True, exist_ok=True)
    archive_rows = [
        (entry.path, blob_bytes(repository_root, entry.object_id), entry.mode == 0o100755)
        for entry in entries
    ] + [(MANIFEST_PATH, manifest_bytes(commit, file_count), False)]
    archive_rows.sort(key=lambda row: row[0].as_posix())
    with ZipFile(output, "w", compression=ZIP_DEFLATED, compresslevel=9) as archive:
        for path, contents, executable in archive_rows:
            archive.writestr(
                archive_info(path, executable),
                contents,
                compresslevel=9,
            )
    expected = [row[0] for row in archive_rows]
    verify_archive(output, expected, commit)
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    return file_count, digest, commit


def verify_archive(output: Path, expected: list[PurePosixPath], commit: str) -> None:
    with ZipFile(output) as archive:
        actual = [PurePosixPath(name) for name in archive.namelist()]
        if actual != expected:
            raise RuntimeError("Source archive file list is not sorted and reproducible")
        source_paths = [path for path in actual if path != MANIFEST_PATH]
        if any(path.is_absolute() or ".." in path.parts for path in actual):
            raise RuntimeError("Source archive contains an unsafe path")
        if any(not is_approved(path) for path in source_paths):
            raise RuntimeError("Source archive contains a forbidden file")
        manifest = json.loads(archive.read(MANIFEST_PATH.as_posix()))
        if manifest != json.loads(manifest_bytes(commit, len(actual))):
            raise RuntimeError("Source archive manifest does not describe the archived commit")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build a reproducible source archive from a Git commit."
    )
    parser.add_argument("--repository-root", type=Path, default=REPOSITORY_ROOT)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--ref", default="HEAD")
    return parser.parse_args()


def main() -> None:
    arguments = parse_args()
    output = arguments.output or (
        arguments.repository_root
        / "fitness-platform"
        / "build"
        / "source-archive"
        / "momentum-source.zip"
    )
    count, digest, commit = build_archive(output, arguments.repository_root, arguments.ref)
    print(f"archive={output}")
    print(f"commit={commit}")
    print(f"files={count}")
    print(f"sha256={digest}")


if __name__ == "__main__":
    main()
