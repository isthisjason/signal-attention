from argparse import Namespace

import scripts.evaluate_market_regime_model as evaluation_script
from scripts.evaluate_market_regime_model import (
    apply_holdout,
    build_evaluation_registry_entry,
    calculate_metrics,
    label_distribution_from_predictions,
    majority_class_baseline,
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
        "architecture": "attention-transformer-v2",
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

    report["evaluationScope"] = "holdout"
    report["baseline"] = {"label": "SIDEWAYS", "metrics": {"accuracy": 0.5}}
    report["liftOverBaseline"] = 0.25

    entry = build_evaluation_registry_entry(args, report, "run-123")

    assert entry == {
        "name": "baseline",
        "runId": "run-123",
        "evaluation": {
            "dataset": {"path": str(tmp_path / "candles.csv"), "name": "candles.csv"},
            "artifact": {"path": str(tmp_path / "market-regime.pt"), "name": "market-regime.pt"},
            "reportPath": str(tmp_path / "evaluation.json"),
            "architecture": "attention-transformer-v2",
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
            "evaluationScope": "holdout",
            "baselineAccuracy": 0.5,
            "baselineLabel": "SIDEWAYS",
            "liftOverBaseline": 0.25,
        },
    }


def test_evaluation_uses_metadata_architecture_builder(monkeypatch) -> None:
    captured = {}

    def fake_builder(torch, *, feature_count, class_count, metadata):
        # Evaluation must use the same metadata-aware builder as service inference so v2
        # artifacts do not accidentally get loaded into the v1 architecture.
        captured["metadata"] = metadata
        return object()

    monkeypatch.setattr(evaluation_script, "build_model_for_metadata", fake_builder)
    metadata = {
        "labels": ["SIDEWAYS"],
        "architecture": "attention-transformer-v2",
        "model": {"dModel": 32},
    }

    model = evaluation_script.build_model_for_metadata(
        object(),
        feature_count=6,
        class_count=1,
        metadata=metadata,
    )

    assert model is not None
    assert captured["metadata"] == metadata


def test_majority_class_baseline_predicts_dominant_label() -> None:
    predictions = [
        {"expectedLabel": "SIDEWAYS", "predictedLabel": "SIDEWAYS"},
        {"expectedLabel": "SIDEWAYS", "predictedLabel": "TRENDING_UP"},
        {"expectedLabel": "TRENDING_UP", "predictedLabel": "TRENDING_UP"},
    ]

    baseline = majority_class_baseline(predictions, ["SIDEWAYS", "TRENDING_UP"])

    assert baseline["strategy"] == "majority-class"
    assert baseline["label"] == "SIDEWAYS"
    # Two of three windows are SIDEWAYS, so always guessing SIDEWAYS scores 2/3.
    assert baseline["metrics"]["accuracy"] == round(2 / 3, 4)


def test_apply_holdout_returns_full_scope_when_ratio_is_none() -> None:
    examples = [{"openTime": str(index)} for index in range(5)]

    result, scope = apply_holdout(examples, None)

    assert scope == "full"
    assert result == examples


def test_apply_holdout_keeps_only_the_later_chronological_tail() -> None:
    examples = [{"openTime": str(index)} for index in range(10)]

    result, scope = apply_holdout(examples, 0.2)

    assert scope == "holdout"
    assert [example["openTime"] for example in result] == ["8", "9"]


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
