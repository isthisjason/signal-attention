import json

from app.services.market_regime_governance import ExperimentGates
from scripts.promote_market_regime_experiment import build_promotion_summary, write_summary


def test_build_promotion_summary_selects_best_eligible_run() -> None:
    registry = {
        "experiments": [
            {
                "name": "weak",
                "runId": "run-1",
                "artifact": {"sha256": "one"},
                "evaluation": {"accuracy": 0.61, "liftOverBaseline": 0.06, "evaluationScope": "holdout"},
                "training": {"validationAccuracy": 0.7},
            },
            {
                "name": "strong",
                "runId": "run-2",
                "artifact": {"sha256": "two"},
                "evaluation": {"accuracy": 0.75, "liftOverBaseline": 0.1, "evaluationScope": "holdout"},
                "training": {"validationAccuracy": 0.5},
            },
        ]
    }

    summary = build_promotion_summary(registry)

    assert summary["status"] == "promoted"
    assert summary["selectedRun"]["runId"] == "run-2"
    assert summary["eligibleRunCount"] == 2
    assert [run["runId"] for run in summary["eligibleRuns"]] == ["run-2", "run-1"]


def test_build_promotion_summary_reports_rejected_runs() -> None:
    registry = {
        "experiments": [
            {
                "name": "bad",
                "runId": "run-1",
                "artifact": {"sha256": "one"},
                "evaluation": {"accuracy": 0.4, "liftOverBaseline": 0.01, "evaluationScope": "full"},
            }
        ]
    }

    summary = build_promotion_summary(registry, ExperimentGates(min_accuracy=0.6, min_lift=0.05))

    assert summary["status"] == "no_eligible_run"
    assert summary["selectedRun"] is None
    assert summary["rejectedRuns"][0]["promotionGate"]["eligible"] is False


def test_write_summary_creates_parent_directory(tmp_path) -> None:
    output = tmp_path / "experiments" / "promoted.json"

    write_summary(output, {"status": "promoted"})

    assert json.loads(output.read_text(encoding="utf-8")) == {"status": "promoted"}
