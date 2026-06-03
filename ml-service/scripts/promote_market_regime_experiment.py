import argparse
import json
import sys
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

sys.path.append(str(Path(__file__).resolve().parents[1]))

from app.services.market_regime_experiment import load_experiment_registry
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
    return {
        "generatedAt": datetime.now(UTC).isoformat(),
        "status": "promoted" if selected else "no_eligible_run",
        "selectedRun": selected,
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
