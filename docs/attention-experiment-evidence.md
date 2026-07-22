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

## July 22, 2026 Forward Outcome Review

The selected candidate was evaluated again with a 24-candle forward horizon. Of the 5,256 untouched test windows, 5,232 had a complete future horizon and the final 24 stayed in weak-label scoring but were excluded from this outcome review.

Forward return compares the input window's closing price with the close 24 candles later. Realized volatility is the unannualized population standard deviation of the 24 intervening hourly returns. These values come from after the model input, unlike the rule-derived labels used for training and accuracy scoring.

| Predicted regime | Windows | Mean forward return | Median forward return | Mean absolute move | Mean realized volatility |
| --- | ---: | ---: | ---: | ---: | ---: |
| `SIDEWAYS` | 4,363 | 0.1298% | 0.0507% | 1.8169% | 0.4612% |
| `TRENDING_UP` | 495 | 0.2630% | 0.2933% | 1.8243% | 0.5026% |
| `TRENDING_DOWN` | 374 | 0.5419% | 0.3934% | 2.4132% | 0.7209% |
| `HIGH_VOLATILITY` | 0 | N/A | N/A | N/A | N/A |

The `TRENDING_DOWN` cohort was followed by the largest moves and highest realized volatility, but its average and median forward returns were positive. That result is a useful limit on interpretation: the model's label summarizes the preceding sequence and does not predict that the next 24 hours will continue in the label's direction. The equivalent rule-label groups had a similar profile, which is unsurprising given the model's high agreement with those weak labels.

This review adds a market outcome that is independent of the training-label calculation, but it is not independent regime ground truth. It does not establish profitability, causal predictive power, investment suitability, or deployment readiness. The missing `HIGH_VOLATILITY` support also remains unresolved.

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
../.venv/bin/python scripts/evaluate_market_regime_model.py \
  --csv-path ../data/generated/coinbase-btc-usd-1h-2022-2024.csv \
  --artifact models/evidence-wave/market-regime-seed42-dropout0.1-poson-attention-v2.pt \
  --output models/evidence-wave/market-regime-seed42-dropout0.1-poson-attention-v2.evaluation.json \
  --forward-horizon-candles 24 \
  --experiment-name sweep-seed42-dropout0.1-poson-attention-v2 \
  --experiments-dir models/evidence-wave-experiments
../.venv/bin/python scripts/promote_market_regime_experiment.py \
  --experiments-dir models/evidence-wave-experiments \
  --output models/evidence-wave-experiments/promoted-market-regime.json
```

The fetched CSV, provenance file, model artifacts, reports, registry, and promotion manifest are generated research outputs and remain ignored. The committed evidence contains the fixed inputs, hashes, configuration, aggregate results, and limitations needed to review the claim without treating a local artifact as a deployable model.
