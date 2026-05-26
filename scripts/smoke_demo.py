#!/usr/bin/env python3
"""Smoke checks for a running SignalAttention local stack."""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


@dataclass(frozen=True)
class Config:
    backend_url: str
    frontend_url: str
    ml_url: str


def parse_args() -> Config:
    parser = argparse.ArgumentParser(description="Run SignalAttention smoke checks.")
    parser.add_argument("--backend-url", default="http://localhost:8080")
    parser.add_argument("--frontend-url", default="http://localhost:5173")
    parser.add_argument("--ml-url", default="http://localhost:8000")
    args = parser.parse_args()
    return Config(
        backend_url=args.backend_url.rstrip("/"),
        frontend_url=args.frontend_url.rstrip("/"),
        ml_url=args.ml_url.rstrip("/"),
    )


def request_json(url: str) -> Any:
    request = Request(url, headers={"Accept": "application/json"})
    with urlopen(request, timeout=10) as response:
        payload = response.read().decode("utf-8")
    return json.loads(payload) if payload else None


def request_text(url: str) -> str:
    request = Request(url)
    with urlopen(request, timeout=10) as response:
        return response.read().decode("utf-8")


def check(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def check_stack(config: Config) -> None:
    ml_health = request_json(f"{config.ml_url}/health")
    check(ml_health == {"status": "ok"}, "ML service health check did not return ok.")

    openapi = request_json(f"{config.backend_url}/v3/api-docs")
    check("paths" in openapi, "Backend OpenAPI document did not include paths.")

    frontend = request_text(config.frontend_url)
    check("SignalAttention" in frontend or "<div id=\"root\"" in frontend, "Frontend did not render the app shell.")


def main() -> int:
    config = parse_args()
    try:
        check_stack(config)
    except (HTTPError, URLError, TimeoutError, RuntimeError, json.JSONDecodeError) as error:
        print(f"smoke check failed: {error}", file=sys.stderr)
        return 1

    print("stack smoke checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
