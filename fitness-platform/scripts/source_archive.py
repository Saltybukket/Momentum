from __future__ import annotations

import hashlib
import shutil
import subprocess
from pathlib import Path, PurePosixPath
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
OUTPUT = REPOSITORY_ROOT / "fitness-platform" / "build" / "source-archive" / "momentum-source.zip"
FIXED_TIMESTAMP = (2026, 7, 13, 0, 0, 0)
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


def tracked_files() -> list[PurePosixPath]:
    git = shutil.which("git")
    if git is None:
        raise RuntimeError("git is required to build the source archive")
    result = subprocess.run(  # noqa: S603 - resolved executable, fixed arguments
        [git, "ls-files", "-z"],
        cwd=REPOSITORY_ROOT,
        check=True,
        capture_output=True,
    )
    paths = [PurePosixPath(raw.decode()) for raw in result.stdout.split(b"\0") if raw]
    return sorted(path for path in paths if is_approved(path))


def is_approved(path: PurePosixPath) -> bool:
    if path.is_absolute() or ".." in path.parts:
        return False
    if any(part in FORBIDDEN_PARTS for part in path.parts):
        return False
    if path.name in FORBIDDEN_NAMES or path.suffix.lower() in FORBIDDEN_SUFFIXES:
        return False
    if path.suffix.lower() in {".jar", ".bin", ".so", ".dll", ".exe"}:
        return path.as_posix() in BINARY_ALLOWLIST
    return True


def build_archive(output: Path = OUTPUT) -> tuple[int, str]:
    paths = tracked_files()
    output.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(output, "w", compression=ZIP_DEFLATED, compresslevel=9) as archive:
        for path in paths:
            source = REPOSITORY_ROOT / path.as_posix()
            info = ZipInfo(path.as_posix(), FIXED_TIMESTAMP)
            info.create_system = 3
            info.compress_type = ZIP_DEFLATED
            info.external_attr = (0o755 if source.stat().st_mode & 0o111 else 0o644) << 16
            archive.writestr(info, source.read_bytes(), compresslevel=9)
    verify_archive(output, paths)
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    return len(paths), digest


def verify_archive(output: Path, expected: list[PurePosixPath]) -> None:
    with ZipFile(output) as archive:
        actual = [PurePosixPath(name) for name in archive.namelist()]
        if actual != expected:
            raise RuntimeError("Source archive file list is not sorted and reproducible")
        if any(path.is_absolute() or ".." in path.parts for path in actual):
            raise RuntimeError("Source archive contains an unsafe path")
        if any(not is_approved(path) for path in actual):
            raise RuntimeError("Source archive contains a forbidden file")


def main() -> None:
    count, digest = build_archive()
    print(f"archive={OUTPUT}")
    print(f"files={count}")
    print(f"sha256={digest}")


if __name__ == "__main__":
    main()
