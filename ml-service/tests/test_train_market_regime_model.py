import pytest

from scripts.train_market_regime_model import validate_validation_ratio


@pytest.mark.parametrize("validation_ratio", [0, -0.1, 1, 1.1])
def test_validate_validation_ratio_rejects_invalid_values(validation_ratio: float) -> None:
    with pytest.raises(SystemExit, match="validation-ratio"):
        validate_validation_ratio(validation_ratio)


def test_validate_validation_ratio_accepts_fractional_values() -> None:
    validate_validation_ratio(0.2)
