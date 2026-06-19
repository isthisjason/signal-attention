import argparse
import csv
import json
import sys
from datetime import datetime
from decimal import Decimal
from pathlib import Path
from typing import Any

sys.path.append(str(Path(__file__).resolve().parents[1]))

from app.schemas.market_regime_schema import MarketRegimeCandle, MarketRegimeRequest
from app.services.market_regime_service import RuleBasedMarketRegimeClassifier
from app.services.market_regime_torch_adapter import (
    build_model_for_metadata,
    load_artifact,
    load_torch,
    normalize_features,
    validate_artifact_metadata,
)
from app.services.market_regime_experiment import (
    append_or_merge_run,
    chronological_split_index,
    describe_path,
    load_experiment_registry,
    write_experiment_registry,
)
from app.services.market_regime_torch_features import (
    TORCH_MARKET_REGIME_FEATURE_ORDER,
    build_torch_feature_matrix,
)


def main() -> None:
    args = parse_args()
    torch = load_torch()
    device = torch.device("cpu")
    artifact = load_artifact(torch, args.artifact, device)
    metadata = validate_artifact_metadata(artifact)
    candles = load_candles(args.csv_path)
    examples = build_examples(candles, int(metadata["sequenceLength"]))
    # Optional holdout scores only the later windows to mimic future data.
    examples, evaluation_scope = apply_holdout(examples, args.holdout_ratio)

    model = build_model_for_metadata(
        torch,
        feature_count=len(TORCH_MARKET_REGIME_FEATURE_ORDER),
        class_count=len(metadata["labels"]),
        metadata=metadata,
    )
    model.load_state_dict(artifact["modelStateDict"])
    model.to(device)
    model.eval()

    predictions = predict_examples(torch, model, metadata, examples, device)
    model_metrics = calculate_metrics(predictions, metadata["labels"])
    baseline = majority_class_baseline(predictions, metadata["labels"])
    report = {
        "artifact": describe_path(args.artifact),
        "dataset": describe_path(args.csv_path),
        "sequenceLength": metadata["sequenceLength"],
        "architecture": metadata.get("architecture"),
        "featureOrder": metadata["featureOrder"],
        "labels": metadata["labels"],
        "windowCount": len(examples),
        "evaluationScope": evaluation_scope,
        "groundTruthSource": "rule-based-labels",
        "note": (
            "Expected labels come from the deterministic rule based classifier, "
            "so this measures agreement with those rules, not an independent ground truth. "
            "Read accuracy alongside the majority class baseline and lift."
        ),
        "windowRanges": window_ranges_from_predictions(predictions),
        "labelDistribution": label_distribution_from_predictions(predictions, metadata["labels"]),
        "metrics": model_metrics,
        "baseline": baseline,
        "liftOverBaseline": round(model_metrics["accuracy"] - baseline["metrics"]["accuracy"], 4),
        "samples": predictions[: args.sample_count],
    }
    write_report(args.output, report)
    if args.experiment_name:
        register_evaluation_report(args, report, metadata.get("runId"))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Evaluate a local market-regime torch artifact.")
    parser.add_argument("--csv-path", type=Path, required=True)
    parser.add_argument("--artifact", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--sample-count", type=int, default=10)
    parser.add_argument(
        "--holdout-ratio",
        type=float,
        default=None,
        help="Score only the later chronological fraction of windows (for example 0.2).",
    )
    parser.add_argument("--experiment-name")
    parser.add_argument("--experiments-dir", type=Path, default=Path("models/experiments"))
    return parser.parse_args()


def apply_holdout(examples: list[dict[str, Any]], holdout_ratio: float | None) -> tuple[list[dict[str, Any]], str]:
    if holdout_ratio is None:
        return examples, "full"
    if len(examples) < 2:
        return examples, "full"
    # Holdout evaluation uses the later chronological tail to approximate unseen future windows.
    split_index = chronological_split_index(len(examples), holdout_ratio)
    return examples[split_index:], "holdout"


def majority_class_baseline(predictions: list[dict[str, Any]], labels: list[str]) -> dict[str, Any]:
    """Predict the single most common expected label for every window.

    This is the honest floor a sequence model has to beat. If it cannot, the model
    is not learning anything the label frequencies do not already tell us.
    """
    expected_counts = {label: 0 for label in labels}
    for prediction in predictions:
        expected_counts[prediction["expectedLabel"]] += 1
    majority_label = max(labels, key=lambda label: expected_counts[label]) if predictions else (labels[0] if labels else None)

    baseline_predictions = [
        {
            "expectedLabel": prediction["expectedLabel"],
            "predictedLabel": majority_label,
            "confidence": 0,
        }
        for prediction in predictions
    ]
    return {
        "strategy": "majority-class",
        "label": majority_label,
        "metrics": calculate_metrics(baseline_predictions, labels),
    }


def load_candles(csv_path: Path) -> list[MarketRegimeCandle]:
    with csv_path.open(newline="") as file:
        rows = csv.DictReader(file)
        return [
            MarketRegimeCandle(
                openTime=parse_datetime(row["openTime"]),
                open=Decimal(row["open"]),
                high=Decimal(row["high"]),
                low=Decimal(row["low"]),
                close=Decimal(row["close"]),
                volume=Decimal(row["volume"]),
            )
            for row in rows
        ]


def parse_datetime(value: str) -> datetime:
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def build_examples(candles: list[MarketRegimeCandle], sequence_length: int) -> list[dict[str, Any]]:
    classifier = RuleBasedMarketRegimeClassifier()
    examples: list[dict[str, Any]] = []
    for end_index in range(sequence_length, len(candles) + 1):
        window = candles[end_index - sequence_length : end_index]
        request = MarketRegimeRequest(symbol="local", timeframe="local", candles=window)
        # Expected labels are generated the same way as training labels for a fair comparison.
        examples.append(
            {
                "openTime": window[-1].openTime.isoformat(),
                "expectedLabel": classifier.classify(request).regimeLabel,
                "features": build_torch_feature_matrix(window),
            }
        )
    return examples


def predict_examples(torch, model, metadata: dict[str, Any], examples: list[dict[str, Any]], device) -> list[dict[str, Any]]:
    predictions: list[dict[str, Any]] = []
    with torch.no_grad():
        for example in examples:
            # Use artifact metadata normalization so evaluation matches service inference.
            normalized_features = normalize_features(example["features"], metadata)
            input_tensor = torch.tensor([normalized_features], dtype=torch.float32, device=device)
            probabilities = torch.softmax(model(input_tensor), dim=-1)[0]
            confidence_value, label_index = probabilities.max(dim=0)
            predicted_label = metadata["labels"][int(label_index.item())]
            predictions.append(
                {
                    "openTime": example["openTime"],
                    "expectedLabel": example["expectedLabel"],
                    "predictedLabel": predicted_label,
                    "isCorrect": example["expectedLabel"] == predicted_label,
                    "confidence": round(float(confidence_value.item()) * 100, 4),
                }
            )
    return predictions


def calculate_metrics(predictions: list[dict[str, Any]], labels: list[str]) -> dict[str, Any]:
    if not predictions:
        return {
            "accuracy": 0,
            "correctCount": 0,
            "totalCount": 0,
            "perLabel": {},
            "confusionMatrix": {label: {inner: 0 for inner in labels} for label in labels},
            "confidence": {"min": 0, "max": 0, "average": 0},
        }

    correct = sum(1 for prediction in predictions if prediction["expectedLabel"] == prediction["predictedLabel"])
    confusion_matrix = {label: {inner: 0 for inner in labels} for label in labels}
    for prediction in predictions:
        confusion_matrix[prediction["expectedLabel"]][prediction["predictedLabel"]] += 1

    per_label = {}
    for label in labels:
        # Per-label metrics show whether accuracy is hiding a weak minority class.
        true_positive = confusion_matrix[label][label]
        false_positive = sum(confusion_matrix[other][label] for other in labels if other != label)
        false_negative = sum(confusion_matrix[label][other] for other in labels if other != label)
        precision = ratio(true_positive, true_positive + false_positive)
        recall = ratio(true_positive, true_positive + false_negative)
        per_label[label] = {
            "precision": precision,
            "recall": recall,
            "f1": ratio(2 * precision * recall, precision + recall),
            "support": sum(confusion_matrix[label].values()),
        }

    confidences = [float(prediction["confidence"]) for prediction in predictions]
    return {
        "accuracy": ratio(correct, len(predictions)),
        "correctCount": correct,
        "totalCount": len(predictions),
        "perLabel": per_label,
        "confusionMatrix": confusion_matrix,
        "confidence": {
            "min": round(min(confidences), 4),
            "max": round(max(confidences), 4),
            "average": round(sum(confidences) / len(confidences), 4),
        },
    }


def window_ranges_from_predictions(predictions: list[dict[str, Any]]) -> dict[str, str | None]:
    if not predictions:
        return {"firstWindowEnd": None, "lastWindowEnd": None}
    return {
        "firstWindowEnd": predictions[0]["openTime"],
        "lastWindowEnd": predictions[-1]["openTime"],
    }


def label_distribution_from_predictions(predictions: list[dict[str, Any]], labels: list[str]) -> dict[str, dict[str, int]]:
    return {
        "expected": {
            label: sum(1 for prediction in predictions if prediction["expectedLabel"] == label)
            for label in labels
        },
        "predicted": {
            label: sum(1 for prediction in predictions if prediction["predictedLabel"] == label)
            for label in labels
        },
    }


def ratio(numerator: float, denominator: float) -> float:
    if denominator == 0:
        return 0
    return round(numerator / denominator, 4)


def write_report(output: Path | None, report: dict[str, Any]) -> None:
    payload = json.dumps(report, indent=2) + "\n"
    if output is None:
        print(payload, end="")
        return
    # Reports are plain JSON so promotion and diagnostics can read them without importing torch.
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(payload, encoding="utf-8")


def build_evaluation_registry_entry(args: argparse.Namespace, report: dict[str, Any], run_id: str | None = None) -> dict[str, Any]:
    metrics = report["metrics"]
    return {
        "name": args.experiment_name,
        "runId": run_id,
        "evaluation": {
            "dataset": report["dataset"],
            "artifact": report["artifact"],
            "reportPath": str(args.output) if args.output is not None else None,
            "architecture": report.get("architecture"),
            "accuracy": metrics["accuracy"],
            "correctCount": metrics["correctCount"],
            "totalCount": metrics["totalCount"],
            "perLabel": metrics["perLabel"],
            "confidence": metrics["confidence"],
            "labelDistribution": report["labelDistribution"],
            "windowRanges": report["windowRanges"],
            "evaluationScope": report.get("evaluationScope"),
            "baselineAccuracy": report.get("baseline", {}).get("metrics", {}).get("accuracy"),
            "baselineLabel": report.get("baseline", {}).get("label"),
            "liftOverBaseline": report.get("liftOverBaseline"),
        },
    }


def register_evaluation_report(args: argparse.Namespace, report: dict[str, Any], run_id: str | None) -> None:
    registry_path = args.experiments_dir / "index.json"
    registry = load_experiment_registry(registry_path)
    # Evaluation merges into the matching run when the artifact metadata carries a run id.
    registry = append_or_merge_run(registry, build_evaluation_registry_entry(args, report, run_id))
    write_experiment_registry(registry_path, registry)


if __name__ == "__main__":
    main()
