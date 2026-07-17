from argparse import Namespace
from pathlib import Path

import pytest

from scripts.run_market_regime_sweep import build_sweep_commands, parse_modes


def args(**overrides) -> Namespace:
    defaults = {
        "csv_path": Path("../data/btc-usd-1h-sample.csv"),
        "experiments_dir": Path("models/experiments"),
        "models_dir": Path("models/sweeps"),
        "sequence_length": 20,
        "epochs": 50,
        "batch_size": 32,
        "patience": 10,
        "class_weighting": "balanced",
        "selection_metric": "macro-f1",
        "seeds": "42,43",
        "dropouts": "0.1,0.2",
        "positional_encoding_modes": "on,off",
        "architecture": "transformer-v1",
    }
    defaults.update(overrides)
    return Namespace(**defaults)


def test_build_sweep_commands_creates_train_and_eval_for_each_grid_item() -> None:
    commands = build_sweep_commands(args())

    assert len(commands) == 16
    assert commands[0][:2] == ["python", "scripts/train_market_regime_model.py"]
    assert commands[1][:2] == ["python", "scripts/evaluate_market_regime_model.py"]


def test_build_sweep_commands_adds_no_positional_encoding_for_off_mode() -> None:
    commands = build_sweep_commands(args(seeds="42", dropouts="0.1", positional_encoding_modes="off"))

    assert "--no-positional-encoding" in commands[0]
    assert "--holdout-ratio" not in commands[1]


def test_build_sweep_commands_uses_default_cpu_training_path() -> None:
    commands = build_sweep_commands(args(seeds="42", dropouts="0.1", positional_encoding_modes="on"))

    train_command = commands[0]
    assert "--cpu" in train_command
    assert "../data/btc-usd-1h-sample.csv" in train_command
    assert "sweep-seed42-dropout0.1-poson" in train_command
    assert train_command[train_command.index("--architecture") + 1] == "transformer-v1"
    assert train_command[train_command.index("--model-version") + 1] == "local-transformer-v1"
    assert train_command[train_command.index("--class-weighting") + 1] == "balanced"
    assert train_command[train_command.index("--selection-metric") + 1] == "macro-f1"


def test_build_sweep_commands_gives_attention_v2_distinct_identity() -> None:
    commands = build_sweep_commands(
        args(
            seeds="42",
            dropouts="0.1",
            positional_encoding_modes="on",
            architecture="attention-transformer-v2",
        )
    )

    train_command = commands[0]
    assert any(value.endswith("market-regime-seed42-dropout0.1-poson-attention-v2.pt") for value in train_command)
    assert "sweep-seed42-dropout0.1-poson-attention-v2" in train_command
    assert train_command[train_command.index("--model-version") + 1] == "local-attention-transformer-v2"


def test_parse_modes_rejects_unknown_modes() -> None:
    with pytest.raises(SystemExit, match="on or off"):
        parse_modes("on,bad")
