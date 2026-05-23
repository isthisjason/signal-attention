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
    backend --> regime[Market Regime API]
    backend --> audit[Audit Events]
    backend --> mlclient[ML Risk Client]

    strategies --> postgres[(PostgreSQL)]
    marketdata --> postgres
    backtests --> postgres
    risk --> postgres
    paper --> postgres
    dashboard --> postgres
    regime --> postgres
    audit --> postgres

    mlclient --> ml[FastAPI ML Service]
    ml --> scorer[Rule-Based Risk Scorer]
    ml --> regimeClassifier[CPU Market Regime Classifier]
```

## Runtime Services

- `postgres`: Stores strategies, candles, backtests, trades, risk policies, paper sessions, positions, orders, and audit events.
- `backend`: Owns REST APIs, validation, persistence, deterministic backtesting, risk evaluation, paper trading, dashboard aggregation, and market regime proxying.
- `ml-service`: Provides CPU-first rule-based strategy risk scoring and market regime classification.
- `frontend`: Provides a local React dashboard/workbench for importing candles, creating SMA strategies, running backtests, scoring ML risk, managing paper sessions, and reviewing summary, strategy performance, audit, and market regime status.

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
  --cpu
python scripts/evaluate_market_regime_model.py \
  --csv-path ../data/btc-usd-1h-sample.csv \
  --artifact models/market-regime.pt \
  --output models/market-regime-evaluation.json
```

The training command saves `models/market-regime.pt.manifest.json` beside the artifact. The evaluation command saves accuracy, per-label metrics, confusion matrix, confidence summary, and sample predictions. Torch-mode API responses include optional provenance fields so the dashboard can identify mode, model version, feature version, sequence length, and artifact name.

The optional Compose profile exposes a torch-enabled ML service on port `8001`:

```bash
docker compose --profile torch up --build ml-service-torch
```

## Current Boundaries

- No real-money trading, broker integration, custody, trade recommendations, or live order execution. These are permanent boundaries, not deferred roadmap items.
- No authentication or multi-user model yet.
- The current default market regime path is deterministic and CPU-safe; torch inference is optional and artifact-backed.
- The dashboard is local and unauthenticated; account isolation remains future scope.
