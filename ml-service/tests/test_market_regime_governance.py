from app.services.market_regime_governance import (
    ExperimentGates,
    eligible_promotion_candidates,
    evaluate_promotion_gates,
    select_promotion_candidate,
)


def eligible_entry() -> dict:
    return {
        "artifact": {"sha256": "artifact-hash"},
        "evaluation": {
            "accuracy": 0.72,
            "liftOverBaseline": 0.12,
            "evaluationScope": "holdout",
        },
    }


def test_evaluate_promotion_gates_accepts_eligible_run() -> None:
    result = evaluate_promotion_gates(eligible_entry())

    assert result["eligible"] is True
    assert result["failures"] == []
    assert result["gates"]["minAccuracy"] == 0.6


def test_evaluate_promotion_gates_rejects_low_accuracy() -> None:
    entry = eligible_entry()
    entry["evaluation"]["accuracy"] = 0.59

    result = evaluate_promotion_gates(entry)

    assert result["eligible"] is False
    assert "accuracy is below 0.6" in result["failures"]


def test_evaluate_promotion_gates_rejects_low_lift() -> None:
    entry = eligible_entry()
    entry["evaluation"]["liftOverBaseline"] = 0.01

    result = evaluate_promotion_gates(entry)

    assert result["eligible"] is False
    assert "lift over baseline is below 0.05" in result["failures"]


def test_evaluate_promotion_gates_rejects_missing_evaluation() -> None:
    result = evaluate_promotion_gates({"artifact": {"sha256": "artifact-hash"}})

    assert result["eligible"] is False
    assert "missing evaluation metrics" in result["failures"]


def test_evaluate_promotion_gates_rejects_non_holdout_scope() -> None:
    entry = eligible_entry()
    entry["evaluation"]["evaluationScope"] = "full"

    result = evaluate_promotion_gates(entry)

    assert result["eligible"] is False
    assert "evaluation scope is not holdout" in result["failures"]


def test_evaluate_promotion_gates_allows_custom_thresholds() -> None:
    entry = eligible_entry()
    entry["evaluation"]["evaluationScope"] = "full"

    result = evaluate_promotion_gates(entry, ExperimentGates(min_accuracy=0.8, min_lift=0.2, require_holdout=False))

    assert result["eligible"] is False
    assert "evaluation scope is not holdout" not in result["failures"]
    assert "accuracy is below 0.8" in result["failures"]


def test_eligible_promotion_candidates_sort_by_accuracy_lift_then_validation() -> None:
    registry = {
        "experiments": [
            {**eligible_entry(), "runId": "middle", "training": {"validationAccuracy": 0.9}},
            {
                **eligible_entry(),
                "runId": "best",
                "evaluation": {"accuracy": 0.8, "liftOverBaseline": 0.07, "evaluationScope": "holdout"},
                "training": {"validationAccuracy": 0.1},
            },
            {
                **eligible_entry(),
                "runId": "tie-break",
                "evaluation": {"accuracy": 0.72, "liftOverBaseline": 0.12, "evaluationScope": "holdout"},
                "training": {"validationAccuracy": 0.95},
            },
        ]
    }

    candidates = eligible_promotion_candidates(registry)

    assert [candidate["runId"] for candidate in candidates] == ["best", "tie-break", "middle"]


def test_eligible_promotion_candidates_ignore_missing_metrics() -> None:
    registry = {"experiments": [{**eligible_entry(), "runId": "good"}, {"runId": "bad", "evaluation": {}}]}

    candidates = eligible_promotion_candidates(registry)

    assert [candidate["runId"] for candidate in candidates] == ["good"]


def test_select_promotion_candidate_returns_none_when_no_runs_are_eligible() -> None:
    registry = {"experiments": [{"runId": "bad", "evaluation": {"accuracy": 0.1}}]}

    assert select_promotion_candidate(registry) is None
