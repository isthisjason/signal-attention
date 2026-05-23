from datetime import UTC, datetime
from pathlib import Path
from typing import Any

EXPERIMENT_SCHEMA_VERSION = "market-regime-experiment/v1"
MARKET_REGIME_FEATURE_VERSION = "torch-market-regime-features/v1"


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
        "device": device,
    }
