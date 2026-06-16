from app.services.market_regime_experiment import append_or_merge_run
from scripts.compare_market_regime_experiments import (
    build_comparison_rows,
    format_table,
    short_commit,
    sort_rows,
)


def test_append_or_merge_run_keeps_runs_with_distinct_run_ids() -> None:
    registry = {"experiments": []}

    registry = append_or_merge_run(registry, {"name": "demo", "runId": "run-1", "training": {}})
    registry = append_or_merge_run(registry, {"name": "demo", "runId": "run-2", "training": {}})

    assert [run["runId"] for run in registry["experiments"]] == ["run-1", "run-2"]


def test_append_or_merge_run_merges_eval_into_matching_run() -> None:
    registry = append_or_merge_run({"experiments": []}, {"name": "demo", "runId": "run-1", "training": {"validationAccuracy": 0.8}})

    merged = append_or_merge_run(registry, {"name": "demo", "runId": "run-1", "evaluation": {"accuracy": 0.7}})

    assert len(merged["experiments"]) == 1
    entry = merged["experiments"][0]
    assert entry["training"]["validationAccuracy"] == 0.8
    assert entry["evaluation"]["accuracy"] == 0.7


def test_build_comparison_rows_pulls_training_and_evaluation_fields() -> None:
    registry = {
        "experiments": [
            {
                "name": "demo",
                "runId": "run-1",
                "training": {"validationAccuracy": 0.8, "dropout": 0.1, "usePositionalEncoding": True},
                "evaluation": {
                    "accuracy": 0.7,
                    "liftOverBaseline": 0.2,
                    "confidenceSummary": {"mean": 0.74},
                    "baselineDisagreementRate": 0.15,
                    "labelDistributionDrift": {"SIDEWAYS": -0.1, "TRENDING_UP": 0.2},
                },
                "diagnostics": {"attentionConcentration": 0.61},
                "reproducibility": {"seed": 42, "gitCommit": "abcdef1234567890"},
            }
        ]
    }

    rows = build_comparison_rows(registry)

    assert rows == [
        {
            "name": "demo",
            "runId": "run-1",
            "valAccuracy": 0.8,
            "evalAccuracy": 0.7,
            "liftOverBaseline": 0.2,
            "meanConfidence": 0.74,
            "rulesDisagreementRate": 0.15,
            "labelDrift": 0.2,
            "attentionConcentration": 0.61,
            "seed": 42,
            "gitCommit": "abcdef12",
            "dropout": 0.1,
            "positionalEncoding": True,
        }
    ]


def test_sort_rows_ranks_by_eval_then_validation_accuracy() -> None:
    rows = [
        {"runId": "low", "evalAccuracy": 0.5, "valAccuracy": 0.9},
        {"runId": "high", "evalAccuracy": 0.9, "valAccuracy": 0.1},
        {"runId": "missing", "evalAccuracy": None, "valAccuracy": 0.99},
    ]

    ordered = [row["runId"] for row in sort_rows(rows)]

    assert ordered == ["high", "low", "missing"]


def test_short_commit_truncates_long_hashes() -> None:
    assert short_commit("abcdef1234567890") == "abcdef12"
    assert short_commit(None) is None


def test_format_table_reports_when_no_experiments_exist() -> None:
    assert "(no experiments recorded yet)" in format_table([])
