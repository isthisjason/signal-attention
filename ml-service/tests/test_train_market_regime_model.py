import pytest

from app.services.market_regime_experiment import build_experiment_manifest
from scripts.train_market_regime_model import (
    chronological_split_index,
    label_distribution,
    normalize_window,
    normalization_stats,
    predict_validation_labels,
    split_training_windows,
    validate_validation_ratio,
)


@pytest.mark.parametrize("validation_ratio", [0, -0.1, 1, 1.1])
def test_validate_validation_ratio_rejects_invalid_values(validation_ratio: float) -> None:
    with pytest.raises(SystemExit, match="validation-ratio"):
        validate_validation_ratio(validation_ratio)


def test_validate_validation_ratio_accepts_fractional_values() -> None:
    validate_validation_ratio(0.2)


def test_chronological_split_index_uses_validation_ratio() -> None:
    assert chronological_split_index(10, 0.2) == 8


def test_chronological_split_index_keeps_minimum_training_and_validation_windows() -> None:
    assert chronological_split_index(2, 0.2) == 1
    assert chronological_split_index(3, 0.9) == 1


def test_chronological_split_index_rounds_validation_size() -> None:
    assert chronological_split_index(11, 0.25) == 8


def test_chronological_split_index_rejects_too_few_windows() -> None:
    with pytest.raises(ValueError, match="at least two windows"):
        chronological_split_index(1, 0.2)


def test_split_training_windows_preserves_chronological_order() -> None:
    train, validation = split_training_windows([1, 2, 3, 4, 5], 0.4)

    assert train == [1, 2, 3]
    assert validation == [4, 5]


def test_training_normalization_stats_exclude_validation_windows() -> None:
    train_windows = [[[1.0 for _ in range(10)], [3.0 for _ in range(10)]]]
    validation_window = [[100.0 for _ in range(10)]]

    means, stds = normalization_stats(train_windows)

    assert means == [2.0 for _ in range(10)]
    assert stds == [1.0 for _ in range(10)]
    assert normalize_window(validation_window, means, stds) == [[98.0 for _ in range(10)]]


def test_split_training_windows_excludes_validation_items_from_training() -> None:
    train_labels, validation_labels = split_training_windows([0, 1, 2, 3, 4], 0.4)

    assert train_labels == [0, 1, 2]
    assert validation_labels == [3, 4]


def test_predict_validation_labels_returns_label_indexes_and_confidence() -> None:
    predictions = predict_validation_labels(FakeTorch(), FakeModel(), [[[1.0 for _ in range(10)]]], "cpu")

    assert predictions == [{"labelIndex": 1, "confidence": 75.0}]


def test_experiment_manifest_records_split_fields(tmp_path) -> None:
    manifest = build_experiment_manifest(
        csv_path=tmp_path / "candles.csv",
        output_path=tmp_path / "market-regime.pt",
        sequence_length=20,
        feature_order=["close"],
        labels=["SIDEWAYS"],
        split={"method": "chronological_holdout"},
        training={"epochs": 1},
        window_count=10,
        train_window_count=8,
        validation_window_count=2,
        validation_ratio=0.2,
        train_label_distribution={"SIDEWAYS": 7, "TRENDING_UP": 1},
        validation_label_distribution={"SIDEWAYS": 1, "TRENDING_UP": 1},
        device="cpu",
    )

    assert manifest["windowCount"] == 10
    assert manifest["trainWindowCount"] == 8
    assert manifest["validationWindowCount"] == 2
    assert manifest["validationRatio"] == 0.2


def test_label_distribution_formats_counts_by_label_name() -> None:
    distribution = label_distribution([0, 1, 1, 3], ["SIDEWAYS", "TRENDING_UP", "TRENDING_DOWN", "HIGH_VOLATILITY"])

    assert distribution == {
        "SIDEWAYS": 1,
        "TRENDING_UP": 2,
        "TRENDING_DOWN": 0,
        "HIGH_VOLATILITY": 1,
    }


class FakeScalar:
    def __init__(self, value: float | int) -> None:
        self.value = value

    def item(self) -> float | int:
        return self.value


class FakeProbabilities:
    def __getitem__(self, index: int) -> "FakeProbabilities":
        return self

    def max(self, dim: int) -> tuple[FakeScalar, FakeScalar]:
        return FakeScalar(0.75), FakeScalar(1)


class FakeNoGrad:
    def __enter__(self) -> None:
        return None

    def __exit__(self, exc_type, exc, traceback) -> None:
        return None


class FakeTorch:
    float32 = "float32"

    def no_grad(self) -> FakeNoGrad:
        return FakeNoGrad()

    def tensor(self, values, dtype, device):
        return values

    def softmax(self, logits, dim: int) -> FakeProbabilities:
        return FakeProbabilities()


class FakeModel:
    def __call__(self, input_tensor):
        return input_tensor
