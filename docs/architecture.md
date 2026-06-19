# SignalAttention Architecture

```mermaid
flowchart LR
    user[Developer / Swagger / curl] --> backend[Spring Boot Backend]
    user --> frontend[React Dashboard]
    frontend --> backend
    csv[Sample OHLCV CSV] --> backend

    backend --> strategies[Strategy CRUD]
    backend --> marketdata[Market Data Import]
    backend --> backtests[Backtesting Engine]
    backend --> risk[Risk Policy Engine]
    backend --> paper[Paper Trading Simulation]
    backend --> dashboard[Dashboard Summary API]
    backend --> alerts[Dashboard Risk Alerts]
    backend --> regime[Market Regime API]
    backend --> anomaly[Anomaly Check API]
    backend --> audit[Audit Events]
    backend --> mlclient[ML Risk Client]

    strategies --> postgres[(PostgreSQL)]
    marketdata --> postgres
    backtests --> postgres
    risk --> postgres
    paper --> postgres
    dashboard --> postgres
    alerts --> postgres
    regime --> postgres
    anomaly --> postgres
    audit --> postgres

    mlclient --> ml[FastAPI ML Service]
    ml --> scorer[Rule-Based Risk Scorer]
    ml --> regimeClassifier[CPU Market Regime Classifier]
    ml --> anomalyScorer[Rule-Based Anomaly Scorer]
```

## Runtime Services

- `postgres`: Stores strategies, candles, backtests, trades, risk policies, paper sessions, positions, orders, and audit events.
- `backend`: Owns REST APIs, validation, persistence, deterministic backtesting, equity/drawdown chart data, risk evaluation, paper trading, dashboard aggregation, derived risk alerts, model status proxying, persisted regime runs, derived run quality summaries, recent regime run comparison, regime grouped backtest analysis, and anomaly proxying.
- `ml-service`: Provides CPU-first rule-based strategy risk scoring, market regime classification, rolling regime runs with rule baseline comparison, model status, and anomaly checks.
- `frontend`: Provides a local React dashboard/workbench for importing candles, creating SMA strategies, running backtests, scoring ML risk, managing paper sessions, and reviewing summary, charts, risk alerts, strategy comparison, audit, anomaly, market regime status, and recent regime run comparison.

## Market Regime Modes

The market regime endpoint defaults to `MARKET_REGIME_MODE=rules`. This path uses deterministic feature extraction and rule-based labels, stays CPU-only, and is the mode used by the default Docker Compose setup.

`MARKET_REGIME_MODE=torch` enables the optional model-backed classifier. It requires `MARKET_REGIME_ARTIFACT_PATH` and the optional Torch dependencies; the default service does not require PyTorch or GPU drivers.

Torch artifacts are dictionaries saved with `torch.save` and must contain:

- `metadata.sequenceLength`
- `metadata.featureOrder`, matching the service feature order
- `metadata.labels`
- optional `metadata.normalization.mean` and `metadata.normalization.std`
- optional `metadata.model` Transformer settings
- `modelStateDict`

Create a local research artifact with:

```bash
cd ml-service
pip install -r requirements-torch.txt
python scripts/train_market_regime_model.py \
  --csv-path ../data/btc-usd-1h-sample.csv \
  --output models/market-regime.pt \
  --cpu --seed 42 --batch-size 32 --patience 10
python scripts/evaluate_market_regime_model.py \
  --csv-path ../data/btc-usd-1h-sample.csv \
  --artifact models/market-regime.pt \
  --output models/market-regime-evaluation.json \
  --holdout-ratio 0.2
python scripts/compare_market_regime_experiments.py \
  --experiments-dir models/experiments
python scripts/diagnose_market_regime_experiments.py \
  --experiments-dir models/experiments \
  --markdown-output models/experiments/market-regime-diagnostics.md
python scripts/promote_market_regime_experiment.py \
  --experiments-dir models/experiments
python scripts/generate_market_regime_model_card.py \
  --promotion models/experiments/promoted-market-regime.json
```

The training command runs a seeded, mini batch loop with per epoch validation and early stopping, keeps the best epoch, and saves `models/market-regime.pt.manifest.json` beside the artifact with the seed, git commit, torch version, model config, and per epoch history. The model uses light dropout and sinusoidal positional encoding by default, both adjustable from the command line. The evaluation command saves accuracy, per label metrics, a confusion matrix, a confidence summary, and sample predictions, plus a majority class baseline and the lift over it so the rule derived labels are read honestly rather than as ground truth. Each run is recorded in `models/experiments/index.json` under its own run id, and the compare script prints those runs sorted by accuracy. The diagnostics script summarizes local registry health, incomplete runs, weak labels, confusion pairs, and promotion gate status before promotion. The promotion script applies holdout, accuracy, lift, and artifact-hash gates before writing a local promotion summary; the model-card script renders that summary into Markdown for review. Torch mode API responses include optional provenance fields so the dashboard can identify mode, model version, feature version, sequence length, and artifact name.

The optional Compose profile exposes a torch-enabled ML service on port `8001`:

```bash
docker compose --profile torch up --build ml-service-torch
```

## Current Boundaries

- No real-money trading, broker integration, custody, trade recommendations, or live order execution. These are permanent boundaries, not deferred roadmap items.
- No authentication or multi-user model yet.
- The current default market regime path is deterministic and CPU-safe; torch inference is optional and artifact-backed.
- The dashboard is local and unauthenticated; account isolation remains future scope.
- Anomaly checks are deterministic research warnings based on recent candles, not trade signals.
