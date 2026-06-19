#!/usr/bin/env python3
"""Unit tests for smoke_demo helper validation."""

from __future__ import annotations

import io
import unittest
from pathlib import Path
import sys
from urllib.error import HTTPError
from urllib.request import Request

sys.path.insert(0, str(Path(__file__).resolve().parent))
import smoke_demo


class SmokeDemoHelperTests(unittest.TestCase):
    def test_parse_args_uses_default_timeout(self) -> None:
        config = smoke_demo.parse_args([])

        self.assertEqual(config.timeout_seconds, 10.0)

    def test_parse_args_accepts_custom_timeout(self) -> None:
        config = smoke_demo.parse_args(["--timeout-seconds", "2.5"])

        self.assertEqual(config.timeout_seconds, 2.5)

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

    def test_validate_model_status_accepts_default_rules_mode(self) -> None:
        smoke_demo.validate_model_status(
            {
                "mode": "rules",
                "effectiveMode": "rules",
                "classifierSource": "rules",
                "ready": True,
                "artifactExists": False,
                "featureVersion": "torch-market-regime-features/v1",
                "promotionStatus": None,
                "promotionArtifactMatches": None,
                "promotionWarnings": [],
                "warnings": [],
            }
        )

    def test_validate_model_status_requires_verified_promoted_artifact(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "recorded hash"):
            smoke_demo.validate_model_status(
                {
                    "mode": "auto",
                    "effectiveMode": "torch",
                    "classifierSource": "torch",
                    "ready": True,
                    "artifactExists": True,
                    "featureVersion": "torch-market-regime-features/v1",
                    "promotionStatus": "promoted",
                    "promotedRunId": "run-1",
                    "promotionArtifactMatches": False,
                    "promotionWarnings": [],
                    "warnings": [],
                }
            )

    def test_format_http_error_includes_method_url_status_and_body(self) -> None:
        request = Request("http://api.test/api/fails", method="POST")
        error = HTTPError(
            request.full_url,
            400,
            "Bad Request",
            hdrs={},
            fp=io.BytesIO(b'{"message":"Invalid request"}'),
        )

        self.assertEqual(
            smoke_demo.format_http_error(request, error),
            'POST http://api.test/api/fails failed with HTTP 400: {"message":"Invalid request"}',
        )

    def test_format_http_error_omits_empty_body(self) -> None:
        request = Request("http://api.test/api/fails")
        error = HTTPError(request.full_url, 500, "Server Error", hdrs={}, fp=io.BytesIO(b""))

        self.assertEqual(
            smoke_demo.format_http_error(request, error),
            "GET http://api.test/api/fails failed with HTTP 500",
        )


if __name__ == "__main__":
    unittest.main()
