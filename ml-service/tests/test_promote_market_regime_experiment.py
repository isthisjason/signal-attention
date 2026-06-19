import json
import hashlib

from app.services.market_regime_governance import ExperimentGates
from scripts.promote_market_regime_experiment import build_promotion_summary, write_summary


def artifact(path, content: bytes = b"artifact") -> dict:
    path.write_bytes(content)
    return {
        "path": str(path),
        "name": path.name,
        "sha256": hashlib.sha256(content).hexdigest(),
    }


def test_build_promotion_summary_selects_best_eligible_run(tmp_path) -> None:
    weak_artifact = artifact(tmp_path / "weak.pt", b"weak")
    strong_artifact = artifact(tmp_path / "strong.pt", b"strong")
    registry = {
        "experiments": [
            {
                "name": "weak",
                "runId": "run-1",
                "artifact": weak_artifact,
                "evaluation": {"accuracy": 0.61, "liftOverBaseline": 0.06, "evaluationScope": "holdout"},
                "training": {"validationAccuracy": 0.7},
            },
            {
                "name": "strong",
                "runId": "run-2",
                "artifact": strong_artifact,
                "evaluation": {"accuracy": 0.75, "liftOverBaseline": 0.1, "evaluationScope": "holdout"},
                "training": {"validationAccuracy": 0.5},
            },
        ]
    }

    summary = build_promotion_summary(registry)

    assert summary["status"] == "promoted"
    assert summary["selectedRun"]["runId"] == "run-2"
    assert summary["artifactCheck"]["matchesRecordedHash"] is True
    assert summary["runnableManifest"]["environment"]["MARKET_REGIME_MODE"] == "auto"
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


def test_build_promotion_summary_rejects_missing_selected_artifact(tmp_path) -> None:
    registry = {
        "experiments": [
            {
                "name": "missing",
                "runId": "run-1",
                "artifact": {"path": str(tmp_path / "missing.pt"), "sha256": "expected"},
                "evaluation": {"accuracy": 0.75, "liftOverBaseline": 0.1, "evaluationScope": "holdout"},
            }
        ]
    }

    summary = build_promotion_summary(registry)

    assert summary["status"] == "no_eligible_run"
    assert summary["selectedRun"]["runId"] == "run-1"
    assert summary["artifactCheck"]["failure"] == "selected run artifact file is missing"


def test_build_promotion_summary_rejects_changed_selected_artifact(tmp_path) -> None:
    artifact_path = tmp_path / "changed.pt"
    artifact_path.write_bytes(b"current")
    registry = {
        "experiments": [
            {
                "name": "changed",
                "runId": "run-1",
                "artifact": {"path": str(artifact_path), "sha256": hashlib.sha256(b"old").hexdigest()},
                "evaluation": {"accuracy": 0.75, "liftOverBaseline": 0.1, "evaluationScope": "holdout"},
            }
        ]
    }

    summary = build_promotion_summary(registry)

    assert summary["status"] == "no_eligible_run"
    assert summary["artifactCheck"]["exists"] is True
    assert summary["artifactCheck"]["failure"] == "selected run artifact hash does not match registry"


def test_write_summary_creates_parent_directory(tmp_path) -> None:
    output = tmp_path / "experiments" / "promoted.json"

    write_summary(output, {"status": "promoted"})

    assert json.loads(output.read_text(encoding="utf-8")) == {"status": "promoted"}
