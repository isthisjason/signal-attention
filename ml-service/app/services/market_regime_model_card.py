from typing import Any


def render_model_card(promotion: dict[str, Any]) -> str:
    selected = promotion.get("selectedRun") or {}
    evaluation = selected.get("evaluation") or {}
    artifact = selected.get("artifact") or evaluation.get("artifact") or {}
    dataset = selected.get("dataset") or evaluation.get("dataset") or {}
    gate = selected.get("promotionGate") or {}
    failures = gate.get("failures") or []

    lines = [
        "# Market Regime Model Card",
        "",
        "## Summary",
        f"- Promotion status: {promotion.get('status', 'unknown')}",
        f"- Experiment: {selected.get('name', 'N/A')}",
        f"- Run ID: {selected.get('runId', 'N/A')}",
        f"- Model version: {selected.get('modelVersion', 'N/A')}",
        f"- Feature version: {selected.get('featureVersion', 'N/A')}",
        "",
        "## Data And Artifact",
        f"- Dataset: {dataset.get('name', 'N/A')}",
        f"- Dataset SHA-256: {dataset.get('sha256', 'N/A')}",
        f"- Artifact: {artifact.get('name', 'N/A')}",
        f"- Artifact SHA-256: {artifact.get('sha256', 'N/A')}",
        "",
        "## Evaluation",
        f"- Scope: {evaluation.get('evaluationScope', 'N/A')}",
        f"- Accuracy: {render_metric(evaluation.get('accuracy'))}",
        f"- Baseline accuracy: {render_metric(evaluation.get('baselineAccuracy'))}",
        f"- Lift over baseline: {render_metric(evaluation.get('liftOverBaseline'))}",
        f"- Average confidence: {render_metric((evaluation.get('confidence') or {}).get('average'))}",
        "",
        "## Promotion Gates",
        f"- Eligible: {gate.get('eligible', False)}",
    ]
    if failures:
        lines.extend(f"- Failure: {failure}" for failure in failures)
    else:
        lines.append("- Failure: none")
    lines.extend(
        [
            "",
            "## Limitations",
            "- Expected labels come from the rule-based classifier, not independent market ground truth.",
            "- This model card supports local research review and is not trading advice.",
            "- The default SignalAttention demo remains CPU-safe and rule-based unless torch mode is explicitly enabled.",
            "",
            "## Deployment Note",
            "- Do not enable torch mode unless the artifact path, feature version, and local dependencies are verified.",
            "",
        ]
    )
    return "\n".join(lines)


def render_metric(value: Any) -> str:
    if isinstance(value, (int, float)):
        return str(value)
    return "N/A"
