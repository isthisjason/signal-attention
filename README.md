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
- Python 3.12 or compatible Python 3.x

## Local Services

Planned local URLs:

- Backend API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- ML service: `http://localhost:8000`
- PostgreSQL: `localhost:5432`

## Demo Flow

The MVP demo flow will be:

1. Start the stack with `docker compose up --build`.
2. Import sample BTC-USD candle data.
3. Create an SMA crossover strategy.
4. Run a backtest.
5. Review generated trades and metrics.
6. Request an ML risk score.
7. Confirm the risk score is persisted and audit events were recorded.

## Current Status

This repository is in the initial scaffold stage. See `IMPLEMENTATION_PLAN.md` for the full MVP roadmap.
