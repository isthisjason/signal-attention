import csv
from datetime import datetime
from decimal import Decimal
from pathlib import Path
from typing import Any

from app.schemas.market_regime_schema import MarketRegimeCandle, MarketRegimeRequest
from app.services.market_regime_experiment import describe_path
from app.services.market_regime_service import RuleBasedMarketRegimeClassifier
from app.services.market_regime_torch_features import build_torch_feature_matrix


MARKET_REGIME_LABELS = ["SIDEWAYS", "TRENDING_UP", "TRENDING_DOWN", "HIGH_VOLATILITY"]


def load_market_regime_candles(csv_path: Path) -> list[MarketRegimeCandle]:
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


def build_labeled_windows(
    candles: list[MarketRegimeCandle],
    sequence_length: int,
) -> tuple[list[list[list[float]]], list[int]]:
    classifier = RuleBasedMarketRegimeClassifier()
    windows: list[list[list[float]]] = []
    labels: list[int] = []

    for end_index in range(sequence_length, len(candles) + 1):
        window = candles[end_index - sequence_length : end_index]
        request = MarketRegimeRequest(symbol="local", timeframe="local", candles=window)
        # These are weak labels distilled from the rule baseline, not independent market truth.
        label = classifier.classify(request).regimeLabel
        windows.append(build_torch_feature_matrix(window))
        labels.append(MARKET_REGIME_LABELS.index(label))
    return windows, labels


def three_way_split(items: list, validation_ratio: float = 0.2, test_ratio: float = 0.2) -> tuple[list, list, list]:
    if validation_ratio <= 0 or test_ratio <= 0 or validation_ratio + test_ratio >= 1:
        raise ValueError("validation and test ratios must be positive and sum to less than 1")
    if len(items) < 3:
        raise ValueError("at least three windows are required for train, validation, and test splits")
    validation_count = max(1, round(len(items) * validation_ratio))
    test_count = max(1, round(len(items) * test_ratio))
    train_count = len(items) - validation_count - test_count
    if train_count < 1:
        raise ValueError("split ratios must leave at least one training window")
    return items[:train_count], items[train_count : train_count + validation_count], items[train_count + validation_count :]


def label_distribution(labels: list[int]) -> dict[str, int]:
    return {label: labels.count(index) for index, label in enumerate(MARKET_REGIME_LABELS)}


def build_dataset_readiness(
    candles: list[MarketRegimeCandle],
    sequence_length: int,
    *,
    validation_ratio: float = 0.2,
    test_ratio: float = 0.2,
    min_windows: int = 1000,
    min_labels_per_split: int = 2,
    min_evaluation_support: int = 20,
    granularity_seconds: int = 3600,
) -> dict[str, Any]:
    windows, labels = build_labeled_windows(candles, sequence_length)
    failures: list[str] = []
    if len(windows) < 3:
        return {
            "ready": False,
            "failures": ["at least three sequence windows are required"],
            "candleCount": len(candles),
            "windowCount": len(windows),
            "splits": {},
            "gaps": candle_gaps(candles, granularity_seconds),
        }

    train_labels, validation_labels, test_labels = three_way_split(labels, validation_ratio, test_ratio)
    distributions = {
        "train": label_distribution(train_labels),
        "validation": label_distribution(validation_labels),
        "test": label_distribution(test_labels),
    }
    if len(windows) < min_windows:
        failures.append(f"window count is below {min_windows}")
    for split_name, distribution in distributions.items():
        observed = {label for label, count in distribution.items() if count > 0}
        if len(observed) < min_labels_per_split:
            failures.append(f"{split_name} split has fewer than {min_labels_per_split} observed labels")

    train_observed = {label for label, count in distributions["train"].items() if count > 0}
    for split_name in ("validation", "test"):
        evaluation_observed = {label for label, count in distributions[split_name].items() if count > 0}
        missing_from_train = sorted(evaluation_observed - train_observed)
        if missing_from_train:
            failures.append(f"{split_name} labels missing from training: {', '.join(missing_from_train)}")
        low_support = sorted(
            label
            for label, count in distributions[split_name].items()
            if 0 < count < min_evaluation_support
        )
        if low_support:
            failures.append(
                f"{split_name} labels below {min_evaluation_support} windows: {', '.join(low_support)}"
            )

    return {
        "ready": not failures,
        "failures": failures,
        "candleCount": len(candles),
        "windowCount": len(windows),
        "sequenceLength": sequence_length,
        "ratios": {"train": round(1 - validation_ratio - test_ratio, 4), "validation": validation_ratio, "test": test_ratio},
        "splits": distributions,
        "gaps": candle_gaps(candles, granularity_seconds),
    }


def inspect_dataset(
    csv_path: Path,
    sequence_length: int,
    **readiness_options,
) -> dict[str, Any]:
    candles = load_market_regime_candles(csv_path)
    report = build_dataset_readiness(candles, sequence_length, **readiness_options)
    return {
        "dataset": describe_path(csv_path),
        "firstCandle": candles[0].openTime.isoformat() if candles else None,
        "lastCandle": candles[-1].openTime.isoformat() if candles else None,
        **report,
    }


def candle_gaps(candles: list[MarketRegimeCandle], granularity_seconds: int) -> list[dict[str, Any]]:
    gaps = []
    for previous, current in zip(candles, candles[1:]):
        elapsed_seconds = int((current.openTime - previous.openTime).total_seconds())
        missing_count = max(0, elapsed_seconds // granularity_seconds - 1)
        if missing_count:
            gaps.append(
                {
                    "after": previous.openTime.isoformat(),
                    "before": current.openTime.isoformat(),
                    "missingCount": missing_count,
                }
            )
    return gaps
