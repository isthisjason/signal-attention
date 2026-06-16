from datetime import UTC, datetime
import hashlib
import json
import uuid
from pathlib import Path
from typing import Any

EXPERIMENT_SCHEMA_VERSION = "market-regime-experiment/v1"
MARKET_REGIME_FEATURE_VERSION = "torch-market-regime-features/v1"


def generate_run_id() -> str:
    """A sortable run id so repeated runs of one experiment name stay distinct."""
    return datetime.now(UTC).strftime("%Y%m%dT%H%M%S") + "-" + uuid.uuid4().hex[:8]


def load_experiment_registry(registry_path: Path) -> dict[str, Any]:
    if not registry_path.exists():
        return {"experiments": []}
    return json.loads(registry_path.read_text(encoding="utf-8"))


def write_experiment_registry(registry_path: Path, registry: dict[str, Any]) -> None:
    # The registry is intentionally append-friendly so local sweeps preserve history.
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


def chronological_split_index(item_count: int, validation_ratio: float) -> int:
    """Return the index that splits items into an earlier train slice and a later slice.

    The split is chronological (no shuffling) so the later slice always represents
    unseen future windows. At least one window is kept on each side.
    """
    if validation_ratio <= 0 or validation_ratio >= 1:
        raise ValueError("validation_ratio must be greater than 0 and less than 1")
    if item_count < 2:
        raise ValueError("at least two windows are required for a chronological split")
    validation_count = max(1, round(item_count * validation_ratio))
    if validation_count >= item_count:
        validation_count = item_count - 1
    return item_count - validation_count


def append_or_merge_run(registry: dict[str, Any], entry: dict[str, Any]) -> dict[str, Any]:
    """Keep one entry per runId, merging train and eval that share a runId.

    Unlike upsert_experiment_entry (which matched by name and overwrote history),
    this preserves every run so the same experiment name can hold many runs.
    """
    experiments = list(registry.get("experiments", []))
    run_id = entry.get("runId")
    if run_id is not None:
        for index, existing in enumerate(experiments):
            if existing.get("runId") == run_id:
                experiments[index] = {**existing, **entry}
                return {**registry, "experiments": experiments}
    return {**registry, "experiments": [*experiments, entry]}


def describe_path(path: Path) -> dict[str, Any]:
    summary: dict[str, Any] = {
        "path": str(path),
        "name": path.name,
        "sizeBytes": None,
        "sha256": None,
    }
    if not path.exists() or not path.is_file():
        return summary
    digest = hashlib.sha256()
    with path.open("rb") as file:
        # Hash large artifacts in chunks so model files do not have to fit in memory.
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(chunk)
    summary["sizeBytes"] = path.stat().st_size
    summary["sha256"] = digest.hexdigest()
    return summary


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
    window_ranges: dict[str, Any] | None = None,
    reproducibility: dict[str, Any] | None = None,
    device: str,
) -> dict[str, Any]:
    # The manifest is the portable audit record for a local artifact; keep enough training context
    # here that a promoted model can be explained without re-opening the training script.
    return {
        "schemaVersion": EXPERIMENT_SCHEMA_VERSION,
        "generatedAt": datetime.now(UTC).isoformat(),
        "dataset": describe_path(csv_path),
        "artifact": describe_path(output_path),
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
        "windowRanges": window_ranges,
        "reproducibility": reproducibility,
        "device": device,
    }
