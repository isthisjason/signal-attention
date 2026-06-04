import argparse
import json
import sys
from pathlib import Path

sys.path.append(str(Path(__file__).resolve().parents[1]))

from app.services.market_regime_diagnostics import (
    build_experiment_diagnostics,
    render_diagnostics_markdown,
)
from app.services.market_regime_experiment import load_experiment_registry


def main() -> None:
    args = parse_args()
    registry = load_experiment_registry(args.experiments_dir / "index.json")
    diagnostics = build_experiment_diagnostics(registry)
    write_json(args.output, diagnostics)
    if args.markdown_output is not None:
        write_markdown(args.markdown_output, render_diagnostics_markdown(diagnostics))
    print(render_stdout_summary(diagnostics))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Diagnose recorded market-regime experiment runs.")
    parser.add_argument("--experiments-dir", type=Path, default=Path("models/experiments"))
    parser.add_argument("--output", type=Path, default=Path("models/experiments/market-regime-diagnostics.json"))
    parser.add_argument("--markdown-output", type=Path)
    return parser.parse_args()


def write_json(output: Path, diagnostics: dict) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(diagnostics, indent=2) + "\n", encoding="utf-8")


def write_markdown(output: Path, content: str) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(content, encoding="utf-8")


def render_stdout_summary(diagnostics: dict) -> str:
    summary = diagnostics["summary"]
    best = summary.get("bestRun")
    best_label = "none" if best is None else f"{best.get('name') or 'unnamed'} / {best.get('runId') or 'no-run-id'}"
    return (
        "market-regime diagnostics: "
        f"{summary['totalRuns']} runs, "
        f"{summary['evaluatedRuns']} evaluated, "
        f"{summary['promotionEligibleRuns']} promotion eligible, "
        f"best run {best_label}"
    )


if __name__ == "__main__":
    main()
