from app.services.market_regime_governance import ExperimentGates, evaluate_promotion_gates


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
