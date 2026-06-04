import json

from scripts.diagnose_market_regime_experiments import (
    render_stdout_summary,
    write_json,
    write_markdown,
)


def empty_diagnostics() -> dict:
    return {
        "summary": {
            "totalRuns": 0,
            "trainedRuns": 0,
            "evaluatedRuns": 0,
            "promotionEligibleRuns": 0,
            "bestRun": None,
        },
        "runs": [],
        "incompleteRuns": [],
    }


def test_write_json_creates_parent_directory(tmp_path) -> None:
    output = tmp_path / "experiments" / "diagnostics.json"

    write_json(output, empty_diagnostics())

    assert json.loads(output.read_text(encoding="utf-8"))["summary"]["totalRuns"] == 0


def test_write_markdown_creates_parent_directory(tmp_path) -> None:
    output = tmp_path / "experiments" / "diagnostics.md"

    write_markdown(output, "# Diagnostics\n")

    assert output.read_text(encoding="utf-8") == "# Diagnostics\n"


def test_render_stdout_summary_reports_empty_registry() -> None:
    summary = render_stdout_summary(empty_diagnostics())

    assert summary == "market-regime diagnostics: 0 runs, 0 evaluated, 0 promotion eligible, best run none"


def test_render_stdout_summary_reports_best_run() -> None:
    diagnostics = empty_diagnostics()
    diagnostics["summary"]["totalRuns"] = 1
    diagnostics["summary"]["evaluatedRuns"] = 1
    diagnostics["summary"]["bestRun"] = {"name": "baseline", "runId": "run-1"}

    summary = render_stdout_summary(diagnostics)

    assert "best run baseline / run-1" in summary
