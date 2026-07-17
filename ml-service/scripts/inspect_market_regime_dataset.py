import argparse
import json
import sys
from pathlib import Path

sys.path.append(str(Path(__file__).resolve().parents[1]))

from app.services.market_regime_dataset import inspect_dataset


def main() -> None:
    args = parse_args()
    report = inspect_dataset(
        args.csv_path,
        args.sequence_length,
        validation_ratio=args.validation_ratio,
        test_ratio=args.test_ratio,
        min_windows=args.min_windows,
        min_labels_per_split=args.min_labels_per_split,
        min_evaluation_support=args.min_evaluation_support,
        granularity_seconds=args.granularity_seconds,
    )
    payload = json.dumps(report, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(payload, encoding="utf-8")
    else:
        print(payload, end="")
    if not report["ready"]:
        raise SystemExit(1)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Inspect weak-label coverage before market-regime training.")
    parser.add_argument("--csv-path", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--sequence-length", type=int, default=20)
    parser.add_argument("--validation-ratio", type=float, default=0.2)
    parser.add_argument("--test-ratio", type=float, default=0.2)
    parser.add_argument("--min-windows", type=int, default=1000)
    parser.add_argument("--min-labels-per-split", type=int, default=2)
    parser.add_argument("--min-evaluation-support", type=int, default=20)
    parser.add_argument("--granularity-seconds", type=int, default=3600)
    return parser.parse_args()


if __name__ == "__main__":
    main()
