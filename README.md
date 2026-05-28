# SignalAttention

SignalAttention is a local trading research sandbox I built because I wanted my first applied ML project to feel like a real system, not just a notebook. I have always found trading charts interesting, but I was more interested in the engineering question underneath them: how do you import messy market data, run a repeatable simulation, score risk without pretending to know the future, and leave an audit trail that explains what happened?

The name comes from the paper *Attention Is All You Need*. That paper made sequence modeling feel concrete to me: instead of treating data points as isolated rows, the model attends to relationships across a sequence. SignalAttention is not a production Transformer trading model, but that idea shaped the project. I wanted to build the backend and simulation foundation first, then add attention-inspired market-regime experiments in a way that stayed honest and reproducible.

This is not a trading bot. It does not connect to brokers, place real orders, manage money, or give investment advice. The point is to explore strategy behavior, risk scoring, simulation, and ML-style analysis inside a local app I can run, inspect, and explain.

## What I Built

The project is split into three services:

- `backend` is the Spring Boot API. It owns the database, strategy rules, candle imports, backtesting, paper trading, audit logs, and calls to the ML service.
- `ml-service` is a FastAPI app. I kept it separate because I wanted the project to feel like a real backend talking to a separate analysis service, even though everything runs locally.
- `frontend` is a React dashboard/workbench. It is not trying to be a polished trading terminal. It is mostly there so the demo flow is easier to click through.

The first version was deliberately simple: one strategy type, one sample dataset, deterministic rules, and a local Docker setup. Later work adds risk policies, paper trading, market-regime analysis, anomaly checks, chart feedback, and optional torch experiments, but the default path is still meant to run on a normal CPU.

## Stack

- Java 21 and Spring Boot 3 for the backend API
- PostgreSQL 16 for strategies, market data, backtests, trades, paper sessions, and audit events
- Python FastAPI for the risk and market-regime service
- React and TypeScript for the local dashboard
- Docker Compose for running the whole thing locally
- Swagger/OpenAPI for poking at the backend API

## How the ML Fits

The ML side of this project is intentionally cautious. I did not want to build a black-box price predictor and pretend it could tell someone what to buy. The safer and more useful question is: given a strategy run and recent candles, does this look fragile, risky, unusual, or regime-dependent?

The default ML service answers that with inspectable rules:

- Strategy risk scoring looks at backtest metrics such as drawdown, trade count, volatility, win rate, and profit factor, then returns a score, label, and reasons.
- Market-regime classification looks at recent candle sequences and summarizes trend, volatility, SMA distance, returns, and volume behavior.
- Anomaly checks flag unusual recent candle behavior as research warnings, not trade signals.

The attention idea matters most in the sequence framing. Candle data is temporal: the meaning of one candle depends on what came before it. The optional torch path experiments with that idea by training a small local market-regime model over candle windows, but the project keeps the rule-based path as the default because it is easier to reproduce, explain, and test.

## What you need locally

- Docker Desktop with WSL integration enabled
- Java 21
- Maven 3.9 or compatible Maven 3.x, or the included backend Maven wrapper
- Python 3.12 or compatible Python 3.x
- Node.js 24 or compatible current Node.js runtime

Docker is the easiest way to run the whole thing. The individual tools are still useful when I am working on one service at a time.

## Local URLs

When everything is running, these are the main local URLs:

- Backend API: `http://localhost:8080`
- Frontend dashboard: `http://localhost:5173`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- ML service: `http://localhost:8000`
- PostgreSQL: `localhost:5432`

## Demo flow

The usual demo is pretty small:

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
11. Optionally check recent candles for simple anomaly warnings.

You can do this from Swagger, curl, or the React dashboard. The dashboard is the most comfortable path, but the API routes are still the main thing I wanted to build.

## Useful commands

Backend tests:

```bash
cd backend
./mvnw test
```

ML service tests:

```bash
cd ml-service
python3 -m pip install -r requirements.txt
python3 -m pytest
```

## Optional Torch Experiments

The normal demo does not need PyTorch. I kept the torch path separate because I wanted a place to experiment with sequence models without making the project harder to run.

Install optional torch dependencies only when working on model-backed market-regime experiments:

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

The training command writes the model artifact and a manifest beside it. The evaluation command writes a separate report with accuracy, per-label metrics, a confusion matrix, confidence summary, and sample predictions. When `--experiment-name` is provided, both commands also update `models/experiments/index.json`, which gives me a small local registry of what I trained, how it was evaluated, and where the artifacts live.

The torch path is optional on purpose. The default service still uses the CPU-safe rule classifier because I do not want the normal demo to depend on GPU setup, large dependencies, or a model artifact that only exists on my machine.

Start the full local stack:

```bash
docker compose up --build
```

Run the frontend directly while working on it:

```bash
cd frontend
npm install
npm run dev
```

Frontend tests:

```bash
cd frontend
npm run test
```

Smoke check against a running local stack:

```bash
python3 scripts/smoke_demo.py
```

The smoke script checks that the services are reachable, imports the sample CSV, creates an SMA strategy, runs a backtest, saves an ML risk score, exercises paper trading, and checks dashboard, market-regime, and audit endpoints. It is safe to rerun against an existing local database. Duplicate sample candles are treated as a sign that the data is already loaded.

The frontend reads `VITE_API_BASE_URL`, defaulting to `http://localhost:8080`.

The dashboard includes controls for importing the sample CSV, creating an SMA strategy, running a backtest, scoring ML risk, managing paper sessions, submitting manual paper orders, replaying candles, and reviewing summary, risk alert, audit, and regime panels.

## Main API routes

After the stack starts, I usually use Swagger at `http://localhost:8080/swagger-ui.html`. These are the routes used in the demo:

- `POST /api/market-data/import`
- `GET /api/market-data/candles?symbol=BTC-USD&timeframe=1h`
- `POST /api/strategies`
- `POST /api/strategies/{id}/backtests`
- `GET /api/backtests/{id}`
- `GET /api/backtests/{id}/trades`
- `GET /api/backtests/{id}/metrics`
- `GET /api/backtests/{id}/equity-series`
- `GET /api/backtests/{id}/drawdown-series`
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
- `POST /api/anomaly-check`

See [docs/demo-flow.md](docs/demo-flow.md) for a reproducible curl-based walkthrough.
See [docs/architecture.md](docs/architecture.md) for the current service and data-flow diagram.
See [docs/verification.md](docs/verification.md) for the local verification checklist.

## What I was trying to practice

The main goal was to build something that touches a few real backend problems without pretending to be a real trading platform.

- Spring Boot REST APIs with validation and consistent error responses
- PostgreSQL persistence with JPA repositories and Flyway migrations
- A deterministic backtest flow that is boring in a good way
- A backend calling a separate FastAPI service
- Rule-based risk scoring with reasons instead of a mystery number
- Audit events for important actions
- A small React workbench that can drive the demo without copying curl commands all day
- Optional torch experiments without making the default setup painful

The ML part is intentionally conservative. The default risk score and market regime classifier are rules-based because that is easier to inspect and much easier to demo honestly. The torch path is there for experiments, but it is not required for the normal app.

## What is not included

This does not place real trades. That is intentional. I want the project to be useful as a simulator I can reason about, not something that can accidentally drift into real-money execution.

- No broker or exchange integration
- No custody, payments, or real-money order routing
- No trade recommendations or copy-trading behavior
- No authentication, users, roles, or account isolation yet
- No background live trading scheduler
- No custom user-submitted strategy code

Paper trading here means simulated orders and manual candle replay. It is useful for testing the shape of the workflow, charting assessments, and audit behavior, but it is still fake trading.

## Current status

The repo currently has the backend foundation, strategy CRUD, CSV candle import, SMA indicators, backtesting, equity and drawdown chart data, audit events, rule-based ML risk scoring, CPU-safe market regime classification, optional torch-backed regime inference, a simple anomaly check, baseline risk policies, paper-trading sessions, dashboard summary APIs, candlestick assessment feedback in the React workbench, and a smoke script for the running stack.

Backend, ML service, frontend, frontend build, smoke-helper tests, Compose config, Compose startup, and the running-stack smoke demo were verified locally on May 27, 2026.

The next work I would do is mostly polish and research depth: verify the full Compose demo in a Docker-enabled environment, keep improving the optional market-regime experiment tracking, and make the dashboard charts easier to inspect.
