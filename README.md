# SignalAttention

SignalAttention is a local-first trading strategy research and risk-scoring lab. The MVP focuses on importing historical OHLCV candles, defining an SMA crossover strategy, running deterministic backtests, and requesting a rule-based risk score from a Python FastAPI service.

The project is intentionally risk-focused rather than profit-prediction-focused. It is designed to demonstrate historical testing, simulation, explainability, service integration, and auditability before any live-trading features are considered.

## Planned Stack

- Java 21 and Spring Boot 3 for the backend API
- PostgreSQL 16 for strategy, market data, backtest, trade, and audit persistence
- Python FastAPI for the risk-scoring service
- React and TypeScript for the local dashboard
- Docker Compose for local orchestration
- Swagger/OpenAPI for the initial API UI

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

The frontend reads `VITE_API_BASE_URL`, defaulting to `http://localhost:8080`.

The dashboard includes controls for importing the sample CSV, creating an SMA strategy, running a backtest, scoring ML risk, managing paper sessions, submitting manual paper orders, replaying candles, and reviewing summary/audit/regime panels.

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
- React dashboard for the local research workflow, summary metrics, strategy performance, paper trading, audit events, and regime status
- Auditability for strategy, import, backtest, ML, risk, and paper-trading actions
- Docker Compose local orchestration for backend, database, ML service, and frontend

## Known Limitations

- No real-money trading, broker integration, or live order execution
- No authentication, users, roles, or account isolation yet
- No trained model or PyTorch Transformer inference path yet
- Optional Torch dependencies are excluded from the default setup
- Paper trading is deterministic simulation and manual candle replay only
- The frontend is a local unauthenticated dashboard; user accounts and access control are not implemented

## Current Status

The repository now includes the backend foundation, strategy CRUD, CSV candle import, SMA indicators, deterministic SMA crossover backtesting, append-only audit events, rule-based ML risk scoring through FastAPI, CPU-safe market regime classification from candle sequences, a baseline risk engine for policy-based simulated order approval, paper-trading sessions with manual orders and candle replay, paper session summaries, dashboard summary APIs, and a React dashboard/workbench. Backend, ML service, and frontend checks pass locally; Docker Compose verification still requires Docker Desktop WSL integration in this environment. See `IMPLEMENTATION_PLAN.md` for future phases such as authentication and PyTorch attention-model experiments.
