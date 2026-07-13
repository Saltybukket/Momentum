"""Lightweight repository/documentation integrity checks used by CI.

The script intentionally uses only the Python standard library so it can run before project
installation. It verifies required architecture documents, relative Markdown links, structured data
syntax and the absence of generated or secret-prone paths.
"""

from __future__ import annotations

import json
import re
import shutil
import subprocess
import sys
import tomllib
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REQUIRED_FILES = (
    "README.md",
    "ARCHITECTURE.md",
    "API.md",
    "DATA_MODEL.md",
    "SECURITY.md",
    "PRIVACY.md",
    "INTEGRATIONS.md",
    "TESTING.md",
    "CONTRIBUTING.md",
    "IMPLEMENTATION_PLAN.md",
    "FUTURE_FEATURES.md",
    "CHANGELOG.md",
    "docs/STATUS.md",
    "docs/archive/IMPLEMENTATION_REPORT_2026-07-11.md",
    ".env.example",
    "docker-compose.yml",
    "backend/pyproject.toml",
    "backend/uv.lock",
    "android/settings.gradle.kts",
)
FORBIDDEN_NAMES = {".venv", ".pytest_cache", ".mypy_cache", ".ruff_cache", ".gradle", "build"}
FORBIDDEN_FILES = {".env", ".coverage", "local.properties"}
MARKDOWN_LINK = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")


def fail(message: str, errors: list[str]) -> None:
    errors.append(message)


def repository_files() -> list[Path]:
    """Return tracked and non-ignored untracked files, excluding local build output."""
    try:
        git = shutil.which("git")
        if git is None:
            raise FileNotFoundError("git executable not found")
        result = subprocess.run(  # noqa: S603 - fixed Git command, no untrusted arguments
            [git, "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
            cwd=ROOT,
            check=True,
            capture_output=True,
        )
    except (FileNotFoundError, subprocess.CalledProcessError):
        return [
            path
            for path in ROOT.rglob("*")
            if path.is_file()
            and not any(part in FORBIDDEN_NAMES for part in path.relative_to(ROOT).parts)
        ]
    return [ROOT / raw.decode("utf-8") for raw in result.stdout.split(b"\0") if raw]


def main() -> int:
    errors: list[str] = []
    for relative in REQUIRED_FILES:
        path = ROOT / relative
        if not path.is_file() or path.stat().st_size == 0:
            fail(f"required file missing or empty: {relative}", errors)

    files = repository_files()
    forbidden_directories: set[Path] = set()
    for path in files:
        relative = path.relative_to(ROOT)
        for parent in relative.parents:
            if parent.name in FORBIDDEN_NAMES:
                forbidden_directories.add(parent)
        if path.name in FORBIDDEN_FILES:
            fail(f"generated/secret file must not be committed: {relative}", errors)
    for relative in sorted(forbidden_directories):
        fail(f"generated directory must not be committed: {relative}", errors)

    for path in (path for path in files if path.suffix == ".json"):
        try:
            json.loads(path.read_text(encoding="utf-8"))
        except Exception as exc:
            fail(f"invalid JSON {path.relative_to(ROOT)}: {exc}", errors)

    for path in (path for path in files if path.suffix == ".toml"):
        try:
            with path.open("rb") as handle:
                tomllib.load(handle)
        except Exception as exc:
            fail(f"invalid TOML {path.relative_to(ROOT)}: {exc}", errors)

    for path in (path for path in files if path.suffix == ".xml"):
        try:
            ET.parse(path)  # noqa: S314 - repository-owned Android resource XML
        except Exception as exc:
            fail(f"invalid XML {path.relative_to(ROOT)}: {exc}", errors)

    for path in (path for path in files if path.suffix == ".md"):
        text = path.read_text(encoding="utf-8")
        for raw_target in MARKDOWN_LINK.findall(text):
            target = raw_target.split("#", 1)[0].strip()
            if not target or "://" in target or target.startswith("mailto:"):
                continue
            candidate = (path.parent / target).resolve()
            if ROOT not in candidate.parents and candidate != ROOT:
                fail(
                    f"Markdown link escapes repository in {path.relative_to(ROOT)}: {raw_target}",
                    errors,
                )
            elif not candidate.exists():
                fail(f"broken relative link in {path.relative_to(ROOT)}: {raw_target}", errors)

    if errors:
        print("Repository integrity check failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print("Repository integrity check passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
