import json

from app.services.market_regime_diagnostics import (
    build_experiment_diagnostics,
    confusion_pairs,
    render_diagnostics_markdown,
    sort_diagnostic_runs,
    weakest_labels,
)


def eligible_run(**overrides) -> dict:
    run = {
        "name": "baseline",
        "runId": "run-1",
        "artifact": {"sha256": "artifact-hash"},
        "training": {"validationAccuracy": 0.7},
        "evaluation": {
            "accuracy": 0.75,
            "baselineAccuracy": 0.5,
            "liftOverBaseline": 0.25,
            "evaluationScope": "holdout",
            "confidence": {"average": 80},
            "labelDistribution": {"expected": {"SIDEWAYS": 2}, "predicted": {"SIDEWAYS": 1}},
            "windowRanges": {"firstWindowEnd": "2024-01-01T00:00:00+00:00"},
            "perLabel": {"SIDEWAYS": {"f1": 0.9, "recall": 0.9, "precision": 0.9, "support": 10}},
        },
    }
    run.update(overrides)
    return run


def test_build_experiment_diagnostics_handles_empty_registry() -> None:
    diagnostics = build_experiment_diagnostics({"experiments": []})

    assert diagnostics["summary"]["totalRuns"] == 0
    assert diagnostics["summary"]["bestRun"] is None
    assert diagnostics["runs"] == []


def test_build_experiment_diagnostics_reports_training_only_run_as_incomplete() -> None:
    diagnostics = build_experiment_diagnostics(
        {"experiments": [{"name": "train-only", "runId": "run-1", "training": {"validationAccuracy": 0.8}}]}
    )

    assert diagnostics["summary"]["trainedRuns"] == 1
    assert diagnostics["summary"]["evaluatedRuns"] == 0
    assert diagnostics["incompleteRuns"][0]["hasEvaluation"] is False


def test_build_experiment_diagnostics_reports_evaluation_only_run_as_incomplete() -> None:
    diagnostics = build_experiment_diagnostics({"experiments": [eligible_run(training={})]})

    assert diagnostics["summary"]["evaluatedRuns"] == 1
    assert diagnostics["incompleteRuns"][0]["hasTraining"] is False


def test_build_experiment_diagnostics_ranks_best_run() -> None:
    diagnostics = build_experiment_diagnostics(
        {
            "experiments": [
                eligible_run(runId="low", evaluation={"accuracy": 0.6, "liftOverBaseline": 0.4, "evaluationScope": "holdout"}),
                eligible_run(runId="high", evaluation={"accuracy": 0.8, "liftOverBaseline": 0.1, "evaluationScope": "holdout"}),
                eligible_run(runId="tie", evaluation={"accuracy": 0.8, "liftOverBaseline": 0.2, "evaluationScope": "holdout"}),
            ]
        }
    )

    assert diagnostics["summary"]["bestRun"]["runId"] == "tie"
    assert [run["runId"] for run in diagnostics["runs"][:3]] == ["tie", "high", "low"]


def test_build_experiment_diagnostics_includes_promotion_gate() -> None:
    diagnostics = build_experiment_diagnostics({"experiments": [eligible_run()]})

    assert diagnostics["summary"]["promotionEligibleRuns"] == 1
    assert diagnostics["runs"][0]["promotionGate"]["eligible"] is True


def test_build_experiment_diagnostics_uses_report_metrics_when_available(tmp_path) -> None:
    report_path = tmp_path / "evaluation.json"
    report_path.write_text(
        json.dumps(
            {
                "metrics": {
                    "perLabel": {
                        "SIDEWAYS": {"f1": 0.8, "recall": 0.7, "precision": 0.9, "support": 5},
                        "TRENDING_UP": {"f1": 0.2, "recall": 0.3, "precision": 0.1, "support": 2},
                    },
                    "confusionMatrix": {
                        "SIDEWAYS": {"SIDEWAYS": 4, "TRENDING_UP": 1},
                        "TRENDING_UP": {"SIDEWAYS": 2, "TRENDING_UP": 0},
                    },
                }
            }
        ),
        encoding="utf-8",
    )
    run = eligible_run()
    run["evaluation"]["reportPath"] = str(report_path)

    diagnostics = build_experiment_diagnostics({"experiments": [run]})

    assert diagnostics["runs"][0]["weakestLabels"][0]["label"] == "TRENDING_UP"
    assert diagnostics["runs"][0]["confusionPairs"][0] == {
        "expected": "TRENDING_UP",
        "predicted": "SIDEWAYS",
        "count": 2,
    }


def test_build_experiment_diagnostics_ignores_missing_report_file() -> None:
    run = eligible_run()
    run["evaluation"]["reportPath"] = "/tmp/not-present-market-regime-report.json"

    diagnostics = build_experiment_diagnostics({"experiments": [run]})

    assert diagnostics["runs"][0]["reportPath"] == "/tmp/not-present-market-regime-report.json"
    assert diagnostics["runs"][0]["confusionPairs"] == []


def test_sort_diagnostic_runs_uses_accuracy_lift_then_validation() -> None:
    rows = [
        {"runId": "low", "accuracy": 0.7, "liftOverBaseline": 0.5, "validationAccuracy": 0.9},
        {"runId": "high", "accuracy": 0.8, "liftOverBaseline": 0.1, "validationAccuracy": 0.1},
        {"runId": "tie", "accuracy": 0.8, "liftOverBaseline": 0.2, "validationAccuracy": 0.1},
    ]

    ordered = [row["runId"] for row in sort_diagnostic_runs(rows)]

    assert ordered == ["tie", "high", "low"]


def test_weakest_labels_sort_by_f1_then_recall() -> None:
    result = weakest_labels(
        {
            "good": {"f1": 0.9, "recall": 0.9},
            "bad": {"f1": 0.1, "recall": 0.8},
            "worse": {"f1": 0.1, "recall": 0.2},
        },
        limit=2,
    )

    assert [row["label"] for row in result] == ["worse", "bad"]


def test_confusion_pairs_excludes_correct_predictions() -> None:
    result = confusion_pairs(
        {
            "SIDEWAYS": {"SIDEWAYS": 5, "TRENDING_UP": 2},
            "TRENDING_UP": {"SIDEWAYS": 3, "TRENDING_UP": 4},
        }
    )

    assert result == [
        {"expected": "TRENDING_UP", "predicted": "SIDEWAYS", "count": 3},
        {"expected": "SIDEWAYS", "predicted": "TRENDING_UP", "count": 2},
    ]


def test_render_diagnostics_markdown_includes_empty_state() -> None:
    markdown = render_diagnostics_markdown(build_experiment_diagnostics({"experiments": []}))

    assert "# Market Regime Experiment Diagnostics" in markdown
    assert "- No experiments recorded yet." in markdown
