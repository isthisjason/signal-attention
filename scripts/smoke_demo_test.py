#!/usr/bin/env python3
"""Unit tests for smoke_demo helper validation."""

from __future__ import annotations

import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
import smoke_demo


class SmokeDemoHelperTests(unittest.TestCase):
    def test_only_duplicate_rejections_accepts_existing_candles(self) -> None:
        self.assertTrue(
            smoke_demo.only_duplicate_rejections(
                {
                    "errors": [
                        {"message": "Duplicate candle already exists"},
                        {"message": "Duplicate candle already exists"},
                    ]
                }
            )
        )

    def test_only_duplicate_rejections_rejects_mixed_errors(self) -> None:
        self.assertFalse(
            smoke_demo.only_duplicate_rejections(
                {
                    "errors": [
                        {"message": "Duplicate candle already exists"},
                        {"message": "Invalid close price"},
                    ]
                }
            )
        )

    def test_require_keys_reports_missing_response_fields(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "Backtest response missing keys: status"):
            smoke_demo.require_keys({"id": 1}, ("id", "status"), "Backtest")


if __name__ == "__main__":
    unittest.main()
