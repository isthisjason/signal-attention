# SignalAttention

SignalAttention is a local-first trading strategy research and risk-scoring lab. The MVP focuses on importing historical OHLCV candles, defining an SMA crossover strategy, running deterministic backtests, and requesting a rule-based risk score from a Python FastAPI service.

The project is intentionally risk-focused rather than profit-prediction-focused. It is designed to demonstrate historical testing, simulation, explainability, service integration, and auditability before any live-trading features are considered.

## Planned Stack

- Java 21 and Spring Boot 3 for the backend API
- PostgreSQL 16 for strategy, market data, backtest, trade, and audit persistence
- Python FastAPI for the risk-scoring service
- Docker Compose for local orchestration
- Swagger/OpenAPI for the initial API UI

## Local Prerequisites

- Docker Desktop with WSL integration enabled
- Java 21
- Maven 3.9 or compatible Maven 3.x
- Python 3.12 or compatible Python 3.x

## Local Services

Planned local URLs:

- Backend API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- ML service: `http://localhost:8000`
- PostgreSQL: `localhost:5432`

## Demo Flow

The backend MVP demo flow is:

1. Start the stack with `docker compose up --build`.
2. Import sample BTC-USD candle data.
3. Create an SMA crossover strategy.
4. Run a backtest.
5. Review generated trades and metrics.
6. Request an ML risk score.
7. Confirm the risk score is persisted and audit events were recorded.
8. Optionally configure a risk policy and evaluate a simulated order.

## Useful Local Commands

Run backend tests:

```bash
cd backend
mvn test
```

Run ML service tests:

```bash
cd ml-service
python3 -m pip install -r requirements.txt
python3 -m pytest
```

Start the full local stack:

```bash
docker compose up --build
```

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
- `PATCH /api/paper-sessions/{id}/start`
- `POST /api/paper-sessions/{id}/orders`
- `GET /api/paper-sessions/{id}/orders`
- `GET /api/paper-sessions/{id}/positions`
- `PATCH /api/paper-sessions/{id}/stop`

See [docs/demo-flow.md](docs/demo-flow.md) for a reproducible curl-based walkthrough.

## Current Status

The repository now includes the backend foundation, strategy CRUD, CSV candle import, SMA indicators, deterministic SMA crossover backtesting, append-only audit events, rule-based ML risk scoring through FastAPI, a baseline risk engine for policy-based simulated order approval, and a paper-trading simulation foundation. See `IMPLEMENTATION_PLAN.md` for future phases such as a dashboard and attention-model experiments.
