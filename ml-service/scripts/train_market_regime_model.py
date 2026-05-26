import argparse
import csv
import json
import sys
from datetime import datetime
from decimal import Decimal
from pathlib import Path

sys.path.append(str(Path(__file__).resolve().parents[1]))

from app.schemas.market_regime_schema import MarketRegimeCandle, MarketRegimeRequest
from app.services.market_regime_service import RuleBasedMarketRegimeClassifier
from app.services.market_regime_experiment import MARKET_REGIME_FEATURE_VERSION, build_experiment_manifest
from app.services.market_regime_torch_features import (
    TORCH_MARKET_REGIME_FEATURE_ORDER,
    build_torch_feature_matrix,
)
from app.services.market_regime_torch_model import DEFAULT_TORCH_MODEL_CONFIG, build_transformer_model


LABELS = ["SIDEWAYS", "TRENDING_UP", "TRENDING_DOWN", "HIGH_VOLATILITY"]


def main() -> None:
    args = parse_args()
    torch = load_torch()
    candles = load_candles(args.csv_path)
    windows, labels = build_training_windows(candles, args.sequence_length)
    if not windows:
        raise SystemExit("not enough candles to build training windows")
    train_windows, validation_windows = split_training_windows(windows, args.validation_ratio)
    train_labels, validation_labels = split_training_windows(labels, args.validation_ratio)

    means, stds = normalization_stats(train_windows)
    normalized_windows = [normalize_window(window, means, stds) for window in windows]
    normalized_validation_windows = [
        normalize_window(window, means, stds)
        for window in validation_windows
    ]
    device = torch.device("cuda" if torch.cuda.is_available() and not args.cpu else "cpu")

    inputs = torch.tensor(normalized_windows, dtype=torch.float32, device=device)
    targets = torch.tensor(labels, dtype=torch.long, device=device)
    model = build_transformer_model(
        torch,
        feature_count=len(TORCH_MARKET_REGIME_FEATURE_ORDER),
        class_count=len(LABELS),
        config=DEFAULT_TORCH_MODEL_CONFIG,
    ).to(device)
    optimizer = torch.optim.Adam(model.parameters(), lr=args.learning_rate)
    loss_function = torch.nn.CrossEntropyLoss()

    model.train()
    for _ in range(args.epochs):
        optimizer.zero_grad()
        loss = loss_function(model(inputs), targets)
        loss.backward()
        optimizer.step()

    args.output.parent.mkdir(parents=True, exist_ok=True)
    torch.save(
        {
            "metadata": {
                "modelVersion": args.model_version,
                "featureVersion": MARKET_REGIME_FEATURE_VERSION,
                "sequenceLength": args.sequence_length,
                "featureOrder": TORCH_MARKET_REGIME_FEATURE_ORDER,
                "labels": LABELS,
                "normalization": {"mean": means, "std": stds},
                "model": DEFAULT_TORCH_MODEL_CONFIG,
            },
            "modelStateDict": model.cpu().state_dict(),
        },
        args.output,
    )
    write_experiment_manifest(args, windows, str(device))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train a local market-regime torch artifact.")
    parser.add_argument("--csv-path", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--sequence-length", type=int, default=20)
    parser.add_argument("--epochs", type=int, default=50)
    parser.add_argument("--learning-rate", type=float, default=0.001)
    parser.add_argument("--validation-ratio", type=float, default=0.2)
    parser.add_argument("--model-version", default="local-transformer-v1")
    parser.add_argument("--cpu", action="store_true")
    args = parser.parse_args()
    validate_validation_ratio(args.validation_ratio)
    return args


def validate_validation_ratio(validation_ratio: float) -> None:
    if validation_ratio <= 0 or validation_ratio >= 1:
        raise SystemExit("--validation-ratio must be greater than 0 and less than 1")


def chronological_split_index(item_count: int, validation_ratio: float) -> int:
    validate_validation_ratio(validation_ratio)
    if item_count < 2:
        raise ValueError("at least two windows are required for a train/validation split")
    validation_count = max(1, round(item_count * validation_ratio))
    if validation_count >= item_count:
        validation_count = item_count - 1
    return item_count - validation_count


def split_training_windows(items: list, validation_ratio: float) -> tuple[list, list]:
    split_index = chronological_split_index(len(items), validation_ratio)
    return items[:split_index], items[split_index:]


def load_torch():
    try:
        import torch
    except ModuleNotFoundError as exc:
        raise SystemExit("install requirements-torch.txt before training") from exc
    return torch


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


def build_training_windows(
    candles: list[MarketRegimeCandle],
    sequence_length: int,
) -> tuple[list[list[list[float]]], list[int]]:
    classifier = RuleBasedMarketRegimeClassifier()
    windows: list[list[list[float]]] = []
    labels: list[int] = []

    for end_index in range(sequence_length, len(candles) + 1):
        window = candles[end_index - sequence_length : end_index]
        request = MarketRegimeRequest(symbol="local", timeframe="local", candles=window)
        label = classifier.classify(request).regimeLabel
        windows.append(build_torch_feature_matrix(window))
        labels.append(LABELS.index(label))

    return windows, labels


def normalization_stats(windows: list[list[list[float]]]) -> tuple[list[float], list[float]]:
    feature_count = len(TORCH_MARKET_REGIME_FEATURE_ORDER)
    flattened = [row for window in windows for row in window]
    means = [
        sum(row[index] for row in flattened) / len(flattened)
        for index in range(feature_count)
    ]
    variances = [
        sum((row[index] - means[index]) ** 2 for row in flattened) / len(flattened)
        for index in range(feature_count)
    ]
    stds = [variance ** 0.5 if variance > 0 else 1.0 for variance in variances]
    return means, stds


def normalize_window(
    window: list[list[float]],
    means: list[float],
    stds: list[float],
) -> list[list[float]]:
    return [[(value - means[index]) / stds[index] for index, value in enumerate(row)] for row in window]


def write_experiment_manifest(args: argparse.Namespace, windows: list[list[list[float]]], device: str) -> None:
    manifest = build_experiment_manifest(
        csv_path=args.csv_path,
        output_path=args.output,
        sequence_length=args.sequence_length,
        feature_order=TORCH_MARKET_REGIME_FEATURE_ORDER,
        labels=LABELS,
        split={
            "method": "chronological_holdout",
            "validationRatio": args.validation_ratio,
        },
        training={
            "epochs": args.epochs,
            "learningRate": args.learning_rate,
            "modelVersion": args.model_version,
            "model": DEFAULT_TORCH_MODEL_CONFIG,
        },
        window_count=len(windows),
        device=device,
    )
    manifest_path = args.output.with_suffix(args.output.suffix + ".manifest.json")
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
