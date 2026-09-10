#!/usr/bin/env python3
"""Unit tests for native local-run preflight helpers."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import dev_native


class DevNativeTests(unittest.TestCase):
    def test_parse_args_defaults_to_localhost_postgres(self) -> None:
        config = dev_native.parse_args([])

        self.assertEqual(config.postgres_host, "localhost")
        self.assertEqual(config.postgres_port, 5432)

    def test_missing_requirements_reports_unreachable_postgres(self) -> None:
        config = dev_native.NativeConfig(postgres_host="localhost", postgres_port=5432)

        with (
            patch.object(dev_native, "command_available", return_value=True),
            patch.object(dev_native, "postgres_reachable", return_value=False),
        ):
            missing = dev_native.missing_requirements(config)

        self.assertEqual(len(missing), 1)
        self.assertIn("postgres at localhost:5432", missing[0])

    def test_missing_requirements_is_empty_when_tools_and_postgres_exist(self) -> None:
        config = dev_native.NativeConfig(postgres_host="localhost", postgres_port=5432)

        with (
            patch.object(dev_native, "command_available", return_value=True),
            patch.object(dev_native, "postgres_reachable", return_value=True),
        ):
            self.assertEqual(dev_native.missing_requirements(config), [])


if __name__ == "__main__":
    unittest.main()
