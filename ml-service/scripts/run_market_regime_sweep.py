import argparse
import subprocess
from pathlib import Path


def main() -> None:
    args = parse_args()
    commands = build_sweep_commands(args)
    for command in commands:
        print(" ".join(command))
        if not args.dry_run:
            subprocess.run(command, check=True)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run a small market-regime training/evaluation sweep.")
    parser.add_argument("--csv-path", type=Path, default=Path("../data/btc-usd-1h-sample.csv"))
    parser.add_argument("--experiments-dir", type=Path, default=Path("models/experiments"))
    parser.add_argument("--models-dir", type=Path, default=Path("models/sweeps"))
    parser.add_argument("--sequence-length", type=int, default=20)
    parser.add_argument("--epochs", type=int, default=50)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--patience", type=int, default=10)
    parser.add_argument("--seeds", default="42,43")
    parser.add_argument("--dropouts", default="0.1,0.2")
    parser.add_argument("--positional-encoding-modes", default="on,off")
    parser.add_argument("--dry-run", action="store_true")
    return parser.parse_args()


def build_sweep_commands(args: argparse.Namespace) -> list[list[str]]:
    commands: list[list[str]] = []
    for seed in parse_int_list(args.seeds):
        for dropout in parse_float_list(args.dropouts):
            for positional_mode in parse_modes(args.positional_encoding_modes):
                suffix = f"seed{seed}-dropout{dropout}-pos{positional_mode}"
                artifact = args.models_dir / f"market-regime-{suffix}.pt"
                experiment_name = f"sweep-{suffix}"
                train_command = [
                    "python",
                    "scripts/train_market_regime_model.py",
                    "--csv-path",
                    str(args.csv_path),
                    "--output",
                    str(artifact),
                    "--cpu",
                    "--seed",
                    str(seed),
                    "--dropout",
                    str(dropout),
                    "--sequence-length",
                    str(args.sequence_length),
                    "--epochs",
                    str(args.epochs),
                    "--batch-size",
                    str(args.batch_size),
                    "--patience",
                    str(args.patience),
                    "--experiment-name",
                    experiment_name,
                    "--experiments-dir",
                    str(args.experiments_dir),
                ]
                if positional_mode == "off":
                    train_command.append("--no-positional-encoding")
                evaluate_command = [
                    "python",
                    "scripts/evaluate_market_regime_model.py",
                    "--csv-path",
                    str(args.csv_path),
                    "--artifact",
                    str(artifact),
                    "--output",
                    str(artifact.with_suffix(".evaluation.json")),
                    "--holdout-ratio",
                    "0.2",
                    "--experiment-name",
                    experiment_name,
                    "--experiments-dir",
                    str(args.experiments_dir),
                ]
                commands.extend([train_command, evaluate_command])
    return commands


def parse_int_list(value: str) -> list[int]:
    return [int(item.strip()) for item in value.split(",") if item.strip()]


def parse_float_list(value: str) -> list[float]:
    return [float(item.strip()) for item in value.split(",") if item.strip()]


def parse_modes(value: str) -> list[str]:
    modes = [item.strip().lower() for item in value.split(",") if item.strip()]
    invalid = [mode for mode in modes if mode not in {"on", "off"}]
    if invalid:
        raise SystemExit("--positional-encoding-modes must contain only on or off")
    return modes


if __name__ == "__main__":
    main()
