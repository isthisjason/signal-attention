# SignalAttention Architecture

```mermaid
flowchart LR
    user[Developer / Swagger / curl] --> backend[Spring Boot Backend]
    csv[Sample OHLCV CSV] --> backend

    backend --> strategies[Strategy CRUD]
    backend --> marketdata[Market Data Import]
    backend --> backtests[Backtesting Engine]
    backend --> risk[Risk Policy Engine]
    backend --> paper[Paper Trading Simulation]
    backend --> dashboard[Dashboard Summary API]
    backend --> audit[Audit Events]
    backend --> mlclient[ML Risk Client]

    strategies --> postgres[(PostgreSQL)]
    marketdata --> postgres
    backtests --> postgres
    risk --> postgres
    paper --> postgres
    dashboard --> postgres
    audit --> postgres

    mlclient --> ml[FastAPI ML Service]
    ml --> scorer[Rule-Based Risk Scorer]
```

## Runtime Services

- `postgres`: Stores strategies, candles, backtests, trades, risk policies, paper sessions, positions, orders, and audit events.
- `backend`: Owns REST APIs, validation, persistence, deterministic backtesting, risk evaluation, paper trading, and dashboard aggregation.
- `ml-service`: Provides CPU-first rule-based strategy risk scoring.

## Current Boundaries

- No real-money trading or broker integration.
- No authentication or multi-user model yet.
- No trained attention/PyTorch model yet.
- Dashboard support is backend API only; no frontend is included.
