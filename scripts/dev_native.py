#!/usr/bin/env python3
"""Preflight check for a native local run without Docker Compose."""

from __future__ import annotations

import argparse
import shutil
import socket
import sys
from dataclasses import dataclass


@dataclass(frozen=True)
class NativeConfig:
    postgres_host: str
    postgres_port: int


def parse_args(argv: list[str] | None = None) -> NativeConfig:
    parser = argparse.ArgumentParser(description="Check native SignalAttention prerequisites.")
    parser.add_argument("--postgres-host", default="localhost")
    parser.add_argument("--postgres-port", type=int, default=5432)
    args = parser.parse_args(argv)
    return NativeConfig(postgres_host=args.postgres_host, postgres_port=args.postgres_port)


def command_available(name: str) -> bool:
    return shutil.which(name) is not None


def postgres_reachable(host: str, port: int, timeout_seconds: float = 2.0) -> bool:
    try:
        with socket.create_connection((host, port), timeout=timeout_seconds):
            return True
    except OSError:
        return False


def missing_requirements(config: NativeConfig) -> list[str]:
    missing: list[str] = []
    if not command_available("java"):
        missing.append("java (need Java 21)")
    if not command_available("python3"):
        missing.append("python3")
    if not command_available("node"):
        missing.append("node (need Node.js 24 for the workbench)")
    if not postgres_reachable(config.postgres_host, config.postgres_port):
        missing.append(
            f"postgres at {config.postgres_host}:{config.postgres_port} "
            "(install PostgreSQL locally or enable Docker Desktop WSL integration and run `docker compose up postgres`)"
        )
    return missing


def start_commands() -> str:
    return """# Native local run (rules mode, no Compose app containers)

export POSTGRES_HOST=localhost
export POSTGRES_PORT=5432
export POSTGRES_DB=signalattention
export POSTGRES_USER=signalattention
export POSTGRES_PASSWORD=signalattention
export ML_SERVICE_URL=http://localhost:8000
export VITE_API_BASE_URL=http://localhost:8080

# Terminal 1
cd ml-service && python3 -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload

# Terminal 2
cd backend && ./mvnw spring-boot:run

# Terminal 3
cd frontend && npm run dev
"""


def main(argv: list[str] | None = None) -> int:
    config = parse_args(argv)
    missing = missing_requirements(config)
    if missing:
        print("native preflight failed:", file=sys.stderr)
        for item in missing:
            print(f"- {item}", file=sys.stderr)
        if not command_available("docker"):
            print("docker is also missing; Compose is not available in this environment.", file=sys.stderr)
        return 1

    print("native preflight passed")
    print(start_commands())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
