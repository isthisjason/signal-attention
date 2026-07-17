# Attention Experiment Evidence

## July 17, 2026 Regime-Diverse Wave

This experiment asks whether the attention-v2 model can learn the deterministic regime-label function better than guessing the most common label. It does not measure trading profitability, price prediction, or agreement with independent market truth.

Dataset:

- Provider: Coinbase Exchange public candles endpoint.
- Product and interval: `BTC-USD`, one-hour candles, January 1, 2022 through December 31, 2024.
- CSV SHA-256: `3f8625f0d5388883c1973fee06e1233f7caef04e6d8056a06558c09b5617fbc4`.
- Candles: 26,301, with one reported three-hour source gap that was not filled.
- Sequence windows: 26,282 using sequence length 20.
- Split: 15,770 train, 5,256 validation, and 5,256 untouched test windows.
- Test weak labels: 4,414 `SIDEWAYS`, 500 `TRENDING_UP`, 342 `TRENDING_DOWN`, and 0 `HIGH_VOLATILITY`.

The readiness check passed because the three observed labels occur with sufficient support in every partition. `HIGH_VOLATILITY` remains an explicit limitation: the current rule threshold did not produce that label anywhere in this range.

## Fixed Configuration

All candidates used attention transformer v2, CPU execution, positional encoding, sequence length 20, batch size 64, at most 50 epochs, patience 8, inverse-frequency class weighting, and validation macro-F1 checkpoint selection. The grid varied only seed (`42`, `43`) and dropout (`0.1`, `0.2`). Evaluation used the artifact-pinned final 20% test boundary and verified the dataset hash before scoring.

| Seed | Dropout | Test accuracy | Macro-F1 | Balanced accuracy | Majority baseline | Lift |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 42 | 0.1 | 0.9808 | 0.9560 | 0.9680 | 0.8398 | 0.1410 |
| 43 | 0.2 | 0.9768 | 0.9462 | 0.9704 | 0.8398 | 0.1370 |
| 42 | 0.2 | 0.9625 | 0.9135 | 0.9162 | 0.8398 | 0.1227 |
| 43 | 0.1 | 0.9561 | 0.9022 | 0.9357 | 0.8398 | 0.1163 |

All four candidates passed the unchanged local research gates: holdout evaluation, accuracy of at least 0.60, and lift of at least 0.05. The selected local candidate was seed 42 with dropout 0.1. Its per-label test F1 was 0.9885 for `SIDEWAYS`, 0.9467 for `TRENDING_UP`, and 0.9329 for `TRENDING_DOWN`.

## Reproduction

From `ml-service/` with the optional Torch requirements installed:

```bash
../.venv/bin/python scripts/fetch_market_regime_candles.py
../.venv/bin/python scripts/inspect_market_regime_dataset.py \
  --csv-path ../data/generated/coinbase-btc-usd-1h-2022-2024.csv
../.venv/bin/python scripts/run_market_regime_sweep.py \
  --csv-path ../data/generated/coinbase-btc-usd-1h-2022-2024.csv \
  --experiments-dir models/evidence-wave-experiments \
  --models-dir models/evidence-wave \
  --sequence-length 20 --epochs 50 --batch-size 64 --patience 8 \
  --seeds 42,43 --dropouts 0.1,0.2 \
  --positional-encoding-modes on \
  --architecture attention-transformer-v2
../.venv/bin/python scripts/promote_market_regime_experiment.py \
  --experiments-dir models/evidence-wave-experiments \
  --output models/evidence-wave-experiments/promoted-market-regime.json
```

The fetched CSV, provenance file, model artifacts, reports, registry, and promotion manifest are generated research outputs and remain ignored. The committed evidence contains the fixed inputs, hashes, configuration, aggregate results, and limitations needed to review the claim without treating a local artifact as a deployable model.
