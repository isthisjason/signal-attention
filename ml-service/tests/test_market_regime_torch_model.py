import math

from app.services.market_regime_torch_model import (
    DEFAULT_TORCH_MODEL_CONFIG,
    sinusoidal_positional_encoding,
)


def test_default_config_enables_regularization_and_positional_encoding() -> None:
    assert DEFAULT_TORCH_MODEL_CONFIG["dropout"] > 0
    assert DEFAULT_TORCH_MODEL_CONFIG["usePositionalEncoding"] is True


def test_sinusoidal_positional_encoding_has_expected_shape() -> None:
    table = sinusoidal_positional_encoding(sequence_length=4, d_model=8)

    assert len(table) == 4
    assert all(len(row) == 8 for row in table)


def test_sinusoidal_positional_encoding_first_position_is_sin_zero_cos_zero() -> None:
    table = sinusoidal_positional_encoding(sequence_length=1, d_model=4)

    assert table[0][0] == math.sin(0)
    assert table[0][1] == math.cos(0)
    assert table[0] == [0.0, 1.0, 0.0, 1.0]
