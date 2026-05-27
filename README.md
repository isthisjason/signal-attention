# SignalAttention

SignalAttention is a local trading strategy sandbox I built to practice backend API work, service boundaries, testing, and a little bit of ML-style analysis. It lets me import candle data, create a simple SMA crossover strategy, run a deterministic backtest, and ask a separate Python service for a risk score.

This is not a trading bot. It does not connect to brokers, it does not place real orders, and it should not be read as investment advice. The point is to explore strategy behavior, risk, simulation, and audit trails in a local app.

## What this is

The project is split into three pieces:

- `backend` is the Spring Boot API. It owns the database, strategy rules, candle imports, backtesting, paper trading, audit logs, and calls to the ML service.
- `ml-service` is a FastAPI app. I kept it separate because I wanted the project to feel like a real backend talking to a separate analysis service, even though everything runs locally.
- `frontend` is a React dashboard/workbench. It is not trying to be a polished trading terminal. It is mostly there so the demo flow is easier to click through.

The first version was deliberately simple: one strategy type, one sample dataset, deterministic rules, and a local Docker setup. Later work adds risk policies, paper trading, market regime analysis, and optional torch experiments, but the default path is still meant to run on a normal CPU.

## Stack

- Java 21 and Spring Boot 3 for the backend API
- PostgreSQL 16 for strategies, market data, backtests, trades, paper sessions, and audit events
- Python FastAPI for the risk and market-regime service
- React and TypeScript for the local dashboard
- Docker Compose for running the whole thing locally
- Swagger/OpenAPI for poking at the backend API

## Local Prerequisites

- Docker Desktop with WSL integration enabled
- Java 21
- Maven 3.9 or compatible Maven 3.x, or the included backend Maven wrapper
- Python 3.12 or compatible Python 3.x
- Node.js 24 or compatible current Node.js runtime

## Local Services

Planned local URLs:

- Backend API: `http://localhost:8080`
- Frontend dashboard: `http://localhost:5173`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- ML service: `http://localhost:8000`
- PostgreSQL: `localhost:5432`

## Demo Flow

The MVP demo flow is available from either Swagger/curl or the local React dashboard:

1. Start the stack with `docker compose up --build`.
2. Import sample BTC-USD candle data.
3. Create an SMA crossover strategy.
4. Run a backtest.
5. Review generated trades and metrics.
6. Request an ML risk score.
7. Confirm the risk score is persisted and audit events were recorded.
8. Optionally configure a risk policy and evaluate a simulated order.
9. Optionally create a paper session, submit manual paper orders, replay candles, and review the paper summary.
10. Optionally request a CPU-safe market regime classification from recent imported candles.

## Useful Local Commands

Run backend tests:

```bash
cd backend
./mvnw test
```

Run ML service tests:

```bash
cd ml-service
python3 -m pip install -r requirements.txt
python3 -m pytest
```

Install optional Torch dependencies for model-backed market regime experiments:

```bash
cd ml-service
python3 -m pip install -r requirements-torch.txt
```

Train and evaluate an optional local market-regime artifact:

```bash
cd ml-service
python scripts/train_market_regime_model.py \
  --csv-path ../data/btc-usd-1h-sample.csv \
  --output models/market-regime.pt \
  --cpu \
  --experiment-name btc-sample-v1
python scripts/evaluate_market_regime_model.py \
  --csv-path ../data/btc-usd-1h-sample.csv \
  --artifact models/market-regime.pt \
  --output models/market-regime-evaluation.json \
  --experiment-name btc-sample-v1
```

Training uses a chronological validation split, fits normalization on the training windows, and writes a sidecar `models/market-regime.pt.manifest.json` with dataset, feature, model, split counts, label distributions, final train loss, and validation accuracy. Passing `--experiment-name` also updates `models/experiments/index.json` with the artifact, manifest, and headline metrics. Evaluation writes JSON metrics for accuracy, per-label precision/recall/F1, confusion matrix, confidence summary, sample predictions, and the matching registry entry. These commands are optional research tooling; the default service still uses the CPU-safe rule classifier.

Start the full local stack:

```bash
docker compose up --build
```

Run the frontend directly during local development:

```bash
cd frontend
npm install
npm run dev
```

Run frontend tests:

```bash
cd frontend
npm run test
```

Run the end-to-end smoke checks against a running local stack:

```bash
python3 scripts/smoke_demo.py
```

The smoke script checks service reachability, imports the sample CSV, creates an SMA strategy, runs a backtest, persists an ML risk score, exercises paper trading, and verifies dashboard, market-regime, and audit endpoints. It is safe to rerun against an existing local database; duplicate sample candles are accepted as evidence that the dataset is already loaded.

The frontend reads `VITE_API_BASE_URL`, defaulting to `http://localhost:8080`.

The dashboard includes controls for importing the sample CSV, creating an SMA strategy, running a backtest, scoring ML risk, managing paper sessions, submitting manual paper orders, replaying candles, and reviewing summary/risk-alert/audit/regime panels.

## MVP API Flow

After the stack starts, use Swagger at `http://localhost:8080/swagger-ui.html` or equivalent HTTP requests:

- `POST /api/market-data/import`
- `GET /api/market-data/candles?symbol=BTC-USD&timeframe=1h`
- `POST /api/strategies`
- `POST /api/strategies/{id}/backtests`
- `GET /api/backtests/{id}`
- `GET /api/backtests/{id}/trades`
- `GET /api/backtests/{id}/metrics`
- `POST /api/backtests/{id}/ml-risk-score`
- `POST /api/strategies/{id}/risk-policy`
- `GET /api/strategies/{id}/risk-policy`
- `POST /api/risk/evaluate-order`
- `POST /api/strategies/{id}/paper-sessions`
- `GET /api/strategies/{id}/paper-sessions`
- `GET /api/paper-sessions/{id}`
- `PATCH /api/paper-sessions/{id}/start`
- `POST /api/paper-sessions/{id}/orders`
- `GET /api/paper-sessions/{id}/orders`
- `GET /api/paper-sessions/{id}/positions`
- `GET /api/paper-sessions/{id}/summary`
- `POST /api/paper-sessions/{id}/replay`
- `PATCH /api/paper-sessions/{id}/stop`
- `GET /api/audit-events`
- `GET /api/dashboard/summary`
- `GET /api/dashboard/strategy-performance`
- `GET /api/dashboard/risk-alerts`
- `GET /api/market-regime?symbol=BTC-USD&timeframe=1h&limit=128`

See [docs/demo-flow.md](docs/demo-flow.md) for a reproducible curl-based walkthrough.
See [docs/architecture.md](docs/architecture.md) for the current service and data-flow diagram.
See [docs/verification.md](docs/verification.md) for the local verification checklist.

## What This Demonstrates

- Spring Boot REST API design with validation and consistent error responses
- PostgreSQL persistence with JPA entities, repositories, and Flyway migrations
- Deterministic backtesting and risk metrics for a constrained SMA strategy
- Service-to-service integration between Spring Boot and FastAPI
- Rule-based ML-style risk scoring with explainable reasons
- CPU-safe market regime classification from recent candle sequences
- React dashboard for the local research workflow, summary metrics, risk alerts, strategy performance, paper trading, audit events, and regime status
- Optional torch artifact provenance, split-aware training manifests, experiment registry entries, and evaluation reports for local market-regime experiments
- Auditability for strategy, import, backtest, ML, risk, and paper-trading actions
- Docker Compose local orchestration for backend, database, ML service, and frontend

## Known Limitations

- No real-money trading, broker integration, custody, trade recommendations, or live order execution; these are permanent product boundaries
- No authentication, users, roles, or account isolation yet
- PyTorch Transformer inference is optional and requires a local artifact plus optional Torch dependencies
- Optional Torch dependencies are excluded from the default setup
- Paper trading is deterministic simulation and manual candle replay only
- The frontend is a local unauthenticated dashboard; user accounts and access control are not implemented

## Current Status

The repository now includes the backend foundation, strategy CRUD, CSV candle import, SMA indicators, deterministic SMA crossover backtesting, append-only audit events, rule-based ML risk scoring through FastAPI, CPU-safe market regime classification from candle sequences, an optional artifact-backed PyTorch market-regime inference path, a baseline risk engine for policy-based simulated order approval, paper-trading sessions with manual orders and candle replay, paper session summaries, dashboard summary APIs, a React dashboard/workbench, and an end-to-end smoke script for a running stack. Backend, ML service, frontend, and smoke-helper tests were verified locally on May 26, 2026; Docker Compose startup remains blocked in the current WSL distro until Docker Desktop WSL integration is available. See `IMPLEMENTATION_PLAN.md` for future phases such as authentication and richer attention-model experiments.
