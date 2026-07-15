from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from parse_android_connected_results import (
    InvalidInstrumentationResult,
    parse_module_result,
    parse_modules,
)


class ConnectedResultParserTest(unittest.TestCase):
    def test_five_successful_modules_are_reported_separately(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            logs = []
            for index, module in enumerate(
                ("core:database", "core:datastore", "data", "feature:main", "app"), 1
            ):
                path = Path(directory) / f"{index}.txt"
                path.write_text(f"OK ({index} tests)\nMOMENTUM_RUNNER_EXIT=0\n", encoding="utf-8")
                logs.append((module, path))

            results = parse_modules(logs)

        self.assertEqual([1, 2, 3, 4, 5], [result.test_count for result in results])

    def test_failure_marker_is_rejected(self) -> None:
        self.assert_invalid("FAILURES!!!\nOK (2 tests)\nMOMENTUM_RUNNER_EXIT=0\n")

    def test_missing_completion_marker_is_rejected(self) -> None:
        self.assert_invalid("MOMENTUM_RUNNER_EXIT=0\n")

    def test_timeout_is_rejected(self) -> None:
        self.assert_invalid("MOMENTUM_RUNNER_TIMEOUT\nMOMENTUM_RUNNER_EXIT=124\n")

    def test_duplicate_success_is_rejected(self) -> None:
        self.assert_invalid("OK (2 tests)\nOK (3 tests)\nMOMENTUM_RUNNER_EXIT=0\n")

    def test_partial_module_success_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            passed = Path(directory) / "passed.txt"
            incomplete = Path(directory) / "incomplete.txt"
            passed.write_text("OK (2 tests)\nMOMENTUM_RUNNER_EXIT=0\n", encoding="utf-8")
            incomplete.write_text("MOMENTUM_RUNNER_EXIT=0\n", encoding="utf-8")
            with self.assertRaises(InvalidInstrumentationResult):
                parse_modules([("data", passed), ("app", incomplete)])

    def assert_invalid(self, content: str) -> None:
        with self.assertRaises(InvalidInstrumentationResult):
            parse_module_result("app", content)


if __name__ == "__main__":
    unittest.main()
