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
    timeout_seconds: float


def parse_args(argv: list[str] | None = None) -> Config:
    parser = argparse.ArgumentParser(description="Run SignalAttention smoke checks.")
    parser.add_argument("--backend-url", default="http://localhost:8080")
    parser.add_argument("--frontend-url", default="http://localhost:5173")
    parser.add_argument("--ml-url", default="http://localhost:8000")
    parser.add_argument("--sample-csv", default="data/btc-usd-1h-sample.csv")
    parser.add_argument("--timeout-seconds", type=float, default=10.0)
    args = parser.parse_args(argv)
    return Config(
        backend_url=args.backend_url.rstrip("/"),
        frontend_url=args.frontend_url.rstrip("/"),
        ml_url=args.ml_url.rstrip("/"),
        sample_csv=Path(args.sample_csv),
        timeout_seconds=args.timeout_seconds,
    )


def request_json(url: str, timeout_seconds: float) -> Any:
    request = Request(url, headers={"Accept": "application/json"})
    payload = read_response(request, timeout_seconds)
    return json.loads(payload) if payload else None


def request_text(url: str, timeout_seconds: float) -> str:
    request = Request(url)
    return read_response(request, timeout_seconds)


def request_json_method(url: str, method: str, timeout_seconds: float) -> Any:
    request = Request(url, headers={"Accept": "application/json"}, method=method)
    payload = read_response(request, timeout_seconds)
    return json.loads(payload) if payload else None


def post_json(url: str, timeout_seconds: float, body: dict[str, Any] | None = None) -> Any:
    payload = json.dumps(body or {}).encode("utf-8")
    request = Request(
        url,
        data=payload,
        headers={"Accept": "application/json", "Content-Type": "application/json"},
        method="POST",
    )
    response_payload = read_response(request, timeout_seconds)
    return json.loads(response_payload) if response_payload else None


def post_multipart_file(url: str, field_name: str, file_path: Path, timeout_seconds: float) -> Any:
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
    payload = read_response(request, timeout_seconds)
    return json.loads(payload) if payload else None


def read_response(request: Request, timeout_seconds: float) -> str:
    try:
        with urlopen(request, timeout=timeout_seconds) as response:
            return response.read().decode("utf-8")
    except HTTPError as error:
        raise RuntimeError(format_http_error(request, error)) from error


def format_http_error(request: Request, error: HTTPError) -> str:
    body = ""
    if error.fp is not None:
        body = error.read().decode("utf-8", errors="replace").strip()

    message = f"{request.get_method()} {request.full_url} failed with HTTP {error.code}"
    if body:
        return f"{message}: {body}"
    return message


def check(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def log_step(label: str) -> None:
    print(f"[smoke] {label}")


def require_keys(payload: dict[str, Any], keys: tuple[str, ...], context: str) -> None:
    missing = [key for key in keys if key not in payload]
    check(not missing, f"{context} response missing keys: {', '.join(missing)}.")


def only_duplicate_rejections(import_summary: dict[str, Any]) -> bool:
    errors = import_summary.get("errors", [])
    # Re-running the demo should not fail just because the sample candles are already in Postgres.
    return bool(errors) and all("Duplicate candle already exists" in error["message"] for error in errors)


def validate_model_status(model_status: dict[str, Any]) -> None:
    require_keys(
        model_status,
        (
            "mode",
            "effectiveMode",
            "classifierSource",
            "ready",
            "artifactExists",
            "featureVersion",
            "promotionStatus",
            "promotionArtifactMatches",
            "promotionWarnings",
            "warnings",
        ),
        "Market regime status",
    )
    check(model_status["ready"] is True, "Market regime model status was not ready.")
    check(isinstance(model_status["warnings"], list), "Market regime status warnings field was not a list.")
    check(
        isinstance(model_status["promotionWarnings"], list),
        "Market regime status promotionWarnings field was not a list.",
    )
    if model_status["promotionStatus"] == "promoted":
        # The default stack does not need an artifact, but a promoted local artifact should prove its hash.
        # Promoted mode is optional; when present, the smoke check confirms the promotion is verified.
        check(model_status.get("promotedRunId"), "Promoted market regime status did not include a run id.")
        check(
            model_status["promotionArtifactMatches"] is True,
            "Promoted market regime artifact did not match the recorded hash.",
        )


def validate_model_lab(model_lab: dict[str, Any]) -> None:
    require_keys(model_lab, ("summary", "runs", "incompleteRuns", "promotion", "warnings"), "Model lab diagnostics")
    require_keys(
        model_lab["summary"],
        ("totalRuns", "trainedRuns", "evaluatedRuns", "promotionEligibleRuns"),
        "Model lab summary",
    )
    check(isinstance(model_lab["runs"], list), "Model lab runs field was not a list.")
    check(isinstance(model_lab["incompleteRuns"], list), "Model lab incompleteRuns field was not a list.")
    check(isinstance(model_lab["warnings"], list), "Model lab warnings field was not a list.")

    for field_name in ("totalRuns", "trainedRuns", "evaluatedRuns", "promotionEligibleRuns"):
        check(
            isinstance(model_lab["summary"][field_name], int),
            f"Model lab summary {field_name} was not an integer.",
        )


def validate_regime_robustness(robustness: dict[str, Any], regime_run_id: int, backtest_id: int) -> None:
    require_keys(
        robustness,
        ("regimeRunId", "backtestId", "symbol", "timeframe", "reviewLabel", "qualitySummary", "reviewReasons", "regimes"),
        "Regime robustness",
    )
    check(int(robustness["regimeRunId"]) == regime_run_id, "Regime robustness returned the wrong regime run id.")
    check(int(robustness["backtestId"]) == backtest_id, "Regime robustness returned the wrong backtest id.")
    check(
        robustness["reviewLabel"] in {"stable", "mixed", "needs_review"},
        "Regime robustness returned an unknown review label.",
    )
    check(isinstance(robustness["reviewReasons"], list), "Regime robustness reviewReasons field was not a list.")
    check(isinstance(robustness["regimes"], list), "Regime robustness regimes field was not a list.")
    require_keys(
        robustness["qualitySummary"],
        ("averageConfidence", "baselineDisagreementRate", "anomalyCount"),
        "Regime robustness quality summary",
    )


def check_stack(config: Config) -> None:
    # First prove the three user-facing services are reachable.
    log_step("checking ML service health")
    ml_health = request_json(f"{config.ml_url}/health", config.timeout_seconds)
    check(ml_health == {"status": "ok"}, "ML service health check did not return ok.")

    log_step("checking backend OpenAPI")
    openapi = request_json(f"{config.backend_url}/v3/api-docs", config.timeout_seconds)
    check("paths" in openapi, "Backend OpenAPI document did not include paths.")

    log_step("checking frontend shell")
    frontend = request_text(config.frontend_url, config.timeout_seconds)
    check("SignalAttention" in frontend or "<div id=\"root\"" in frontend, "Frontend did not render the app shell.")


def check_core_workflow(config: Config) -> tuple[int, int]:
    check(config.sample_csv.exists(), f"Sample CSV does not exist: {config.sample_csv}")

    # Core workflow mirrors the smallest useful demo: data, strategy, backtest, risk score.
    log_step("importing market data")
    import_summary = post_multipart_file(
        f"{config.backend_url}/api/market-data/import",
        "file",
        config.sample_csv,
        config.timeout_seconds,
    )
    require_keys(import_summary, ("totalRows", "rowsImported", "rowsRejected", "errors"), "Market data import")
    check(import_summary["totalRows"] > 0, "Market data import did not read any rows.")
    check(
        import_summary["rowsImported"] > 0 or only_duplicate_rejections(import_summary),
        "Market data import neither imported rows nor found existing sample candles.",
    )

    log_step("checking market data quality")
    quality = request_json(
        f"{config.backend_url}/api/market-data/quality?symbol=BTC-USD&timeframe=1h",
        config.timeout_seconds,
    )
    require_keys(
        quality,
        (
            "symbol",
            "timeframe",
            "candleCount",
            "expectedIntervalMinutes",
            "gapCount",
            "invalidOhlcCount",
            "zeroOrNegativeVolumeCount",
            "warnings",
        ),
        "Market data quality",
    )
    check(quality["symbol"] == "BTC-USD" and quality["timeframe"] == "1h", "Market data quality checked the wrong market.")
    check(quality["candleCount"] > 0, "Market data quality did not find imported sample candles.")
    check(isinstance(quality["warnings"], list) and quality["warnings"], "Market data quality did not include warnings.")

    log_step("creating strategy")
    strategy = post_json(
        f"{config.backend_url}/api/strategies",
        config.timeout_seconds,
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

    log_step("running backtest")
    backtest = post_json(
        f"{config.backend_url}/api/strategies/{strategy_id}/backtests",
        config.timeout_seconds,
        {
            "startDate": "2024-01-01T00:00:00Z",
            "endDate": "2024-01-10T00:00:00Z",
        },
    )
    require_keys(backtest, ("id", "status", "tradeCount", "totalReturn", "maxDrawdown"), "Backtest")
    backtest_id = int(backtest["id"])
    check(backtest["status"] == "COMPLETED", "Backtest did not complete.")

    log_step("checking backtest metrics and chart series")
    metrics = request_json(f"{config.backend_url}/api/backtests/{backtest_id}/metrics", config.timeout_seconds)
    require_keys(metrics, ("totalReturn", "maxDrawdown", "winRate", "tradeCount"), "Backtest metrics")
    check(metrics["tradeCount"] == backtest["tradeCount"], "Backtest metrics trade count did not match run response.")

    trades = request_json(f"{config.backend_url}/api/backtests/{backtest_id}/trades", config.timeout_seconds)
    check(isinstance(trades, list), "Backtest trades response was not a list.")

    equity_series = request_json(f"{config.backend_url}/api/backtests/{backtest_id}/equity-series", config.timeout_seconds)
    check(isinstance(equity_series, list) and equity_series, "Backtest equity series response was empty.")
    require_keys(equity_series[0], ("timestamp", "equity"), "Backtest equity point")

    drawdown_series = request_json(
        f"{config.backend_url}/api/backtests/{backtest_id}/drawdown-series",
        config.timeout_seconds,
    )
    check(isinstance(drawdown_series, list) and drawdown_series, "Backtest drawdown series response was empty.")
    require_keys(drawdown_series[0], ("timestamp", "drawdownPercent"), "Backtest drawdown point")

    log_step("scoring ML risk")
    risk = post_json(f"{config.backend_url}/api/backtests/{backtest_id}/ml-risk-score", config.timeout_seconds)
    require_keys(risk, ("riskScore", "riskLabel", "reasons"), "ML risk score")
    check(isinstance(risk["reasons"], list) and risk["reasons"], "ML risk score did not include reasons.")

    refreshed = request_json(f"{config.backend_url}/api/backtests/{backtest_id}", config.timeout_seconds)
    require_keys(refreshed, ("id", "mlRiskScore", "mlRiskLabel"), "Refreshed backtest")
    check(refreshed["mlRiskLabel"] == risk["riskLabel"], "ML risk label was not persisted.")

    return strategy_id, backtest_id


def check_paper_workflow(config: Config, strategy_id: int) -> int:
    # Paper trading stays simulated, but it should still exercise session lifecycle and orders.
    log_step("creating paper session")
    session = post_json(
        f"{config.backend_url}/api/strategies/{strategy_id}/paper-sessions",
        config.timeout_seconds,
        {"initialBalance": 100000},
    )
    require_keys(session, ("id", "status", "cashBalance"), "Paper session create")
    session_id = int(session["id"])
    check(session["status"] == "CREATED", "Paper session was not created.")

    log_step("starting paper session")
    started = request_json_method(
        f"{config.backend_url}/api/paper-sessions/{session_id}/start",
        "PATCH",
        config.timeout_seconds,
    )
    require_keys(started, ("id", "status", "startedAt"), "Paper session start")
    check(started["status"] == "RUNNING", "Paper session did not start.")

    log_step("submitting paper order")
    order = post_json(
        f"{config.backend_url}/api/paper-sessions/{session_id}/orders",
        config.timeout_seconds,
        {
            "side": "BUY",
            "symbol": "BTC-USD",
            "quantity": 0.25,
            "price": 42000,
        },
    )
    require_keys(order, ("id", "status", "side", "notional"), "Paper order")
    check(order["status"] in {"FILLED", "REJECTED"}, "Paper order did not return a terminal status.")

    log_step("checking paper orders, positions, and summary")
    orders = request_json(f"{config.backend_url}/api/paper-sessions/{session_id}/orders", config.timeout_seconds)
    check(isinstance(orders, list) and orders, "Paper orders response did not include the submitted order.")

    positions = request_json(f"{config.backend_url}/api/paper-sessions/{session_id}/positions", config.timeout_seconds)
    check(isinstance(positions, list), "Paper positions response was not a list.")

    summary = request_json(f"{config.backend_url}/api/paper-sessions/{session_id}/summary", config.timeout_seconds)
    require_keys(summary, ("status", "cashBalance", "totalEquity", "openPositions"), "Paper summary")

    log_step("replaying candles through paper session")
    replay = post_json(
        f"{config.backend_url}/api/paper-sessions/{session_id}/replay",
        config.timeout_seconds,
        {
            "startDate": "2024-01-01T00:00:00Z",
            "endDate": "2024-01-10T00:00:00Z",
            "maxCandles": 250,
        },
    )
    require_keys(replay, ("candlesRead", "signalsProcessed", "filledOrders", "rejectedOrders"), "Paper replay")

    log_step("stopping paper session")
    stopped = request_json_method(
        f"{config.backend_url}/api/paper-sessions/{session_id}/stop",
        "PATCH",
        config.timeout_seconds,
    )
    require_keys(stopped, ("id", "status", "stoppedAt"), "Paper session stop")
    check(stopped["status"] == "STOPPED", "Paper session did not stop.")

    return session_id


def check_analysis_workflow(config: Config, strategy_id: int, backtest_id: int) -> None:
    # Analysis checks prove the created demo data flows into dashboard and audit views.
    log_step("checking dashboard summary")
    summary = request_json(f"{config.backend_url}/api/dashboard/summary", config.timeout_seconds)
    require_keys(
        summary,
        ("strategyCount", "backtestCount", "activePaperSessionCount", "recentAuditEvents"),
        "Dashboard summary",
    )
    check(summary["strategyCount"] >= 1, "Dashboard summary did not include created strategies.")
    check(summary["backtestCount"] >= 1, "Dashboard summary did not include created backtests.")

    log_step("checking strategy performance")
    performance = request_json(f"{config.backend_url}/api/dashboard/strategy-performance", config.timeout_seconds)
    check(isinstance(performance, list), "Strategy performance response was not a list.")
    check(
        any(item["strategyId"] == strategy_id for item in performance),
        "Strategy performance did not include the smoke strategy.",
    )

    log_step("checking risk alerts")
    alerts = request_json(f"{config.backend_url}/api/dashboard/risk-alerts", config.timeout_seconds)
    check(isinstance(alerts, list), "Risk alerts response was not a list.")

    log_step("checking market regime")
    regime = request_json(
        f"{config.backend_url}/api/market-regime?symbol=BTC-USD&timeframe=1h&limit=128",
        config.timeout_seconds,
    )
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

    log_step("checking market regime model status")
    model_status = request_json(f"{config.backend_url}/api/market-regime/status", config.timeout_seconds)
    validate_model_status(model_status)

    log_step("checking model lab diagnostics")
    model_lab = request_json(f"{config.backend_url}/api/market-regime/experiments", config.timeout_seconds)
    validate_model_lab(model_lab)

    log_step("creating persisted regime run")
    regime_run = post_json(
        f"{config.backend_url}/api/regime-runs",
        config.timeout_seconds,
        {
            "symbol": "BTC-USD",
            "timeframe": "1h",
            "startDate": "2024-01-01T00:00:00Z",
            "endDate": "2024-01-10T00:00:00Z",
            "windowSize": 20,
            "stride": 8,
            "includeAnomalies": True,
            "backtestId": backtest_id,
        },
    )
    require_keys(
        regime_run,
        ("id", "symbol", "timeframe", "effectiveMode", "pointCount", "qualitySummary", "points", "tradeMarkers"),
        "Persisted regime run",
    )
    regime_run_id = int(regime_run["id"])
    check(regime_run["pointCount"] > 0 and regime_run["points"], "Persisted regime run did not include points.")
    require_keys(
        regime_run["points"][0],
        ("regimeLabel", "confidence", "baselineRegimeLabel", "disagreesWithBaseline"),
        "Persisted regime point",
    )

    log_step("checking persisted regime run retrieval")
    saved_regime_run = request_json(f"{config.backend_url}/api/regime-runs/{regime_run_id}", config.timeout_seconds)
    require_keys(saved_regime_run, ("id", "points", "candles"), "Saved regime run")
    check(int(saved_regime_run["id"]) == regime_run_id, "Saved regime run returned the wrong id.")

    recent_runs = request_json(
        f"{config.backend_url}/api/regime-runs?symbol=BTC-USD&timeframe=1h&limit=5",
        config.timeout_seconds,
    )
    check(
        isinstance(recent_runs, list) and any(int(run["id"]) == regime_run_id for run in recent_runs),
        "Regime run list did not include the saved run.",
    )
    check("qualitySummary" in recent_runs[0], "Regime run list did not include quality summary evidence.")

    log_step("checking regime run comparison")
    comparison = request_json(
        f"{config.backend_url}/api/regime-runs/comparison?symbol=BTC-USD&timeframe=1h&limit=5",
        config.timeout_seconds,
    )
    require_keys(comparison, ("symbol", "timeframe", "runs"), "Regime run comparison")
    check(
        any(int(item["run"]["id"]) == regime_run_id for item in comparison["runs"]),
        "Regime run comparison did not include the saved run.",
    )
    require_keys(comparison["runs"][0]["run"], ("qualitySummary", "pointCount"), "Regime comparison run")

    log_step("checking regime robustness review")
    robustness = request_json(
        f"{config.backend_url}/api/regime-runs/{regime_run_id}/robustness?backtestId={backtest_id}",
        config.timeout_seconds,
    )
    validate_regime_robustness(robustness, regime_run_id, backtest_id)

    log_step("checking attention evidence diagnostics")
    latest_window_end = regime_run["points"][-1]["windowEnd"]
    diagnostics = request_json(
        f"{config.backend_url}/api/market-regime/diagnostics?symbol=BTC-USD&timeframe=1h&limit=20&windowEnd={latest_window_end}",
        config.timeout_seconds,
    )
    require_keys(
        diagnostics,
        ("regimeLabel", "baselineRegimeLabel", "evidenceSource", "topTimesteps", "featureEvidence"),
        "Attention diagnostics",
    )
    check(diagnostics["topTimesteps"], "Attention diagnostics did not include timestep evidence.")
    snapshots = request_json(
        f"{config.backend_url}/api/market-regime/evidence-snapshots?symbol=BTC-USD&timeframe=1h&limit=5",
        config.timeout_seconds,
    )
    check(isinstance(snapshots, list) and snapshots, "Evidence snapshot list did not include the diagnostic run.")

    log_step("checking regime grouped backtest analysis")
    regime_analysis = request_json(
        f"{config.backend_url}/api/backtests/{backtest_id}/regime-analysis?regimeRunId={regime_run_id}",
        config.timeout_seconds,
    )
    require_keys(regime_analysis, ("backtestId", "regimeRunId", "regimes"), "Regime backtest analysis")
    check(int(regime_analysis["backtestId"]) == backtest_id, "Regime analysis returned the wrong backtest id.")
    check(isinstance(regime_analysis["regimes"], list), "Regime analysis regimes field was not a list.")

    log_step("checking anomaly analysis")
    anomaly = post_json(
        f"{config.backend_url}/api/anomaly-check",
        config.timeout_seconds,
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

    log_step("checking audit events")
    audit_events = request_json(f"{config.backend_url}/api/audit-events?limit=25", config.timeout_seconds)
    check(isinstance(audit_events, list) and audit_events, "Audit events response was empty.")
    require_keys(audit_events[0], ("id", "entityType", "entityId", "action", "message", "createdAt"), "Audit event")
    check(
        any(event["entityType"] == "BACKTEST" and str(event["entityId"]) == str(backtest_id) for event in audit_events),
        "Audit events did not include the smoke backtest.",
    )


def check_assistant_workflow(config: Config, strategy_id: int, backtest_id: int) -> None:
    # The assistant smoke path verifies reviewability without executing another mutating action.
    log_step("creating assistant session")
    session = post_json(f"{config.backend_url}/api/assistant/sessions", config.timeout_seconds, {"title": "Smoke assistant"})
    require_keys(session, ("id", "messages", "actions"), "Assistant session")
    session_id = int(session["id"])

    log_step("asking assistant for regime replay")
    updated = post_json(
        f"{config.backend_url}/api/assistant/sessions/{session_id}/messages",
        config.timeout_seconds,
        {
            "prompt": "Run a regime replay for review",
            "strategyId": strategy_id,
            "backtestId": backtest_id,
            "startDate": "2024-01-01T00:00:00Z",
            "endDate": "2024-01-10T00:00:00Z",
        },
    )
    require_keys(updated, ("id", "messages", "actions"), "Assistant message")
    check(len(updated["messages"]) >= 2, "Assistant did not persist the user and assistant messages.")
    proposed = [action for action in updated["actions"] if action["status"] == "PROPOSED"]
    check(proposed, "Assistant did not propose a reviewable action.")
    action = proposed[0]
    require_keys(action, ("id", "actionType", "status", "summary", "payloadJson"), "Assistant action")
    check(action["actionType"] == "RUN_REGIME_REPLAY", "Assistant proposed the wrong action type.")

    log_step("rejecting assistant action")
    rejected = post_json(
        f"{config.backend_url}/api/assistant/actions/{int(action['id'])}/reject",
        config.timeout_seconds,
    )
    require_keys(rejected, ("id", "status", "rejectedAt"), "Assistant action reject")
    check(rejected["status"] == "REJECTED", "Assistant action was not rejected.")

    log_step("asking assistant for model lab review")
    model_lab_review = post_json(
        f"{config.backend_url}/api/assistant/sessions/{session_id}/messages",
        config.timeout_seconds,
        {
            "prompt": "Review model lab promotion",
            "strategyId": strategy_id,
            "backtestId": backtest_id,
            "startDate": "2024-01-01T00:00:00Z",
            "endDate": "2024-01-10T00:00:00Z",
        },
    )
    require_keys(model_lab_review, ("id", "messages", "actions"), "Assistant model lab message")
    check(
        any(action["status"] == "PROPOSED" and action["actionType"] == "REVIEW_MODEL_LAB" for action in model_lab_review["actions"]),
        "Assistant did not propose a model lab review action.",
    )

    log_step("asking assistant for robustness review")
    robustness_review = post_json(
        f"{config.backend_url}/api/assistant/sessions/{session_id}/messages",
        config.timeout_seconds,
        {
            "prompt": "Review robustness stability",
            "strategyId": strategy_id,
            "backtestId": backtest_id,
            "startDate": "2024-01-01T00:00:00Z",
            "endDate": "2024-01-10T00:00:00Z",
        },
    )
    require_keys(robustness_review, ("id", "messages", "actions"), "Assistant robustness message")
    check(
        any(
            action["status"] == "PROPOSED" and action["actionType"] == "REVIEW_REGIME_ROBUSTNESS"
            for action in robustness_review["actions"]
        ),
        "Assistant did not propose a regime robustness review action.",
    )


def main() -> int:
    config = parse_args()
    try:
        check_stack(config)
        strategy_id, backtest_id = check_core_workflow(config)
        session_id = check_paper_workflow(config, strategy_id)
        check_analysis_workflow(config, strategy_id, backtest_id)
        check_assistant_workflow(config, strategy_id, backtest_id)
    except (HTTPError, URLError, TimeoutError, RuntimeError, json.JSONDecodeError) as error:
        print(f"smoke check failed: {error}", file=sys.stderr)
        return 1

    print(
        "stack, core, paper, analysis, and assistant workflow smoke checks passed "
        f"for strategy #{strategy_id}, backtest #{backtest_id}, session #{session_id}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
