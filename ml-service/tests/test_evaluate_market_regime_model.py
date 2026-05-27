from argparse import Namespace

import scripts.evaluate_market_regime_model as evaluation_script
from scripts.evaluate_market_regime_model import (
    build_evaluation_registry_entry,
    calculate_metrics,
    label_distribution_from_predictions,
    window_ranges_from_predictions,
)


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
        "dataset": {"path": str(tmp_path / "candles.csv"), "name": "candles.csv"},
        "artifact": {"path": str(tmp_path / "market-regime.pt"), "name": "market-regime.pt"},
        "labelDistribution": {"expected": {"SIDEWAYS": 2}, "predicted": {"SIDEWAYS": 1}},
        "windowRanges": {
            "firstWindowEnd": "2024-01-01T00:00:00+00:00",
            "lastWindowEnd": "2024-01-01T03:00:00+00:00",
        },
        "metrics": {
            "accuracy": 0.75,
            "correctCount": 3,
            "totalCount": 4,
            "perLabel": {"SIDEWAYS": {"f1": 1}},
            "confidence": {"average": 80.0},
        }
    }

    entry = build_evaluation_registry_entry(args, report)

    assert entry == {
        "name": "baseline",
        "evaluation": {
            "dataset": {"path": str(tmp_path / "candles.csv"), "name": "candles.csv"},
            "artifact": {"path": str(tmp_path / "market-regime.pt"), "name": "market-regime.pt"},
            "reportPath": str(tmp_path / "evaluation.json"),
            "accuracy": 0.75,
            "correctCount": 3,
            "totalCount": 4,
            "perLabel": {"SIDEWAYS": {"f1": 1}},
            "confidence": {"average": 80.0},
            "labelDistribution": {"expected": {"SIDEWAYS": 2}, "predicted": {"SIDEWAYS": 1}},
            "windowRanges": {
                "firstWindowEnd": "2024-01-01T00:00:00+00:00",
                "lastWindowEnd": "2024-01-01T03:00:00+00:00",
            },
        },
    }


def test_calculate_metrics_includes_counts() -> None:
    metrics = calculate_metrics(
        [
            {"expectedLabel": "SIDEWAYS", "predictedLabel": "SIDEWAYS", "confidence": 80},
            {"expectedLabel": "SIDEWAYS", "predictedLabel": "TRENDING_UP", "confidence": 60},
        ],
        ["SIDEWAYS", "TRENDING_UP"],
    )

    assert metrics["accuracy"] == 0.5
    assert metrics["correctCount"] == 1
    assert metrics["totalCount"] == 2


def test_window_ranges_from_predictions_uses_first_and_last_open_time() -> None:
    ranges = window_ranges_from_predictions(
        [
            {"openTime": "2024-01-01T00:00:00+00:00"},
            {"openTime": "2024-01-01T01:00:00+00:00"},
        ]
    )

    assert ranges == {
        "firstWindowEnd": "2024-01-01T00:00:00+00:00",
        "lastWindowEnd": "2024-01-01T01:00:00+00:00",
    }


def test_label_distribution_from_predictions_counts_expected_and_predicted_labels() -> None:
    distribution = label_distribution_from_predictions(
        [
            {"expectedLabel": "SIDEWAYS", "predictedLabel": "SIDEWAYS"},
            {"expectedLabel": "TRENDING_UP", "predictedLabel": "SIDEWAYS"},
        ],
        ["SIDEWAYS", "TRENDING_UP"],
    )

    assert distribution == {
        "expected": {"SIDEWAYS": 1, "TRENDING_UP": 1},
        "predicted": {"SIDEWAYS": 2, "TRENDING_UP": 0},
    }
