#!/usr/bin/env python3
"""Smoke checks for a running SignalAttention local stack."""

from __future__ import annotations

import argparse
import json
import mimetypes
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


@dataclass(frozen=True)
class Config:
    backend_url: str
    frontend_url: str
    ml_url: str
    sample_csv: Path


def parse_args() -> Config:
    parser = argparse.ArgumentParser(description="Run SignalAttention smoke checks.")
    parser.add_argument("--backend-url", default="http://localhost:8080")
    parser.add_argument("--frontend-url", default="http://localhost:5173")
    parser.add_argument("--ml-url", default="http://localhost:8000")
    parser.add_argument("--sample-csv", default="data/btc-usd-1h-sample.csv")
    args = parser.parse_args()
    return Config(
        backend_url=args.backend_url.rstrip("/"),
        frontend_url=args.frontend_url.rstrip("/"),
        ml_url=args.ml_url.rstrip("/"),
        sample_csv=Path(args.sample_csv),
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


def request_json_method(url: str, method: str) -> Any:
    request = Request(url, headers={"Accept": "application/json"}, method=method)
    with urlopen(request, timeout=20) as response:
        payload = response.read().decode("utf-8")
    return json.loads(payload) if payload else None


def post_json(url: str, body: dict[str, Any] | None = None) -> Any:
    payload = json.dumps(body or {}).encode("utf-8")
    request = Request(
        url,
        data=payload,
        headers={"Accept": "application/json", "Content-Type": "application/json"},
        method="POST",
    )
    with urlopen(request, timeout=20) as response:
        response_payload = response.read().decode("utf-8")
    return json.loads(response_payload) if response_payload else None


def post_multipart_file(url: str, field_name: str, file_path: Path) -> Any:
    boundary = "signalattention-smoke-boundary"
    content_type = mimetypes.guess_type(file_path.name)[0] or "application/octet-stream"
    file_bytes = file_path.read_bytes()
    body = b"".join(
        [
            f"--{boundary}\r\n".encode("utf-8"),
            (
                f'Content-Disposition: form-data; name="{field_name}"; '
                f'filename="{file_path.name}"\r\n'
            ).encode("utf-8"),
            f"Content-Type: {content_type}\r\n\r\n".encode("utf-8"),
            file_bytes,
            f"\r\n--{boundary}--\r\n".encode("utf-8"),
        ]
    )
    request = Request(
        url,
        data=body,
        headers={
            "Accept": "application/json",
            "Content-Type": f"multipart/form-data; boundary={boundary}",
        },
        method="POST",
    )
    with urlopen(request, timeout=30) as response:
        payload = response.read().decode("utf-8")
    return json.loads(payload) if payload else None


def check(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def require_keys(payload: dict[str, Any], keys: tuple[str, ...], context: str) -> None:
    missing = [key for key in keys if key not in payload]
    check(not missing, f"{context} response missing keys: {', '.join(missing)}.")


def only_duplicate_rejections(import_summary: dict[str, Any]) -> bool:
    errors = import_summary.get("errors", [])
    return bool(errors) and all("Duplicate candle already exists" in error["message"] for error in errors)


def check_stack(config: Config) -> None:
    ml_health = request_json(f"{config.ml_url}/health")
    check(ml_health == {"status": "ok"}, "ML service health check did not return ok.")

    openapi = request_json(f"{config.backend_url}/v3/api-docs")
    check("paths" in openapi, "Backend OpenAPI document did not include paths.")

    frontend = request_text(config.frontend_url)
    check("SignalAttention" in frontend or "<div id=\"root\"" in frontend, "Frontend did not render the app shell.")


def check_core_workflow(config: Config) -> tuple[int, int]:
    check(config.sample_csv.exists(), f"Sample CSV does not exist: {config.sample_csv}")

    import_summary = post_multipart_file(
        f"{config.backend_url}/api/market-data/import",
        "file",
        config.sample_csv,
    )
    require_keys(import_summary, ("totalRows", "rowsImported", "rowsRejected", "errors"), "Market data import")
    check(import_summary["totalRows"] > 0, "Market data import did not read any rows.")
    check(
        import_summary["rowsImported"] > 0 or only_duplicate_rejections(import_summary),
        "Market data import neither imported rows nor found existing sample candles.",
    )

    strategy = post_json(
        f"{config.backend_url}/api/strategies",
        {
            "name": "BTC SMA Crossover Smoke",
            "symbol": "BTC-USD",
            "timeframe": "1h",
            "strategyType": "SMA_CROSSOVER",
            "rules": {
                "shortWindow": 3,
                "longWindow": 5,
                "initialBalance": 10000,
                "feePercent": 0.1,
                "positionSizePercent": 50,
            },
        },
    )
    require_keys(strategy, ("id", "symbol", "timeframe", "strategyType"), "Strategy create")
    strategy_id = int(strategy["id"])
    check(strategy["symbol"] == "BTC-USD" and strategy["timeframe"] == "1h", "Strategy create returned the wrong market.")

    backtest = post_json(
        f"{config.backend_url}/api/strategies/{strategy_id}/backtests",
        {
            "startDate": "2024-01-01T00:00:00Z",
            "endDate": "2024-01-10T00:00:00Z",
        },
    )
    require_keys(backtest, ("id", "status", "tradeCount", "totalReturn", "maxDrawdown"), "Backtest")
    backtest_id = int(backtest["id"])
    check(backtest["status"] == "COMPLETED", "Backtest did not complete.")

    metrics = request_json(f"{config.backend_url}/api/backtests/{backtest_id}/metrics")
    require_keys(metrics, ("totalReturn", "maxDrawdown", "winRate", "tradeCount"), "Backtest metrics")
    check(metrics["tradeCount"] == backtest["tradeCount"], "Backtest metrics trade count did not match run response.")

    trades = request_json(f"{config.backend_url}/api/backtests/{backtest_id}/trades")
    check(isinstance(trades, list), "Backtest trades response was not a list.")

    equity_series = request_json(f"{config.backend_url}/api/backtests/{backtest_id}/equity-series")
    check(isinstance(equity_series, list) and equity_series, "Backtest equity series response was empty.")
    require_keys(equity_series[0], ("timestamp", "equity"), "Backtest equity point")

    drawdown_series = request_json(f"{config.backend_url}/api/backtests/{backtest_id}/drawdown-series")
    check(isinstance(drawdown_series, list) and drawdown_series, "Backtest drawdown series response was empty.")
    require_keys(drawdown_series[0], ("timestamp", "drawdownPercent"), "Backtest drawdown point")

    risk = post_json(f"{config.backend_url}/api/backtests/{backtest_id}/ml-risk-score")
    require_keys(risk, ("riskScore", "riskLabel", "reasons"), "ML risk score")
    check(isinstance(risk["reasons"], list) and risk["reasons"], "ML risk score did not include reasons.")

    refreshed = request_json(f"{config.backend_url}/api/backtests/{backtest_id}")
    require_keys(refreshed, ("id", "mlRiskScore", "mlRiskLabel"), "Refreshed backtest")
    check(refreshed["mlRiskLabel"] == risk["riskLabel"], "ML risk label was not persisted.")

    return strategy_id, backtest_id


def check_paper_workflow(config: Config, strategy_id: int) -> int:
    session = post_json(
        f"{config.backend_url}/api/strategies/{strategy_id}/paper-sessions",
        {"initialBalance": 100000},
    )
    require_keys(session, ("id", "status", "cashBalance"), "Paper session create")
    session_id = int(session["id"])
    check(session["status"] == "CREATED", "Paper session was not created.")

    started = request_json_method(f"{config.backend_url}/api/paper-sessions/{session_id}/start", "PATCH")
    require_keys(started, ("id", "status", "startedAt"), "Paper session start")
    check(started["status"] == "RUNNING", "Paper session did not start.")

    order = post_json(
        f"{config.backend_url}/api/paper-sessions/{session_id}/orders",
        {
            "side": "BUY",
            "symbol": "BTC-USD",
            "quantity": 0.25,
            "price": 42000,
        },
    )
    require_keys(order, ("id", "status", "side", "notional"), "Paper order")
    check(order["status"] in {"FILLED", "REJECTED"}, "Paper order did not return a terminal status.")

    orders = request_json(f"{config.backend_url}/api/paper-sessions/{session_id}/orders")
    check(isinstance(orders, list) and orders, "Paper orders response did not include the submitted order.")

    positions = request_json(f"{config.backend_url}/api/paper-sessions/{session_id}/positions")
    check(isinstance(positions, list), "Paper positions response was not a list.")

    summary = request_json(f"{config.backend_url}/api/paper-sessions/{session_id}/summary")
    require_keys(summary, ("status", "cashBalance", "totalEquity", "openPositions"), "Paper summary")

    replay = post_json(
        f"{config.backend_url}/api/paper-sessions/{session_id}/replay",
        {
            "startDate": "2024-01-01T00:00:00Z",
            "endDate": "2024-01-10T00:00:00Z",
            "maxCandles": 250,
        },
    )
    require_keys(replay, ("candlesRead", "signalsProcessed", "filledOrders", "rejectedOrders"), "Paper replay")

    stopped = request_json_method(f"{config.backend_url}/api/paper-sessions/{session_id}/stop", "PATCH")
    require_keys(stopped, ("id", "status", "stoppedAt"), "Paper session stop")
    check(stopped["status"] == "STOPPED", "Paper session did not stop.")

    return session_id


def check_analysis_workflow(config: Config, strategy_id: int, backtest_id: int) -> None:
    summary = request_json(f"{config.backend_url}/api/dashboard/summary")
    require_keys(
        summary,
        ("strategyCount", "backtestCount", "activePaperSessionCount", "recentAuditEvents"),
        "Dashboard summary",
    )
    check(summary["strategyCount"] >= 1, "Dashboard summary did not include created strategies.")
    check(summary["backtestCount"] >= 1, "Dashboard summary did not include created backtests.")

    performance = request_json(f"{config.backend_url}/api/dashboard/strategy-performance")
    check(isinstance(performance, list), "Strategy performance response was not a list.")
    check(
        any(item["strategyId"] == strategy_id for item in performance),
        "Strategy performance did not include the smoke strategy.",
    )

    alerts = request_json(f"{config.backend_url}/api/dashboard/risk-alerts")
    check(isinstance(alerts, list), "Risk alerts response was not a list.")

    regime = request_json(f"{config.backend_url}/api/market-regime?symbol=BTC-USD&timeframe=1h&limit=128")
    require_keys(regime, ("regimeLabel", "confidence", "reasons", "features", "classifierSource"), "Market regime")
    require_keys(
        regime["features"],
        (
            "latestReturnPercent",
            "averageReturnPercent",
            "volatilityPercent",
            "trendSlopePercent",
            "smaDistancePercent",
            "volumeZScore",
        ),
        "Market regime features",
    )

    anomaly = post_json(
        f"{config.backend_url}/api/anomaly-check",
        {"symbol": "BTC-USD", "timeframe": "1h", "limit": 128},
    )
    require_keys(anomaly, ("anomalyScore", "anomalyLabel", "reasons", "features", "classifierSource"), "Anomaly check")
    require_keys(
        anomaly["features"],
        (
            "latestReturnPercent",
            "averageReturnPercent",
            "volatilityPercent",
            "trendSlopePercent",
            "smaDistancePercent",
            "volumeZScore",
        ),
        "Anomaly features",
    )

    audit_events = request_json(f"{config.backend_url}/api/audit-events?limit=25")
    check(isinstance(audit_events, list) and audit_events, "Audit events response was empty.")
    require_keys(audit_events[0], ("id", "entityType", "entityId", "action", "message", "createdAt"), "Audit event")
    check(
        any(event["entityType"] == "BACKTEST" and str(event["entityId"]) == str(backtest_id) for event in audit_events),
        "Audit events did not include the smoke backtest.",
    )


def main() -> int:
    config = parse_args()
    try:
        check_stack(config)
        strategy_id, backtest_id = check_core_workflow(config)
        session_id = check_paper_workflow(config, strategy_id)
        check_analysis_workflow(config, strategy_id, backtest_id)
    except (HTTPError, URLError, TimeoutError, RuntimeError, json.JSONDecodeError) as error:
        print(f"smoke check failed: {error}", file=sys.stderr)
        return 1

    print(
        "stack, core, paper, and analysis workflow smoke checks passed "
        f"for strategy #{strategy_id}, backtest #{backtest_id}, session #{session_id}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
