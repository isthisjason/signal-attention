import json
from pathlib import Path
from typing import Any

from app.services.market_regime_governance import evaluate_promotion_gates


def build_experiment_diagnostics(registry: dict[str, Any]) -> dict[str, Any]:
    experiments = list(registry.get("experiments", []))
    runs = [summarize_run(experiment) for experiment in experiments]
    evaluated_runs = [run for run in runs if run["hasEvaluation"]]
    trained_runs = [run for run in runs if run["hasTraining"]]
    eligible_runs = [run for run in runs if run["promotionGate"]["eligible"]]
    # Finished runs come first so an abandoned local experiment does not become the headline result.
    ranked_runs = sort_diagnostic_runs(evaluated_runs)

    return {
        "summary": {
            "totalRuns": len(runs),
            "trainedRuns": len(trained_runs),
            "evaluatedRuns": len(evaluated_runs),
            "promotionEligibleRuns": len(eligible_runs),
            "bestRun": ranked_runs[0] if ranked_runs else None,
        },
        "runs": ranked_runs + [run for run in runs if not run["hasEvaluation"]],
        "incompleteRuns": [run for run in runs if not run["hasTraining"] or not run["hasEvaluation"]],
    }


def summarize_run(experiment: dict[str, Any]) -> dict[str, Any]:
    training = experiment.get("training") or {}
    evaluation = experiment.get("evaluation") or {}
    report = load_evaluation_report(evaluation.get("reportPath"))
    metrics = report.get("metrics") if isinstance(report, dict) else {}
    evaluation_metrics = metrics if isinstance(metrics, dict) and metrics else evaluation
    forward_outcomes = evaluation.get("forwardOutcomeAnalysis") or report.get("forwardOutcomeAnalysis")
    gate = evaluate_promotion_gates(experiment)

    # Keep the run summary compact enough for registry-level diagnostics while preserving the signals that explain ranking.
    return {
        "name": experiment.get("name"),
        "runId": experiment.get("runId"),
        "hasTraining": bool(training),
        "hasEvaluation": bool(evaluation),
        "validationAccuracy": training.get("validationAccuracy"),
        "validationMacroF1": training.get("validationMacroF1"),
        "accuracy": evaluation.get("accuracy"),
        "macroF1": evaluation.get("macroF1"),
        "balancedAccuracy": evaluation.get("balancedAccuracy"),
        "baselineAccuracy": evaluation.get("baselineAccuracy"),
        "liftOverBaseline": evaluation.get("liftOverBaseline"),
        "confidence": evaluation.get("confidence"),
        "labelDistribution": evaluation.get("labelDistribution"),
        "windowRanges": evaluation.get("windowRanges") or experiment.get("windowRanges"),
        "holdoutSource": evaluation.get("holdoutSource"),
        "promotionGate": gate,
        "weakestLabels": weakest_labels(evaluation_metrics.get("perLabel") or {}),
        "confusionPairs": confusion_pairs(evaluation_metrics.get("confusionMatrix") or {}),
        "forwardOutcomeSummary": summarize_forward_outcomes(forward_outcomes),
        "reportPath": evaluation.get("reportPath"),
    }


def load_evaluation_report(report_path: Any) -> dict[str, Any]:
    if not isinstance(report_path, str) or not report_path:
        return {}
    path = Path(report_path)
    if not path.exists() or not path.is_file():
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        # Local experiment folders get messy, so one broken report should not take down the whole review page.
        return {}


def weakest_labels(per_label: dict[str, Any], limit: int = 3) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for label, metrics in per_label.items():
        if not isinstance(metrics, dict):
            continue
        rows.append(
            {
                "label": label,
                "f1": metrics.get("f1"),
                "recall": metrics.get("recall"),
                "precision": metrics.get("precision"),
                "support": metrics.get("support"),
            }
        )
    return sorted(rows, key=lambda row: (metric_or_inf(row["f1"]), metric_or_inf(row["recall"])))[:limit]


def confusion_pairs(confusion_matrix: dict[str, Any], limit: int = 5) -> list[dict[str, Any]]:
    pairs: list[dict[str, Any]] = []
    for expected, predictions in confusion_matrix.items():
        if not isinstance(predictions, dict):
            continue
        for predicted, count in predictions.items():
            if expected == predicted or not isinstance(count, int) or count <= 0:
                continue
            pairs.append({"expected": expected, "predicted": predicted, "count": count})
    return sorted(pairs, key=lambda pair: pair["count"], reverse=True)[:limit]


def summarize_forward_outcomes(analysis: Any) -> dict[str, Any] | None:
    if not isinstance(analysis, dict):
        return None
    groups = analysis.get("byPredictedLabel")
    if not isinstance(groups, dict):
        return None

    # Zero-support labels are useful in the full report, but they should not win a Model Lab comparison by accident.
    observed = [
        outcome_summary(label, metrics)
        for label, metrics in groups.items()
        if isinstance(metrics, dict) and metric_or_zero(metrics.get("support")) > 0
    ]
    if not observed:
        return None
    return {
        "horizonCandles": analysis.get("horizonCandles"),
        "eligibleWindowCount": analysis.get("eligibleWindowCount"),
        "excludedTailWindowCount": analysis.get("excludedTailWindowCount"),
        "highestForwardVolatility": max(
            observed,
            key=lambda item: metric_or_negative_inf(item.get("meanRealizedVolatilityPercent")),
        ),
        "strongestAverageAbsoluteMove": max(
            observed,
            key=lambda item: metric_or_negative_inf(item.get("meanAbsoluteForwardReturnPercent")),
        ),
    }


def outcome_summary(label: str, metrics: dict[str, Any]) -> dict[str, Any]:
    return {
        "label": label,
        "support": metrics.get("support"),
        "meanAbsoluteForwardReturnPercent": metrics.get("meanAbsoluteForwardReturnPercent"),
        "meanRealizedVolatilityPercent": metrics.get("meanRealizedVolatilityPercent"),
    }


def sort_diagnostic_runs(runs: list[dict[str, Any]]) -> list[dict[str, Any]]:
    # Accuracy leads the table while lift and validation quality settle the close calls.
    return sorted(
        runs,
        key=lambda run: (
            metric_or_negative_inf(run["accuracy"]),
            metric_or_negative_inf(run["liftOverBaseline"]),
            metric_or_negative_inf(run["validationAccuracy"]),
        ),
        reverse=True,
    )


def render_diagnostics_markdown(diagnostics: dict[str, Any]) -> str:
    summary = diagnostics["summary"]
    lines = [
        "# Market Regime Experiment Diagnostics",
        "",
        "## Summary",
        f"- Total runs: {summary['totalRuns']}",
        f"- Trained runs: {summary['trainedRuns']}",
        f"- Evaluated runs: {summary['evaluatedRuns']}",
        f"- Promotion eligible runs: {summary['promotionEligibleRuns']}",
        f"- Best run: {best_run_name(summary.get('bestRun'))}",
        "",
        "## Runs",
    ]
    if not diagnostics["runs"]:
        lines.append("- No experiments recorded yet.")
    for run in diagnostics["runs"]:
        lines.append(
            "- "
            f"{run.get('name') or 'unnamed'} / {run.get('runId') or 'no-run-id'}: "
            f"accuracy {render_metric(run.get('accuracy'))}, "
            f"macro f1 {render_metric(run.get('macroF1'))}, "
            f"lift {render_metric(run.get('liftOverBaseline'))}, "
            f"validation {render_metric(run.get('validationAccuracy'))}, "
            f"eligible {run['promotionGate']['eligible']}"
        )
    lines.extend(["", "## Incomplete Runs"])
    if not diagnostics["incompleteRuns"]:
        lines.append("- None")
    for run in diagnostics["incompleteRuns"]:
        missing = []
        if not run["hasTraining"]:
            missing.append("training")
        if not run["hasEvaluation"]:
            missing.append("evaluation")
        lines.append(f"- {run.get('name') or 'unnamed'} / {run.get('runId') or 'no-run-id'} missing {', '.join(missing)}")
    lines.append("")
    return "\n".join(lines)


def best_run_name(run: dict[str, Any] | None) -> str:
    if not run:
        return "N/A"
    return f"{run.get('name') or 'unnamed'} / {run.get('runId') or 'no-run-id'}"


def render_metric(value: Any) -> str:
    if isinstance(value, (int, float)):
        return str(value)
    return "N/A"


def metric_or_negative_inf(value: Any) -> float:
    return float(value) if isinstance(value, (int, float)) else float("-inf")


def metric_or_inf(value: Any) -> float:
    return float(value) if isinstance(value, (int, float)) else float("inf")


def metric_or_zero(value: Any) -> float:
    return float(value) if isinstance(value, (int, float)) else 0.0
