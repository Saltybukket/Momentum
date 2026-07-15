#!/usr/bin/env python3
"""Validate Momentum's direct Android instrumentation runner output."""

from __future__ import annotations

import argparse
import re
from dataclasses import dataclass
from pathlib import Path

SUCCESS_PATTERN = re.compile(r"^OK \((\d+) tests?\)\r?$", re.MULTILINE)
EXIT_PATTERN = re.compile(r"^MOMENTUM_RUNNER_EXIT=(\d+)$", re.MULTILINE)
FAILURE_PATTERNS = (
    re.compile(r"FAILURES!!!", re.IGNORECASE),
    re.compile(r"INSTRUMENTATION_FAILED", re.IGNORECASE),
    re.compile(r"process crash(?:ed)?", re.IGNORECASE),
    re.compile(r"MOMENTUM_RUNNER_TIMEOUT", re.IGNORECASE),
    re.compile(r"INSTRUMENTATION_STATUS_CODE:\s*-3", re.IGNORECASE),
)


@dataclass(frozen=True)
class ModuleResult:
    module: str
    test_count: int


class InvalidInstrumentationResult(ValueError):
    """Raised when a module log is incomplete or contains a failure marker."""


def parse_module_result(module: str, content: str) -> ModuleResult:
    failures = [pattern.pattern for pattern in FAILURE_PATTERNS if pattern.search(content)]
    if failures:
        raise InvalidInstrumentationResult(
            f"{module}: failure marker detected: {', '.join(failures)}"
        )

    exits = EXIT_PATTERN.findall(content)
    if len(exits) != 1:
        raise InvalidInstrumentationResult(
            f"{module}: expected exactly one runner exit marker, found {len(exits)}"
        )
    exit_code = int(exits[0])
    if exit_code == 124:
        raise InvalidInstrumentationResult(f"{module}: instrumentation timed out")
    if exit_code != 0:
        raise InvalidInstrumentationResult(f"{module}: runner exited with {exit_code}")

    successes = SUCCESS_PATTERN.findall(content)
    if len(successes) != 1:
        raise InvalidInstrumentationResult(
            f"{module}: expected exactly one success marker, found {len(successes)}"
        )
    test_count = int(successes[0])
    if test_count <= 0:
        raise InvalidInstrumentationResult(f"{module}: runner completed without executing tests")
    return ModuleResult(module, test_count)


def parse_modules(module_logs: list[tuple[str, Path]]) -> list[ModuleResult]:
    return [
        parse_module_result(module, path.read_text(encoding="utf-8"))
        for module, path in module_logs
    ]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--api-level", required=True, type=int)
    parser.add_argument("--expected-api", default=36, type=int)
    parser.add_argument("module_logs", nargs="+", metavar="MODULE=LOG")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.api_level != args.expected_api:
        print(
            f"Connected acceptance requires API {args.expected_api}; "
            f"device reports API {args.api_level}."
        )
        return 2

    module_logs: list[tuple[str, Path]] = []
    for value in args.module_logs:
        module, separator, path = value.partition("=")
        if not separator or not module or not path:
            print(f"Invalid module log argument: {value}")
            return 2
        module_logs.append((module, Path(path)))

    try:
        results = parse_modules(module_logs)
    except (InvalidInstrumentationResult, OSError) as error:
        print(f"Connected result validation failed: {error}")
        return 1

    for result in results:
        print(f"{result.module}: API {args.api_level}, {result.test_count} tests, PASS")
    total = sum(result.test_count for result in results)
    print(
        f"Connected total: API {args.api_level}, {total} tests, PASS"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
