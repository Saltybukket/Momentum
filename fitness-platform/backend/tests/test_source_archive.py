from __future__ import annotations

import hashlib
import json
import os
import stat
import subprocess
import sys
from pathlib import Path
from zipfile import ZipFile

PROJECT_ROOT = Path(__file__).resolve().parents[2]
SOURCE_ARCHIVE = PROJECT_ROOT / "scripts" / "source_archive.py"


def run(*arguments: str, cwd: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(  # noqa: S603 - test-controlled executables and arguments
        arguments,
        cwd=cwd,
        check=True,
        capture_output=True,
        text=True,
    )


def test_source_archive_reads_bytes_and_modes_only_from_commit(tmp_path: Path) -> None:
    repository = tmp_path / "repository"
    repository.mkdir()
    run("git", "init", "--quiet", cwd=repository)
    run("git", "config", "user.email", "archive@example.invalid", cwd=repository)
    run("git", "config", "user.name", "Archive Test", cwd=repository)
    (repository / "plain.txt").write_text("committed bytes\n")
    executable = repository / "tool.sh"
    executable.write_text("#!/bin/sh\nexit 0\n")
    executable.chmod(executable.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
    (repository / ".env").write_text("forbidden=true\n")
    run("git", "add", "plain.txt", "tool.sh", ".env", cwd=repository)
    run("git", "commit", "--quiet", "-m", "fixture", cwd=repository)
    commit = run("git", "rev-parse", "HEAD", cwd=repository).stdout.strip()
    first = tmp_path / "first.zip"
    second = tmp_path / "second.zip"

    run(
        sys.executable,
        str(SOURCE_ARCHIVE),
        "--repository-root",
        str(repository),
        "--output",
        str(first),
        cwd=PROJECT_ROOT,
    )
    (repository / "plain.txt").write_text("dirty working-tree bytes\n")
    executable.chmod(0o644)
    run(
        sys.executable,
        str(SOURCE_ARCHIVE),
        "--repository-root",
        str(repository),
        "--output",
        str(second),
        cwd=PROJECT_ROOT,
    )

    assert (
        hashlib.sha256(first.read_bytes()).digest() == hashlib.sha256(second.read_bytes()).digest()
    )
    with ZipFile(first) as archive:
        assert archive.read("plain.txt") == b"committed bytes\n"
        assert archive.getinfo("tool.sh").external_attr >> 16 == 0o755
        assert ".env" not in archive.namelist()
        manifest = json.loads(archive.read("SOURCE_ARCHIVE_MANIFEST.json"))
        assert manifest == {
            "commit": commit,
            "file_count": 3,
            "generator_version": "2",
            "repository": "Momentum",
        }
    assert os.access(executable, os.X_OK) is False
