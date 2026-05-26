import pytest

from scripts.train_market_regime_model import (
    chronological_split_index,
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
