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
        "samples": predictions[: args.sample_count],
    }
    write_report(args.output, report)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Evaluate a local market-regime torch artifact.")
    parser.add_argument("--csv-path", type=Path, required=True)
    parser.add_argument("--artifact", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--sample-count", type=int, default=10)
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


def write_report(output: Path | None, report: dict[str, Any]) -> None:
    payload = json.dumps(report, indent=2) + "\n"
    if output is None:
        print(payload, end="")
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(payload, encoding="utf-8")


if __name__ == "__main__":
    main()
