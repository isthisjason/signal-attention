# SignalAttention Backend MVP Implementation Plan

## 1. Project Goal

SignalAttention is a local-first trading strategy research and risk-scoring lab. The MVP should let a user import historical OHLCV candle data, define a simple SMA crossover strategy, run a deterministic backtest, review trades and metrics, and request a rule-based ML risk score from a Python FastAPI service.

The first milestone is intentionally practical and portfolio-focused. It should demonstrate backend engineering, data modeling, service integration, Docker-based local development, testing, and clear documentation.

The MVP is **not** intended to provide live trading, broker integration, real-money execution, high-frequency trading, a frontend dashboard, or an advanced Transformer/attention model. Live trading, broker integration, real-money execution, account custody, and trade recommendations are permanent exclusions; the frontend and attention-model work are later research/dashboard enhancements after the baseline system is stable.

---

## 2. Original Pitch and Positioning

**One-sentence pitch:** SignalAttention lets users create, backtest, simulate, and risk-score trading strategies using attention-inspired market analysis without connecting to brokers or placing real orders.

SignalAttention should be presented as a research and risk-management simulator, not as a price-prediction, profit-guarantee, investment-advice, or trade-execution tool. The product story is strongest when it emphasizes non-custodial workflows: historical testing, simulation, risk scoring, regime awareness, explainability, and audit trails.

The MVP is intentionally backend-first. The baseline now includes risk-policy and paper-trading simulation APIs, while authentication, a frontend dashboard, and attention-based modeling remain later phases. Live trading and broker integration are intentionally not on the roadmap.

---

## 3. Goals and Non-Goals

| Goals | Non-Goals |
| --- | --- |
| Backtest strategies on historical candle data. | Do not promise profitable trading or investment outcomes. |
| Score strategies for risk, overfitting, and fragility. | Do not integrate with brokers, exchanges, custody, or real-money execution. |
| Simulate paper trading in a later phase with audit logs. | Do not attempt high-frequency trading. |
| Use ML to classify regimes and trade quality over time. | Do not rely on black-box price prediction as the product. |
| Run locally with minimal cost and reproducible setup. | Do not begin with AWS, Kubernetes, or complex cloud infrastructure. |

---

## 4. MVP Success Criteria

The MVP is complete when all of the following are true:

- `docker compose up` starts PostgreSQL, the Spring Boot backend, and the Python ML service.
- Backend Swagger/OpenAPI docs are available locally.
- A sample OHLCV CSV file can be imported into PostgreSQL.
- A user can create, list, update, fetch, and delete an SMA crossover strategy.
- A user can run a backtest against imported candle data.
- Backtest results include trades and core performance/risk metrics.
- The backend can call the ML service for a rule-based strategy risk score.
- The ML risk score and label are persisted on the backtest run.
- Important actions create append-only audit events.
- The README explains setup, architecture, and a reproducible demo flow.

---

## 5. Repository Setup

Create the following top-level structure:

```text
signalattention/
├── backend/
├── ml-service/
├── data/
├── docs/
├── docker-compose.yml
├── .env.example
├── README.md
└── IMPLEMENTATION_PLAN.md
```

### Root-Level Responsibilities

- `backend/`: Java 21 Spring Boot 3 application.
- `ml-service/`: Python FastAPI application for MVP risk scoring.
- `data/`: sample market candle CSV files.
- `docs/`: architecture notes, API examples, and future design notes.
- `docker-compose.yml`: local orchestration for PostgreSQL, backend, and ML service.
- `.env.example`: documented local environment variables.
- `README.md`: portfolio-ready setup and demo documentation.

### Default Technical Choices

- Java: 21
- Backend framework: Spring Boot 3
- Backend build tool: Maven
- Database: PostgreSQL 16
- ML service: Python FastAPI
- Local orchestration: Docker Compose
- Frontend: excluded from MVP
- Authentication/JWT: excluded from MVP

---


## 6. Complete Technology Stack Reference

This section preserves the broader technology plan from the original project document. Not every item is required in the first MVP, but these choices define the intended direction.

### Java / Spring Boot Backend

| Purpose | Technology | Notes |
| --- | --- | --- |
| Language | Java 21 | Modern LTS Java version suitable for Spring Boot 3. |
| Backend framework | Spring Boot 3 | Main API, business logic, services, controllers, and scheduling. |
| Web API | Spring Web | REST endpoints for strategies, backtests, ML calls, and later paper trading. |
| Security | Spring Security + JWT | Future user login and protected endpoints; excluded from MVP. |
| Database access | Spring Data JPA / Hibernate | Entities, repositories, and persistence. |
| Validation | Jakarta Bean Validation | Request and payload validation. |
| Scheduling | Spring Scheduler | Future paper-trading ticks, background checks, and scheduled tasks. |
| API documentation | Swagger / OpenAPI | Primary MVP testing and documentation UI. |
| Testing | JUnit 5, Mockito, Testcontainers | Unit, service, repository, and integration testing. |
| Build tool | Maven | Chosen default for simpler Spring Boot setup. |

### Python ML Service

| Purpose | Technology | Notes |
| --- | --- | --- |
| ML API | Python FastAPI | Prediction endpoints called by Spring Boot. |
| Data processing | pandas, NumPy | Feature engineering and model input preparation. |
| Baseline ML | scikit-learn | Random Forest, Logistic Regression, Isolation Forest, and clustering later. |
| Attention model | PyTorch optional | Future lightweight Transformer encoder for sequence modeling; NVIDIA GPU acceleration is optional and not part of the baseline MVP. |
| Model storage | joblib, pickle, `.pt` files | Store scikit-learn and PyTorch model artifacts locally. |
| Experiments | Jupyter Notebook | Train and evaluate models before productionizing; GPU can be used locally for later PyTorch experiments. |
| Testing | pytest | Test prediction routes and feature engineering functions. |

### Data, Infrastructure, and Tools

| Purpose | Technology | Notes |
| --- | --- | --- |
| Database | PostgreSQL | Primary store for strategies, candles, backtests, trades, and audit events. |
| Cache/queue | Redis optional | Skip initially; consider for paper-trading sessions or background coordination. |
| Local orchestration | Docker Compose | Run PostgreSQL, ML service, backend, and optional frontend locally. |
| Optional GPU runtime | NVIDIA Container Toolkit / Docker GPU support | Future-only path for CUDA-backed PyTorch training or inference; default Compose stack must remain CPU-compatible. |
| Frontend | React + TypeScript optional | Add only after API is stable. |
| Charts | Recharts or Plotly optional | Future equity curve, drawdown, and regime visualizations. |
| API testing | Postman / Insomnia | Manual testing alongside Swagger. |
| Database GUI | DBeaver optional | Inspect tables and imported market data. |
| Version control | Git + GitHub | Portfolio hosting and documentation. |

---

## 7. Backend Foundation

Generate a Spring Boot application under `backend/` using Maven.

### Required Dependencies

Include these backend dependencies:

- Spring Web
- Spring Data JPA
- PostgreSQL Driver
- Jakarta Bean Validation
- Springdoc OpenAPI / Swagger UI
- JUnit 5
- Mockito
- Testcontainers PostgreSQL

Optional but acceptable:

- Lombok, if consistently used and documented.

### Backend Package Structure

Use base package:

```text
com.signalattention
```

Create these modules/packages:

```text
com.signalattention.strategies
com.signalattention.marketdata
com.signalattention.indicators
com.signalattention.backtesting
com.signalattention.ml
com.signalattention.audit
com.signalattention.common
```

### Backend Configuration

Configure:

- PostgreSQL datasource.
- JPA/Hibernate settings.
- OpenAPI/Swagger metadata.
- ML service base URL.
- Local development profile.
- Consistent API error responses.

### Suggested Local URLs

- Backend API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html` or Springdoc equivalent
- ML service: `http://localhost:8000`
- PostgreSQL: `localhost:5432`

---

## 8. Database Model

Use PostgreSQL as the source of truth.

### `Strategy`

Purpose: stores user-defined trading strategy configuration.

Suggested fields:

- `id`
- `name`
- `symbol`
- `timeframe`
- `strategyType`
- `rulesJson`
- `status`
- `createdAt`
- `updatedAt`

MVP strategy type:

- `SMA_CROSSOVER`

Suggested statuses:

- `ACTIVE`
- `DISABLED`

### `MarketCandle`

Purpose: stores imported OHLCV market data.

Suggested fields:

- `id`
- `symbol`
- `timeframe`
- `openTime`
- `open`
- `high`
- `low`
- `close`
- `volume`

Required database behavior:

- Index by `symbol`, `timeframe`, and `openTime`.
- Prevent duplicate candles for the same `symbol`, `timeframe`, and `openTime`.

### `BacktestRun`

Purpose: stores one completed or failed backtest execution.

Suggested fields:

- `id`
- `strategyId`
- `startDate`
- `endDate`
- `initialBalance`
- `finalBalance`
- `totalReturn`
- `maxDrawdown`
- `winRate`
- `profitFactor`
- `tradeCount`
- `averageTradeReturn`
- `feeDrag`
- `volatility`
- `mlRiskScore`
- `mlRiskLabel`
- `status`
- `createdAt`
- `completedAt`

Suggested statuses:

- `COMPLETED`
- `FAILED`

### `BacktestTrade`

Purpose: stores trades generated by a backtest.

Suggested fields:

- `id`
- `backtestRunId`
- `side`
- `entryTime`
- `entryPrice`
- `exitTime`
- `exitPrice`
- `quantity`
- `grossPnl`
- `fees`
- `netPnl`
- `returnPercent`

MVP side:

- `LONG`

### `AuditEvent`

Purpose: append-only event log for important system actions.

Suggested fields:

- `id`
- `entityType`
- `entityId`
- `action`
- `message`
- `metadataJson`
- `createdAt`

Audit events should not be updated after creation.

---

## 9. Strategy Management

Implement CRUD support for strategies.

### Endpoints

```text
POST   /api/strategies
GET    /api/strategies
GET    /api/strategies/{id}
PUT    /api/strategies/{id}
DELETE /api/strategies/{id}
```

### Strategy Request Requirements

A strategy create/update request should include:

- `name`
- `symbol`
- `timeframe`
- `strategyType`
- `rules`

For `SMA_CROSSOVER`, `rules` should include:

- `shortWindow`
- `longWindow`
- `initialBalance`
- `feePercent`
- `positionSizePercent`

### Strategy Validation

Validate:

- Name is present.
- Symbol is present.
- Timeframe is present.
- Strategy type is supported.
- Short window is positive.
- Long window is positive.
- Short window is less than long window.
- Initial balance is positive.
- Fee percentage is zero or positive.
- Position size percentage is greater than zero and less than or equal to 100.

### Audit Events

Create audit events for:

- Strategy created.
- Strategy updated.
- Strategy deleted.

---

## 10. Market Data Import

Implement CSV-based OHLCV import.

### Endpoints

```text
POST /api/market-data/import
GET  /api/market-data/candles?symbol=BTC-USD&timeframe=1h
```

### CSV Columns

The MVP CSV format should use these columns:

```text
symbol,timeframe,openTime,open,high,low,close,volume
```

### Import Behavior

The import endpoint should:

- Accept a CSV file upload.
- Parse all rows.
- Validate required fields.
- Validate numeric OHLCV values.
- Validate timestamps.
- Reject or report duplicate candle rows.
- Store valid candles in PostgreSQL.
- Return an import summary.

### Import Summary Response

Return at least:

- Total rows read.
- Rows imported.
- Rows skipped or rejected.
- Error messages for invalid rows.

### Candle Query Behavior

The candle query endpoint should:

- Require `symbol` and `timeframe`.
- Optionally support start/end time filters.
- Return candles sorted by `openTime` ascending.

### Audit Events

Create audit events for:

- CSV import completed.
- CSV import failed.

---

## 11. Indicators

Implement indicator logic separately from controllers and persistence code.

### MVP Indicator

Implement:

- Simple Moving Average, `SMA`.

### SMA Behavior

The SMA calculator should:

- Accept ordered close prices or candles.
- Accept a positive integer window.
- Return no SMA value until enough data exists.
- Produce deterministic decimal output.

### Indicator Tests

Add unit tests for:

- Normal SMA calculation.
- Insufficient history.
- Invalid zero or negative windows.
- Deterministic sample data.

---

## 12. Backtesting

Implement the SMA crossover backtesting flow.

### Endpoints

```text
POST /api/strategies/{id}/backtests
GET  /api/backtests/{id}
GET  /api/backtests/{id}/trades
GET  /api/backtests/{id}/metrics
```

### Backtest Request

A backtest request should include:

- `startDate`
- `endDate`

Optional overrides may include:

- `initialBalance`
- `feePercent`
- `positionSizePercent`

If optional overrides are absent, use the strategy rules.

### Backtest Execution Behavior

The backtest engine should:

1. Load the strategy.
2. Load candles for the strategy symbol and timeframe within the requested date range.
3. Sort candles by `openTime` ascending.
4. Calculate short and long SMA values.
5. Detect crossover events.
6. Enter a long position when short SMA crosses above long SMA.
7. Exit the long position when short SMA crosses below long SMA.
8. Apply fees on entry and exit.
9. Track cash balance, open position, closed trades, and equity.
10. Persist the backtest run and trades.
11. Calculate and store metrics.
12. Create audit events.

### MVP Trading Assumptions

Use these assumptions for the first implementation:

- Long-only strategy.
- One open position at a time.
- No leverage.
- No short selling.
- No slippage in MVP.
- No stop-loss or take-profit in MVP.
- Fees are percentage-based.
- Trades execute at candle close price.

### Metrics

Calculate:

- `totalReturn`
- `maxDrawdown`
- `winRate`
- `profitFactor`
- `tradeCount`
- `averageTradeReturn`
- `feeDrag`
- `volatility`

### Backtest Failure Cases

Handle gracefully:

- Strategy not found.
- Unsupported strategy type.
- No candles found.
- Not enough candles for the long SMA window.
- Invalid date range.
- Invalid strategy rules.

### Audit Events

Create audit events for:

- Backtest completed.
- Backtest failed.

---

## 13. Python ML Service

Create a Python FastAPI app under `ml-service/`.

### Required Endpoints

```text
GET  /health
POST /predict/strategy-risk
```

### MVP Risk Scoring

The MVP risk score should be rule-based. Do not train a real model yet.

The MVP ML service should remain CPU-first. NVIDIA GPU support is useful for later PyTorch training or heavier inference, but the first risk-scoring endpoint must not require CUDA, GPU drivers, or GPU-enabled Docker to run.

### Risk Prediction Input

Accept backtest metrics such as:

- `totalReturn`
- `maxDrawdown`
- `winRate`
- `profitFactor`
- `tradeCount`
- `averageTradeReturn`
- `feeDrag`
- `volatility`

### Risk Prediction Output

Return:

- `riskScore`
- `riskLabel`
- `reasons`

Supported labels:

- `LOW_RISK`
- `MEDIUM_RISK`
- `HIGH_RISK`
- `LIKELY_OVERFIT`

### Example Rule Intent

The exact thresholds can be refined during implementation, but the MVP should roughly follow this intent:

- High drawdown increases risk.
- Very low trade count increases overfitting risk.
- Very high return with very low trade count may be likely overfit.
- Low profit factor increases risk.
- High volatility increases risk.
- Strong metrics across enough trades reduce risk.

### ML Service Tests

Add tests for:

- Health endpoint.
- Optional GPU health endpoint in future phases, only when CUDA-backed models are introduced.
- Valid prediction request.
- Invalid prediction request.
- Each risk label branch.

### Optional Future GPU Path

When the project moves beyond rule-based scoring into PyTorch sequence models:

- Add a separate GPU-enabled Docker profile or service, rather than changing the default `ml-service`.
- Pin compatible CUDA, PyTorch, and Python versions.
- Add a `GET /health/gpu` endpoint that reports CUDA availability and device name.
- Keep CPU fallback behavior for rule-based and scikit-learn scoring.
- Document local verification with `nvidia-smi` from inside a container.

---


## 14. Long-Term Architecture Notes

These notes preserve the broader architecture from the PDF while keeping the MVP focused.

### Future Backend Modules

| Module | Responsibility | MVP Status |
| --- | --- | --- |
| `auth` | Registration, login, JWT creation, security configuration. | Future |
| `users` | User entity, roles, user repository, current-user lookup. | Future |
| `strategies` | Strategy CRUD, rules, JSON strategy configuration. | MVP |
| `marketdata` | CSV imports, candle storage, market data lookup. | MVP |
| `indicators` | SMA, EMA, RSI, MACD, volatility, volume z-score calculators. | SMA only in MVP |
| `backtesting` | Trade simulation and metrics calculation. | MVP |
| `risk` | Risk policies, order approval/rejection, position sizing. | Implemented baseline |
| `ml` | HTTP client and DTOs for Python predictions. | MVP baseline |
| `papertrading` | Sessions, simulated orders, positions, P&L tracking, manual candle replay. | Implemented baseline |
| `audit` | Append-only events for important decisions. | MVP |
| `dashboard` | Aggregated endpoints for portfolio/demo summary views. | Implemented baseline API |
| `common` | Exceptions, error responses, utilities, validation. | MVP |

### Future Python ML Service Structure

```text
ml-service/app
├── main.py
├── routes/
│   ├── strategy_risk.py
│   ├── market_regime.py
│   ├── anomaly.py
│   └── transformer.py
├── services/
│   ├── feature_engineering.py
│   ├── prediction_service.py
│   ├── training_service.py
│   ├── sequence_builder.py
│   └── transformer_predictor.py
├── schemas/
│   ├── strategy_risk_schema.py
│   ├── regime_schema.py
│   └── anomaly_schema.py
└── models/
    ├── strategy_risk_model.joblib
    ├── regime_model.joblib
    └── market_transformer.pt
```

### Long-Term Data Model Additions

Future entities from the original plan:

- `User`: `id`, `email`, `passwordHash`, `role`, `createdAt`.
- `RiskPolicy`: `strategyId`, max daily loss, max position size, stop loss, take profit, cooldown.
- `PaperTradingSession`: strategy, balances, status, start/stop timestamps.
- `PaperOrder`: session, symbol, side, quantity, simulated price, status, reason.

---

## 15. Backend ML Client

Add a typed backend client for the FastAPI service.

### Backend Endpoint

```text
POST /api/backtests/{id}/ml-risk-score
```

### Behavior

The endpoint should:

1. Load the backtest run.
2. Build an ML request from stored metrics.
3. Call `POST /predict/strategy-risk` on the ML service.
4. Store `riskScore` and `riskLabel` on the backtest run.
5. Return the ML prediction response to the caller.
6. Create audit events for success or failure.

### Failure Handling

If the ML service is unavailable:

- Return a clear API error.
- Do not corrupt the existing backtest run.
- Create an audit event for the failure.

---

## 16. Audit Logging

Audit logging should be lightweight but consistent.

### Required Audit Actions

Record audit events for:

- Strategy creation.
- Strategy update.
- Strategy deletion.
- CSV import completion.
- CSV import failure.
- Backtest completion.
- Backtest failure.
- ML risk score request completion.
- ML risk score request failure.

### Audit Metadata

Use JSON metadata for details such as:

- Strategy ID.
- Backtest ID.
- Symbol.
- Timeframe.
- Imported row counts.
- Error summaries.
- Risk label.

---

## 17. Docker Compose

Create local Docker Compose orchestration.

### Services

Required services:

- `postgres`
- `backend`
- `ml-service`

### PostgreSQL Defaults

Use these local defaults:

```text
POSTGRES_DB=signalattention
POSTGRES_USER=signalattention
POSTGRES_PASSWORD=signalattention
```

### Ports

Expose:

- PostgreSQL: `5432:5432`
- Backend: `8080:8080`
- ML service: `8000:8000`

### Startup Order

The backend should depend on:

- `postgres`
- `ml-service`

The backend should be resilient if the ML service is temporarily unavailable.

### Optional GPU Compose Profile

Do not make the default local stack require GPU support. CUDA-backed PyTorch work must remain behind an optional Compose profile such as:

```bash
docker compose --profile gpu up --build
```

The GPU profile should:

- Use a CUDA/PyTorch-compatible ML service image.
- Request NVIDIA GPU devices through Docker Compose.
- Keep `postgres` and `backend` unchanged where possible.
- Fall back to the CPU `ml-service` for MVP rule-based scoring.

---

## 18. Documentation

Create a portfolio-ready README.

### README Sections

Include:

- Project description.
- Why the project is risk-focused rather than profit-prediction-focused.
- Architecture overview.
- Tech stack.
- Local setup.
- Docker Compose commands.
- Swagger URL.
- ML service URL.
- Sample API workflow.
- Known limitations.
- Future enhancements.

### Demo Flow

Document this manual flow:

1. Start Docker Compose.
2. Import sample candles.
3. Create an SMA crossover strategy.
4. Run a backtest.
5. View trades.
6. View metrics.
7. Request ML risk score.
8. Review audit events.

---

## 19. Testing Plan

### Backend Unit Tests

Add unit tests for:

- SMA calculation.
- Crossover signal generation.
- Backtest trade creation.
- Metrics calculation.
- CSV validation.
- ML client request/response mapping.

### Backend Integration Tests

Add integration tests for:

- Strategy CRUD.
- Market data import.
- Candle querying.
- Backtest execution.
- Repository behavior with Testcontainers PostgreSQL.

### ML Service Tests

Add Python tests for:

- `GET /health`.
- `POST /predict/strategy-risk`.
- Validation errors.
- Low-risk scenario.
- Medium-risk scenario.
- High-risk scenario.
- Likely-overfit scenario.

### Manual Acceptance Test

From a clean clone:

1. Run `docker compose up --build`.
2. Open Swagger UI.
3. Import sample BTC-USD 1h candles.
4. Create an SMA crossover strategy.
5. Run a backtest.
6. Confirm trades were generated.
7. Confirm metrics were calculated.
8. Request an ML risk score.
9. Confirm the backtest stores the risk score and label.
10. Confirm audit events exist for the flow.

---

## 20. Implementation Order

Build the MVP in this order:

1. Create repository structure and root documentation placeholders.
2. Generate Spring Boot Maven app.
3. Add Docker Compose with PostgreSQL.
4. Configure backend database connection and Swagger.
5. Implement entities and repositories.
6. Implement strategy CRUD.
7. Implement CSV import and sample data.
8. Implement SMA indicator.
9. Implement SMA crossover backtester.
10. Implement metrics calculation.
11. Create FastAPI ML service.
12. Connect backend to ML service.
13. Add audit logging.
14. Add tests.
15. Polish README and demo flow.

---

## 21. Explicit MVP Exclusions

Do not include these in the first milestone:

- Real-money trading, permanently.
- Broker, exchange, custody, or payment API integration, permanently.
- Live, automated, recommended, or broker-backed order execution, permanently.
- Investment advice, trade recommendations, or copy-trading behavior, permanently.
- Full JWT authentication.
- Multi-user account management.
- React frontend.
- Redis queueing.
- Kubernetes.
- AWS/cloud deployment.
- Transformer or attention model implementation.
- Custom user-submitted strategy code.

---

## 22. Future Enhancements

After the MVP is stable, consider:

- JWT authentication and users.
- Risk policies and order approval/rejection.
- Paper trading simulator features that remain simulated-only.
- RSI, MACD, Bollinger Bands, breakout, and mean-reversion strategies.
- Walk-forward testing.
- Monte Carlo simulation.
- Strategy comparison views.
- React dashboard.
- Market regime classifier.
- Anomaly detector.
- PyTorch Transformer encoder for sequence-based regime or trade-quality classification.
- Optional local NVIDIA GPU acceleration for PyTorch training and heavier model inference.
- Cloud deployment after local reproducibility is strong.

---


## 23. Extended API Roadmap

These endpoints are part of the broader project vision. Risk controls, baseline paper trading, and dashboard summary are implemented as backend APIs; authentication, market regime/anomaly analysis, richer dashboard views, and frontend work remain future scope.

The extended roadmap must preserve the research/simulation boundary. It may add richer analysis and simulated workflows, but it must not add broker connectivity, live order routing, custody, or investment-advice features.

### Authentication

```text
POST /api/auth/register
POST /api/auth/login
```

### Market Regime and Anomaly Analysis

```text
GET  /api/market-regime?symbol=BTC-USD
POST /api/anomaly-check
```

### Risk Controls Implemented Baseline

```text
POST /api/strategies/{id}/risk-policy
GET  /api/strategies/{id}/risk-policy
POST /api/risk/evaluate-order
```

### Paper Trading Implemented Baseline

```text
POST  /api/strategies/{id}/paper-sessions
GET   /api/strategies/{id}/paper-sessions
GET   /api/paper-sessions/{id}
PATCH /api/paper-sessions/{id}/start
PATCH /api/paper-sessions/{id}/stop
POST  /api/paper-sessions/{id}/replay
GET   /api/paper-sessions/{id}/orders
GET   /api/paper-sessions/{id}/positions
GET   /api/paper-sessions/{id}/summary
```

### Dashboard

```text
GET /api/dashboard/summary
GET /api/dashboard/strategy-performance
GET /api/dashboard/risk-alerts
```

---

## 24. Attention Model Concept

The attention-based component is an advanced feature and should not be built before the baseline backtesting and ML risk-scoring flow works.

| Transformer Concept | Trading Interpretation |
| --- | --- |
| Token | One candle or timestep. |
| Sequence | Last 64, 128, or 256 candles. |
| Embedding | Numeric representation of candle features. |
| Positional encoding | Preserves candle order. |
| Attention head | Learns relationships between important past candles. |
| Classification head | Outputs regime, trade quality, or drawdown risk. |

Example input shape:

```text
batch_size x sequence_length x feature_count
32 x 128 x 11
```

Example features per candle:

- open, high, low, close, volume
- return
- rolling volatility
- RSI
- SMA distance
- MACD value
- volume z-score

Potential future outputs:

- Market regime classification.
- Trade quality score.
- Drawdown risk warning.
- Anomaly detection support.

### Local NVIDIA GPU Use

GPU acceleration belongs with this future attention-model phase, not the Backend MVP. When implemented locally:

- Training scripts and notebooks may use CUDA if available.
- Inference should report whether it is using CPU or GPU.
- The API should avoid returning different business results solely because GPU is enabled.
- Tests should cover CPU execution, with GPU checks treated as optional environment-specific tests.

---

## 25. Seven-Phase Development Roadmap

| Phase | Scope | Definition of Done |
| --- | --- | --- |
| Phase 1 - Backend Foundation | Spring Boot app, PostgreSQL, strategy entity, candle entity, Swagger, Docker Compose. | App runs locally and can create strategies and store/import candle data. |
| Phase 2 - Basic Backtesting | SMA crossover, indicators, trade simulation, metrics. | User can run a backtest and receive metrics and simulated trades. |
| Phase 3 - Baseline ML Service | FastAPI risk endpoint and Spring Boot ML client. | Backtest result includes ML risk score and classification. |
| Phase 4 - Risk Engine | Risk policy, max position size, stop-loss, max daily loss, audit events. | Implemented baseline simulated-order approval/rejection with logged reasons. |
| Phase 5 - Paper Trading | Paper sessions, simulated orders, positions, replayed candles or scheduled checks. | Implemented baseline sessions, manual orders, positions, summaries, and manual candle replay. |
| Phase 6 - Attention Model | Sequence builder, PyTorch Transformer encoder, market regime endpoint, optional NVIDIA GPU acceleration. | CPU-safe regime foundation, artifact-backed torch inference, local training/export script, and optional torch Compose profile are implemented. Model quality and experiment tracking are now implemented too: seeded mini batch training with early stopping, a regularized model with positional encoding, baseline aware evaluation, and a versioned experiment registry with a comparison view, all reproducible on CPU. |
| Phase 7 - Polish | README, tests, seed data, optional dashboard, screenshots. | Portfolio-ready project with clear docs and reproducible local setup. |

---

## 26. Risks and Design Warnings

| Risk | Mitigation |
| --- | --- |
| Scope creep | Build local MVP first: one strategy, one dataset, one ML endpoint. |
| Overpromising trading performance | Position the app as research/risk analysis, not a profit generator. |
| Bad market data quality | Validate CSV rows, detect missing candles, and log data gaps. |
| ML model gives false confidence | Show reasons/limitations and keep deterministic risk rules separate from ML. |
| GPU-specific setup makes the project hard to run | Keep the MVP CPU-compatible and add GPU support only as an optional profile for later PyTorch work. |
| Transformer complexity | Add it only after baseline backtesting and simple ML are complete. |
| Real-money trading danger | Keep live trading, broker integration, custody, order routing, and trade recommendations permanently out of scope. |
| Uncontrolled user strategy code | Use structured JSON strategies before allowing custom scripting. |

---

## 27. Portfolio Deliverables Checklist

The project should eventually include:

- Polished README with setup, architecture overview, screenshots, and example API calls.
- Swagger/OpenAPI docs showing backend endpoints.
- Sample CSV market dataset included in the repository.
- Demo scenario: import candles, create strategy, run backtest, receive ML risk score, review audit log.
- Short explanation of why the app uses ML for risk analysis instead of guaranteed price prediction.
- Architecture diagram showing backend, ML service, PostgreSQL, and optional dashboard.
- Optional React dashboard with equity curve, drawdown chart, risk score, and regime classification.

## 29. Current Project Progress

- Phases 1-5 are implemented as backend-first local MVP capabilities.
- Phase 6 now has a CPU-safe foundation plus a hardened optional torch path: recent candle sequence schemas, deterministic feature extraction, rule-based market regime classification, artifact-backed PyTorch Transformer inference, a local training/export script, optional torch Compose profile, ML `POST /predict/market-regime`, and backend `GET /api/market-regime`.
- Model quality and experiment tracking for the optional torch path are now implemented while keeping CPU reproducibility: a seeded mini batch training loop with per epoch validation and `--patience` early stopping that keeps the best epoch, a regularized model with light dropout and sinusoidal positional encoding, baseline aware evaluation that reports a majority class baseline and lift with an optional `--holdout-ratio`, and a versioned experiment registry (one entry per run id, seed, git commit, torch version, and per epoch history) with a `compare_market_regime_experiments.py` view.
- Further model quality work (richer features, independent ground truth, hyperparameter search) remains optional research and is not required for the local research baseline.

---

## 28. Assumptions and Defaults

- The project starts from an empty repository.
- The first deliverable is the local Backend MVP.
- Maven is the Java build tool.
- Java 21 and Spring Boot 3 are used.
- PostgreSQL 16 is used locally through Docker Compose.
- The ML service starts as rule-based FastAPI, not a trained model.
- NVIDIA GPU support is optional and reserved for later PyTorch model work; the default MVP stack must run without a GPU.
- Strategy execution is long-only with one position at a time.
- Trades execute at candle close price.
- Swagger is sufficient as the initial UI.
- README quality matters because this is intended as a portfolio project.
