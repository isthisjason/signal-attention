# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SignalAttention is a local trading research sandbox with three services:
- **Backend**: Spring Boot 3 / Java 21 REST API for strategy management, backtesting, risk scoring
- **ML Service**: FastAPI Python service for rule-based risk analysis, market regime classification, anomaly detection
- **Frontend**: React 19 / TypeScript dashboard for local workbench

The project is intentionally **research-focused, not production trading**. No real-money execution, broker integration, custody, or investment advice.

## Development Environment

### Prerequisites
- Java 21
- Maven 3.9+ (or use backend `./mvnw`)
- Python 3.12
- Node.js 24 (current LTS)
- Docker Desktop with WSL integration

### Local Service URLs
- Backend API: `http://localhost:8080`
- Frontend dashboard: `http://localhost:5173`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- ML service: `http://localhost:8000`
- PostgreSQL: `localhost:5432`

## Build, Test, and Development Commands

### Full Stack (Docker Compose)

```bash
# Start all services
docker compose up --build

# Start with optional PyTorch-enabled ML service on port 8001
docker compose --profile torch up --build
```

### Backend (Java / Spring Boot)

```bash
cd backend

# Run tests
./mvnw test

# Run a single test
./mvnw test -Dtest=ClassName#methodName

# Build locally (no Docker)
./mvnw clean package

# Run locally (requires PostgreSQL running)
./mvnw spring-boot:run
```

### ML Service (Python / FastAPI)

```bash
cd ml-service

# Install dependencies
python3 -m pip install -r requirements.txt

# Run tests
python3 -m pytest

# Run with verbose output
python3 -m pytest -v

# Run a specific test
python3 -m pytest tests/test_filename.py::test_function

# Start service locally
python3 -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload

# Optional: Install PyTorch for local experiments
python3 -m pip install -r requirements-torch.txt
```

### Frontend (React / TypeScript)

```bash
cd frontend

# Install dependencies
npm install

# Development server
npm run dev

# Build for production
npm run build

# Run tests
npm run test

# Preview production build
npm run preview
```

### Smoke Test

```bash
# Run end-to-end smoke test against running local stack
python3 scripts/smoke_demo.py

# This checks: service reachability, CSV import, strategy creation,
# backtesting, ML scoring, paper trading, and dashboard endpoints
```

## Architecture Highlights

### Backend Structure
```
backend/
├── src/main/java/com/signalattention/
│   ├── strategies/         # Strategy CRUD, JSON rules
│   ├── marketdata/         # CSV import, candle storage
│   ├── backtesting/        # SMA crossover, trade simulation, metrics
│   ├── indicators/         # SMA, future indicators
│   ├── risk/               # Risk policies, order evaluation
│   ├── papertrading/       # Paper sessions, simulated orders
│   ├── ml/                 # HTTP client for Python ML service
│   ├── dashboard/          # Summary APIs, risk alerts
│   ├── audit/              # Append-only event logging
│   └── common/             # Exceptions, utilities, validation
├── src/test/              # JUnit 5, Mockito, Testcontainers
└── pom.xml               # Maven dependencies, Spring Boot 3, JPA, PostgreSQL
```

### ML Service Structure
```
ml-service/
├── app/
│   ├── main.py            # FastAPI app entry
│   ├── routes/            # Endpoints (risk, regime, anomaly)
│   └── services/          # Feature engineering, rule-based scoring
├── scripts/               # Training/evaluation for optional torch models
├── models/                # Torch artifacts and experiment registry
└── requirements.txt       # pandas, numpy, scikit-learn, FastAPI
```

### Frontend Structure
```
frontend/
├── src/
│   ├── components/        # React components
│   ├── pages/            # Page-level components
│   ├── hooks/            # Custom React hooks
│   └── api/              # API client for backend
├── vite.config.ts        # Vite configuration
└── package.json          # React 19, TypeScript, Vitest
```

### Data Flow
CSV → Backend (import) → PostgreSQL (candles) → Backtesting Engine → Metrics → ML Service (risk score) → Dashboard + Audit Events

## Key Concepts

### Backtesting
- **Strategy**: JSON-based rules (SMA crossover with configurable windows)
- **Trades**: Long-only, one position at a time, execution at candle close
- **Metrics**: totalReturn, maxDrawdown, winRate, profitFactor, tradeCount, volatility, averageTradeReturn, feeDrag
- **Deterministic**: Same inputs always produce same output for reproducible testing

### Risk Scoring
- **Rule-based**: Thresholds on backtest metrics (drawdown, trade count, win rate, profit factor, volatility)
- **Labels**: LOW_RISK, MEDIUM_RISK, HIGH_RISK, LIKELY_OVERFIT
- **Reasons**: Returned with score for transparency
- **ML Service**: Stateless, CPU-first endpoint; optional torch inference for market regime

### Market Regime (Optional PyTorch)
- **Default mode** (`rules`): Deterministic feature extraction, rule-based labels, CPU-only
- **Torch mode** (`torch`): Optional Transformer model inference from artifact, requires dependencies
- **Training**: Local scripts for experimental model creation and evaluation
- **Experiment tracking**: Manifest files alongside artifacts, experiment index registry

### Paper Trading
- **Simulated orders**: Manual order submission to simulated positions
- **Replay**: Manual candle replay for session progression
- **Positions tracking**: Current holdings, realized/unrealized P&L
- **Audit trail**: All orders and positions logged

## Testing Strategy

### Backend Tests
- **Unit**: SMA calculation, crossover signals, metrics, CSV validation
- **Integration**: Strategy CRUD, market data import, backtest execution (using Testcontainers PostgreSQL)
- **Command**: `cd backend && ./mvnw test`

### ML Service Tests
- **Health endpoint**: Service availability
- **Risk scoring**: Each risk label branch, validation errors
- **Market regime**: Rule-based classification, optional torch inference
- **Command**: `cd ml-service && python3 -m pytest`

### Frontend Tests
- **Component tests**: React components with React Testing Library
- **Integration**: API client, form submissions
- **Command**: `cd frontend && npm run test`

### Smoke Test
- **Full flow**: CSV import → strategy creation → backtest → ML scoring → paper trading → audit
- **Command**: `python3 scripts/smoke_demo.py`

## Database

### Key Tables
- **strategies**: Strategy CRUD (JSON rules)
- **market_candles**: OHLCV data (indexed by symbol, timeframe, timestamp)
- **backtest_runs**: Backtest execution results (metrics, risk score)
- **backtest_trades**: Simulated trades from backtests
- **risk_policies**: Configurable risk controls per strategy
- **paper_sessions**: Simulated trading sessions
- **paper_orders**: Orders within sessions
- **audit_events**: Append-only log of important actions

### Migrations
- Flyway handles schema versioning
- Migrations in `backend/src/main/resources/db/migration/`

## Configuration

### Environment Variables (docker-compose.yml / .env)
```bash
# PostgreSQL
POSTGRES_DB=signalattention
POSTGRES_USER=signalattention
POSTGRES_PASSWORD=signalattention

# Backend
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/signalattention

# ML Service
ML_SERVICE_PORT=8000
MARKET_REGIME_MODE=rules                    # rules | torch
MARKET_REGIME_ARTIFACT_PATH=                # Path to .pt file if torch mode

# Frontend
VITE_API_BASE_URL=http://localhost:8080
```

## Optional PyTorch Experiments

Default setup is CPU-safe (rule-based). PyTorch integration is optional:

```bash
cd ml-service

# Install optional torch dependencies
python3 -m pip install -r requirements-torch.txt

# Train a market-regime model locally (seeded, mini batch, early stopping)
python scripts/train_market_regime_model.py \
  --csv-path ../data/btc-usd-1h-sample.csv \
  --output models/market-regime.pt \
  --cpu --seed 42 --batch-size 32 --patience 10

# Evaluate the trained model against a majority class baseline
python scripts/evaluate_market_regime_model.py \
  --csv-path ../data/btc-usd-1h-sample.csv \
  --artifact models/market-regime.pt \
  --output models/market-regime-evaluation.json \
  --holdout-ratio 0.2

# Compare recorded runs
python scripts/compare_market_regime_experiments.py \
  --experiments-dir models/experiments

# Run torch-enabled ML service in Docker
docker compose --profile torch up --build ml-service-torch
```

Useful training flags: `--seed` for reproducible CPU runs, `--batch-size` and `--patience` for the mini batch loop and early stopping, and `--dropout` and `--no-positional-encoding` to adjust the model. Evaluation accepts `--holdout-ratio` to score only the later unseen tail. Torch artifacts are loaded at startup if `MARKET_REGIME_ARTIFACT_PATH` is set. Training and evaluation write a manifest and append a run to `models/experiments/index.json`, keeping every run under its own run id rather than overwriting earlier ones.

## Common Workflows

### Import Sample Data and Run Demo
1. Start stack: `docker compose up --build`
2. Run smoke test: `python3 scripts/smoke_demo.py`
3. Or manually via Swagger at `http://localhost:8080/swagger-ui.html`:
   - `POST /api/market-data/import` with `data/btc-usd-1h-sample.csv`
   - `POST /api/strategies` to create SMA strategy
   - `POST /api/strategies/{id}/backtests` to run backtest
   - `POST /api/backtests/{id}/ml-risk-score` to score risk

### Develop Backend in Isolation
```bash
# Terminal 1: Start PostgreSQL via Docker
docker compose up postgres

# Terminal 2: Start backend locally
cd backend
./mvnw spring-boot:run

# Backend will be at http://localhost:8080
```

### Develop ML Service in Isolation
```bash
# Terminal 1: Start PostgreSQL via Docker
docker compose up postgres

# Terminal 2: Start ML service locally
cd ml-service
python3 -m pip install -r requirements.txt
python3 -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload

# Service will be at http://localhost:8000
```

### Develop Frontend in Isolation
```bash
# Terminal 1: Full stack (backend, ML, DB)
docker compose up --build

# Terminal 2: Frontend dev server
cd frontend
npm install
npm run dev

# Frontend will be at http://localhost:5173, proxying to backend
```

### Run Tests Before Committing
```bash
# Backend
cd backend && ./mvnw test

# ML Service
cd ml-service && python3 -m pytest

# Frontend
cd frontend && npm run test

# Full smoke test (requires stack running)
python3 scripts/smoke_demo.py
```

## Boundaries and Exclusions

### Permanent Out of Scope
- Real-money trading, broker/exchange integration, custody, order routing
- Investment advice, trade recommendations, copy-trading
- Live trading execution, automated strategies
- Custom user-submitted strategy code

### Current Limitations
- No authentication or multi-user model
- No cloud deployment or Kubernetes
- Dashboard is local, unauthenticated

## Verification Checklist

See `docs/verification.md` for a local verification checklist after changes.

## Documentation

- **README.md**: Project overview, setup, architecture, demo flow, API routes
- **docs/architecture.md**: Service diagram, runtime responsibilities, market regime modes
- **docs/demo-flow.md**: Curl-based walkthrough of full workflow
- **docs/verification.md**: Local checklist for testing completeness
- **IMPLEMENTATION_PLAN.md**: Original MVP spec and future roadmap

## Common Issues

### Services Not Reachable
- Check `docker compose ps` to see if containers are running
- Check logs: `docker compose logs backend`, `docker compose logs ml-service`, etc.
- Verify PostgreSQL is ready: `docker compose logs postgres`

### Database Connection Failed
- Ensure `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB` match across services
- Wait for PostgreSQL health check to pass before starting backend
- Use Testcontainers for isolated backend tests

### ML Service Unavailable
- Backend is resilient if ML service is down; risk scoring will fail gracefully
- Check ML service logs: `docker compose logs ml-service`
- Verify Python dependencies installed: `pip install -r requirements.txt`

### Frontend Can't Connect to Backend
- Verify backend is running at `localhost:8080`
- Check `VITE_API_BASE_URL` environment variable (default `http://localhost:8080`)
- Check browser console for CORS or network errors

### PyTorch Optional Dependencies
- Only needed for torch-mode market regime inference
- Default (rules mode) runs on CPU without PyTorch
- If torch-mode activation fails, check `requirements-torch.txt` installation
