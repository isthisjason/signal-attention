import argparse
import json
import sys
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

sys.path.append(str(Path(__file__).resolve().parents[1]))

from app.services.market_regime_experiment import describe_path, load_experiment_registry
from app.services.market_regime_governance import (
    ExperimentGates,
    eligible_promotion_candidates,
    evaluate_promotion_gates,
    select_promotion_candidate,
)


def main() -> None:
    args = parse_args()
    registry = load_experiment_registry(args.experiments_dir / "index.json")
    gates = ExperimentGates(
        min_accuracy=args.min_accuracy,
        min_lift=args.min_lift,
        require_holdout=not args.allow_full_evaluation,
    )
    summary = build_promotion_summary(registry, gates)
    write_summary(args.output, summary)
    print(json.dumps(summary, indent=2))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Promote the best eligible market-regime experiment run.")
    parser.add_argument("--experiments-dir", type=Path, default=Path("models/experiments"))
    parser.add_argument("--output", type=Path, default=Path("models/experiments/promoted-market-regime.json"))
    parser.add_argument("--min-accuracy", type=float, default=0.60)
    parser.add_argument("--min-lift", type=float, default=0.05)
    parser.add_argument("--allow-full-evaluation", action="store_true")
    return parser.parse_args()


def build_promotion_summary(registry: dict[str, Any], gates: ExperimentGates = ExperimentGates()) -> dict[str, Any]:
    candidates = eligible_promotion_candidates(registry, gates)
    selected = select_promotion_candidate(registry, gates)
    artifact_check = verify_selected_artifact(selected)
    status = "promoted" if selected and artifact_check["matchesRecordedHash"] else "no_eligible_run"
    return {
        "generatedAt": datetime.now(UTC).isoformat(),
        "status": status,
        "selectedRun": selected,
        "artifactCheck": artifact_check,
        "runnableManifest": build_runnable_manifest(selected, artifact_check),
        "eligibleRunCount": len(candidates),
        "eligibleRuns": [summarize_run(candidate) for candidate in candidates],
        "rejectedRuns": [
            {
                "name": entry.get("name"),
                "runId": entry.get("runId"),
                "promotionGate": evaluate_promotion_gates(entry, gates),
            }
            for entry in registry.get("experiments", [])
            if not evaluate_promotion_gates(entry, gates)["eligible"]
        ],
    }


def verify_selected_artifact(selected: dict[str, Any] | None) -> dict[str, Any]:
    if selected is None:
        return {
            "path": None,
            "expectedSha256": None,
            "actualSha256": None,
            "exists": False,
            "matchesRecordedHash": False,
            "failure": "no eligible run selected",
        }

    artifact = selected.get("artifact") or selected.get("evaluation", {}).get("artifact") or {}
    artifact_path = artifact.get("path")
    expected_hash = artifact.get("sha256")
    if not isinstance(artifact_path, str) or not artifact_path:
        return artifact_check_failure(artifact_path, expected_hash, "selected run artifact path is missing")

    actual = describe_path(Path(artifact_path))
    if actual["sha256"] is None:
        return artifact_check_failure(artifact_path, expected_hash, "selected run artifact file is missing")
    if actual["sha256"] != expected_hash:
        return {
            "path": artifact_path,
            "expectedSha256": expected_hash,
            "actualSha256": actual["sha256"],
            "exists": True,
            "matchesRecordedHash": False,
            "failure": "selected run artifact hash does not match registry",
        }

    return {
        "path": artifact_path,
        "expectedSha256": expected_hash,
        "actualSha256": actual["sha256"],
        "exists": True,
        "matchesRecordedHash": True,
        "failure": None,
    }


def artifact_check_failure(path: Any, expected_hash: Any, failure: str) -> dict[str, Any]:
    return {
        "path": path if isinstance(path, str) else None,
        "expectedSha256": expected_hash if isinstance(expected_hash, str) else None,
        "actualSha256": None,
        "exists": False,
        "matchesRecordedHash": False,
        "failure": failure,
    }


def build_runnable_manifest(selected: dict[str, Any] | None, artifact_check: dict[str, Any]) -> dict[str, Any] | None:
    if selected is None:
        return None
    artifact_path = artifact_check.get("path")
    if not artifact_path:
        return None
    # The manifest is intentionally env-oriented so a local runner can copy it into shell,
    # Compose, or an IDE without treating promotion as production deployment.
    return {
        "mode": "auto",
        "artifactPath": artifact_path,
        "environment": {
            "MARKET_REGIME_MODE": "auto",
            "MARKET_REGIME_ARTIFACT_PATH": artifact_path,
        },
        "note": "local research promotion only; rules fallback remains available",
    }


def summarize_run(entry: dict[str, Any]) -> dict[str, Any]:
    evaluation = entry.get("evaluation") or {}
    training = entry.get("training") or {}
    return {
        "name": entry.get("name"),
        "runId": entry.get("runId"),
        "accuracy": evaluation.get("accuracy"),
        "liftOverBaseline": evaluation.get("liftOverBaseline"),
        "validationAccuracy": training.get("validationAccuracy"),
        "artifact": entry.get("artifact") or evaluation.get("artifact"),
    }


def write_summary(output: Path, summary: dict[str, Any]) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
