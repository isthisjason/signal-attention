from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class ExperimentGates:
    min_accuracy: float = 0.60
    min_lift: float = 0.05
    require_holdout: bool = True


def evaluate_promotion_gates(entry: dict[str, Any], gates: ExperimentGates = ExperimentGates()) -> dict[str, Any]:
    evaluation = entry.get("evaluation") or {}
    artifact = entry.get("artifact") or evaluation.get("artifact") or {}
    failures: list[str] = []

    accuracy = evaluation.get("accuracy")
    lift = evaluation.get("liftOverBaseline")
    if not evaluation:
        failures.append("missing evaluation metrics")
    if gates.require_holdout and evaluation.get("evaluationScope") != "holdout":
        failures.append("evaluation scope is not holdout")
    if not isinstance(accuracy, (int, float)) or accuracy < gates.min_accuracy:
        failures.append(f"accuracy is below {gates.min_accuracy}")
    if not isinstance(lift, (int, float)) or lift < gates.min_lift:
        failures.append(f"lift over baseline is below {gates.min_lift}")
    if not artifact.get("sha256"):
        failures.append("artifact hash is missing")

    return {
        "eligible": not failures,
        "failures": failures,
        "gates": {
            "minAccuracy": gates.min_accuracy,
            "minLiftOverBaseline": gates.min_lift,
            "requireHoldout": gates.require_holdout,
        },
    }
