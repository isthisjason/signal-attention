from argparse import Namespace

import scripts.evaluate_market_regime_model as evaluation_script
from scripts.evaluate_market_regime_model import build_evaluation_registry_entry


def test_parse_args_accepts_experiment_registry_options(monkeypatch, tmp_path) -> None:
    csv_path = tmp_path / "candles.csv"
    artifact_path = tmp_path / "market-regime.pt"
    experiments_dir = tmp_path / "experiments"
    monkeypatch.setattr(
        evaluation_script.sys,
        "argv",
        [
            "evaluate_market_regime_model.py",
            "--csv-path",
            str(csv_path),
            "--artifact",
            str(artifact_path),
            "--experiment-name",
            "baseline",
            "--experiments-dir",
            str(experiments_dir),
        ],
    )

    args = evaluation_script.parse_args()

    assert args.experiment_name == "baseline"
    assert args.experiments_dir == experiments_dir


def test_build_evaluation_registry_entry_uses_report_metrics(tmp_path) -> None:
    args = Namespace(experiment_name="baseline", output=tmp_path / "evaluation.json")
    report = {
        "metrics": {
            "accuracy": 0.75,
            "perLabel": {"SIDEWAYS": {"f1": 1}},
            "confidence": {"average": 80.0},
        }
    }

    entry = build_evaluation_registry_entry(args, report)

    assert entry == {
        "name": "baseline",
        "evaluation": {
            "reportPath": str(tmp_path / "evaluation.json"),
            "accuracy": 0.75,
            "perLabel": {"SIDEWAYS": {"f1": 1}},
            "confidence": {"average": 80.0},
        },
    }
