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
    load_artifact,
    load_torch,
    normalize_features,
    validate_artifact_metadata,
)
from app.services.market_regime_experiment import (
    load_experiment_registry,
    upsert_experiment_entry,
    write_experiment_registry,
)
from app.services.market_regime_torch_features import (
    TORCH_MARKET_REGIME_FEATURE_ORDER,
    build_torch_feature_matrix,
)
from app.services.market_regime_torch_model import build_transformer_model


def main() -> None:
    args = parse_args()
    torch = load_torch()
    device = torch.device("cpu")
    artifact = load_artifact(torch, args.artifact, device)
    metadata = validate_artifact_metadata(artifact)
    candles = load_candles(args.csv_path)
    examples = build_examples(candles, int(metadata["sequenceLength"]))

    model = build_transformer_model(
        torch,
        feature_count=len(TORCH_MARKET_REGIME_FEATURE_ORDER),
        class_count=len(metadata["labels"]),
        config=metadata.get("model"),
    )
    model.load_state_dict(artifact["modelStateDict"])
    model.to(device)
    model.eval()

    predictions = predict_examples(torch, model, metadata, examples, device)
    report = {
        "artifact": str(args.artifact),
        "dataset": str(args.csv_path),
        "sequenceLength": metadata["sequenceLength"],
        "featureOrder": metadata["featureOrder"],
        "labels": metadata["labels"],
        "windowCount": len(examples),
        "metrics": calculate_metrics(predictions, metadata["labels"]),
        "samples": predictions[: args.sample_count],
    }
    write_report(args.output, report)
    if args.experiment_name:
        register_evaluation_report(args, report)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Evaluate a local market-regime torch artifact.")
    parser.add_argument("--csv-path", type=Path, required=True)
    parser.add_argument("--artifact", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--sample-count", type=int, default=10)
    parser.add_argument("--experiment-name")
    parser.add_argument("--experiments-dir", type=Path, default=Path("models/experiments"))
    return parser.parse_args()


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
                    "confidence": round(float(confidence_value.item()) * 100, 4),
                }
            )
    return predictions


def calculate_metrics(predictions: list[dict[str, Any]], labels: list[str]) -> dict[str, Any]:
    if not predictions:
        return {
            "accuracy": 0,
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
        "perLabel": per_label,
        "confusionMatrix": confusion_matrix,
        "confidence": {
            "min": round(min(confidences), 4),
            "max": round(max(confidences), 4),
            "average": round(sum(confidences) / len(confidences), 4),
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
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(payload, encoding="utf-8")


def build_evaluation_registry_entry(args: argparse.Namespace, report: dict[str, Any]) -> dict[str, Any]:
    metrics = report["metrics"]
    return {
        "name": args.experiment_name,
        "evaluation": {
            "reportPath": str(args.output) if args.output is not None else None,
            "accuracy": metrics["accuracy"],
            "perLabel": metrics["perLabel"],
            "confidence": metrics["confidence"],
        },
    }


def register_evaluation_report(args: argparse.Namespace, report: dict[str, Any]) -> None:
    registry_path = args.experiments_dir / "index.json"
    registry = load_experiment_registry(registry_path)
    registry = upsert_experiment_entry(registry, build_evaluation_registry_entry(args, report))
    write_experiment_registry(registry_path, registry)


if __name__ == "__main__":
    main()
