import json

from app.services.market_regime_model_card import render_model_card
from scripts.generate_market_regime_model_card import load_promotion, write_model_card


def promotion_summary() -> dict:
    return {
        "status": "promoted",
        "selectedRun": {
            "name": "baseline",
            "runId": "run-1",
            "modelVersion": "local-transformer-v1",
            "architecture": "attention-transformer-v2",
            "featureVersion": "torch-market-regime-features/v1",
            "sequenceLength": 20,
            "dataset": {"name": "candles.csv", "sha256": "dataset-hash"},
            "artifact": {"name": "market-regime.pt", "sha256": "artifact-hash"},
            "evaluation": {
                "evaluationScope": "holdout",
                "accuracy": 0.75,
                "baselineAccuracy": 0.5,
                "liftOverBaseline": 0.25,
                "confidence": {"average": 80},
                "windowCount": 44,
            },
            "training": {"trainWindowCount": 176, "validationWindowCount": 44},
            "promotionGate": {"eligible": True, "failures": []},
        },
    }


def test_render_model_card_includes_key_sections() -> None:
    card = render_model_card(promotion_summary())

    assert "# Market Regime Model Card" in card
    assert "- Run ID: run-1" in card
    assert "- Artifact SHA-256: artifact-hash" in card
    assert "- Lift over baseline: 0.25" in card
    assert "- Architecture: attention-transformer-v2" in card
    assert "- Sequence length: 20" in card
    assert "- Training windows: 176" in card
    assert "- Evaluation windows: 44" in card
    assert "not trading advice" in card


def test_render_model_card_handles_missing_selected_run() -> None:
    card = render_model_card({"status": "no_eligible_run", "selectedRun": None})

    assert "- Promotion status: no_eligible_run" in card
    assert "- Experiment: N/A" in card
    assert "- Eligible: False" in card


def test_load_and_write_model_card_use_json_and_markdown_files(tmp_path) -> None:
    promotion_path = tmp_path / "promotion.json"
    output_path = tmp_path / "cards" / "card.md"
    promotion_path.write_text(json.dumps(promotion_summary()), encoding="utf-8")

    loaded = load_promotion(promotion_path)
    write_model_card(output_path, render_model_card(loaded))

    assert loaded["status"] == "promoted"
    assert output_path.read_text(encoding="utf-8").startswith("# Market Regime Model Card")
