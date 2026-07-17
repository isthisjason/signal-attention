import argparse
import json
import sys
from pathlib import Path
from typing import Any

sys.path.append(str(Path(__file__).resolve().parents[1]))

from app.services.market_regime_experiment import load_experiment_registry


COLUMNS = [
    ("name", "experiment"),
    ("runId", "run"),
    ("valAccuracy", "val acc"),
    ("evalAccuracy", "eval acc"),
    ("macroF1", "macro f1"),
    ("balancedAccuracy", "bal acc"),
    ("liftOverBaseline", "lift"),
    ("meanConfidence", "conf"),
    ("rulesDisagreementRate", "rule diff"),
    ("labelDrift", "label drift"),
    ("attentionConcentration", "attn conc"),
    ("seed", "seed"),
    ("dropout", "dropout"),
    ("positionalEncoding", "pos enc"),
    ("gitCommit", "commit"),
]


def main() -> None:
    args = parse_args()
    registry = load_experiment_registry(args.experiments_dir / "index.json")
    rows = sort_rows(build_comparison_rows(registry))
    if args.output is not None:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(rows, indent=2) + "\n", encoding="utf-8")
    print(format_table(rows))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Compare recorded market-regime experiment runs.")
    parser.add_argument("--experiments-dir", type=Path, default=Path("models/experiments"))
    parser.add_argument("--output", type=Path, help="Optional path to also write the comparison as JSON.")
    return parser.parse_args()


def build_comparison_rows(registry: dict[str, Any]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for experiment in registry.get("experiments", []):
        training = experiment.get("training", {}) or {}
        evaluation = experiment.get("evaluation", {}) or {}
        diagnostics = experiment.get("diagnostics", {}) or {}
        reproducibility = experiment.get("reproducibility", {}) or {}
        rows.append(
            {
                "name": experiment.get("name"),
                "runId": experiment.get("runId"),
                "valAccuracy": training.get("validationAccuracy"),
                "evalAccuracy": evaluation.get("accuracy"),
                "macroF1": evaluation.get("macroF1"),
                "balancedAccuracy": evaluation.get("balancedAccuracy"),
                "liftOverBaseline": evaluation.get("liftOverBaseline"),
                "meanConfidence": confidence_mean(evaluation),
                "rulesDisagreementRate": first_present(
                    diagnostics.get("rulesDisagreementRate"),
                    evaluation.get("rulesDisagreementRate"),
                    evaluation.get("baselineDisagreementRate"),
                ),
                "labelDrift": label_drift_summary(diagnostics.get("labelDistributionDrift") or evaluation.get("labelDistributionDrift")),
                "attentionConcentration": first_present(
                    diagnostics.get("attentionConcentration"),
                    diagnostics.get("meanAttentionConcentration"),
                    evaluation.get("attentionConcentration"),
                ),
                "seed": reproducibility.get("seed"),
                "gitCommit": short_commit(reproducibility.get("gitCommit")),
                "dropout": training.get("dropout"),
                "positionalEncoding": training.get("usePositionalEncoding"),
            }
        )
    return rows


def sort_rows(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Best runs first, ranking by evaluation accuracy then validation accuracy."""
    return sorted(
        rows,
        key=lambda row: (sortable(row["evalAccuracy"]), sortable(row["valAccuracy"])),
        reverse=True,
    )


def confidence_mean(evaluation: dict[str, Any]) -> Any:
    summary = evaluation.get("confidenceSummary")
    if isinstance(summary, dict):
        return first_present(summary.get("mean"), summary.get("average"))
    return evaluation.get("meanConfidence")


def first_present(*values: Any) -> Any:
    for value in values:
        if value is not None:
            return value
    return None


def label_drift_summary(value: Any) -> Any:
    if isinstance(value, (int, float)):
        return value
    if not isinstance(value, dict) or not value:
        return None
    # Collapse per-label drift into the largest absolute shift so runs remain table-comparable.
    numeric_values = [abs(float(item)) for item in value.values() if isinstance(item, (int, float))]
    return max(numeric_values) if numeric_values else None


def sortable(value: Any) -> float:
    return float(value) if isinstance(value, (int, float)) else float("-inf")


def short_commit(commit: Any) -> Any:
    if isinstance(commit, str) and len(commit) > 8:
        return commit[:8]
    return commit


def format_table(rows: list[dict[str, Any]]) -> str:
    headers = [header for _, header in COLUMNS]
    cells = [[render(row.get(key)) for key, _ in COLUMNS] for row in rows]
    widths = [
        max(len(headers[index]), *(len(line[index]) for line in cells)) if cells else len(headers[index])
        for index in range(len(headers))
    ]
    lines = [format_row(headers, widths), format_row(["-" * width for width in widths], widths)]
    lines.extend(format_row(line, widths) for line in cells)
    if not rows:
        lines.append("(no experiments recorded yet)")
    return "\n".join(lines)


def format_row(values: list[str], widths: list[int]) -> str:
    return "  ".join(value.ljust(widths[index]) for index, value in enumerate(values))


def render(value: Any) -> str:
    if value is None:
        return "-"
    if isinstance(value, bool):
        return "yes" if value else "no"
    return str(value)


if __name__ == "__main__":
    main()
