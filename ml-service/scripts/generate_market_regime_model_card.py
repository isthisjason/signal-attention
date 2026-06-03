import argparse
import json
import sys
from pathlib import Path

sys.path.append(str(Path(__file__).resolve().parents[1]))

from app.services.market_regime_model_card import render_model_card


def main() -> None:
    args = parse_args()
    promotion = load_promotion(args.promotion)
    card = render_model_card(promotion)
    write_model_card(args.output, card)
    print(str(args.output))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate a Markdown model card from a promoted market-regime run.")
    parser.add_argument("--promotion", type=Path, default=Path("models/experiments/promoted-market-regime.json"))
    parser.add_argument("--output", type=Path, default=Path("models/experiments/market-regime-model-card.md"))
    return parser.parse_args()


def load_promotion(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def write_model_card(output: Path, content: str) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(content, encoding="utf-8")


if __name__ == "__main__":
    main()
