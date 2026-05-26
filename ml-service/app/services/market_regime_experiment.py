from datetime import UTC, datetime
import json
from pathlib import Path
from typing import Any

EXPERIMENT_SCHEMA_VERSION = "market-regime-experiment/v1"
MARKET_REGIME_FEATURE_VERSION = "torch-market-regime-features/v1"


def load_experiment_registry(registry_path: Path) -> dict[str, Any]:
    if not registry_path.exists():
        return {"experiments": []}
    return json.loads(registry_path.read_text(encoding="utf-8"))


def write_experiment_registry(registry_path: Path, registry: dict[str, Any]) -> None:
    registry_path.parent.mkdir(parents=True, exist_ok=True)
    registry_path.write_text(json.dumps(registry, indent=2) + "\n", encoding="utf-8")


def upsert_experiment_entry(registry: dict[str, Any], entry: dict[str, Any]) -> dict[str, Any]:
    experiments = list(registry.get("experiments", []))
    entry_name = entry["name"]
    for index, existing in enumerate(experiments):
        if existing.get("name") == entry_name:
            experiments[index] = {**existing, **entry}
            return {**registry, "experiments": experiments}
    return {**registry, "experiments": [*experiments, entry]}


def build_experiment_manifest(
    *,
    csv_path: Path,
    output_path: Path,
    sequence_length: int,
    feature_order: list[str],
    labels: list[str],
    split: dict[str, Any],
    training: dict[str, Any],
    window_count: int,
    train_window_count: int | None = None,
    validation_window_count: int | None = None,
    validation_ratio: float | None = None,
    train_label_distribution: dict[str, int] | None = None,
    validation_label_distribution: dict[str, int] | None = None,
    final_train_loss: float | None = None,
    validation_accuracy: float | None = None,
    device: str,
) -> dict[str, Any]:
    return {
        "schemaVersion": EXPERIMENT_SCHEMA_VERSION,
        "generatedAt": datetime.now(UTC).isoformat(),
        "dataset": {
            "path": str(csv_path),
            "name": csv_path.name,
        },
        "artifact": {
            "path": str(output_path),
            "name": output_path.name,
        },
        "featureVersion": MARKET_REGIME_FEATURE_VERSION,
        "featureOrder": feature_order,
        "sequenceLength": sequence_length,
        "labels": labels,
        "split": split,
        "training": training,
        "windowCount": window_count,
        "trainWindowCount": train_window_count,
        "validationWindowCount": validation_window_count,
        "validationRatio": validation_ratio,
        "trainLabelDistribution": train_label_distribution,
        "validationLabelDistribution": validation_label_distribution,
        "finalTrainLoss": final_train_loss,
        "validationAccuracy": validation_accuracy,
        "device": device,
    }
