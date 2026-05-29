# SignalAttention MVP Demo Flow

This flow assumes the local stack is running:

```bash
docker compose up --build
```

The React dashboard is available at `http://localhost:5173` when the frontend service or local Vite dev server is running.

The dashboard can drive the same flow as the curl commands below: import candles, create the SMA strategy, run the backtest, inspect equity/drawdown charts, score ML risk, create/start paper sessions, submit manual paper orders, replay candles, and refresh summary/audit/regime panels.

To run the same workflow as an automated smoke check:

```bash
python3 scripts/smoke_demo.py
```

The smoke script assumes the stack is already running and uses `http://localhost:8080`, `http://localhost:5173`, and `http://localhost:8000` by default. Use `--backend-url`, `--frontend-url`, `--ml-url`, or `--sample-csv` to point it at different local endpoints or data.

## 1. Import Sample Candles

```bash
curl -F "file=@data/btc-usd-1h-sample.csv" \
  http://localhost:8080/api/market-data/import
```

## 2. Create an SMA Strategy

```bash
curl -X POST http://localhost:8080/api/strategies \
  -H "Content-Type: application/json" \
  -d '{
    "name": "BTC SMA Crossover",
    "symbol": "BTC-USD",
    "timeframe": "1h",
    "strategyType": "SMA_CROSSOVER",
    "rules": {
      "shortWindow": 3,
      "longWindow": 5,
      "initialBalance": 10000,
      "feePercent": 0.1,
      "positionSizePercent": 50
    }
  }'
```

Use the returned `id` as `STRATEGY_ID`.

## 3. Run a Backtest

```bash
curl -X POST http://localhost:8080/api/strategies/STRATEGY_ID/backtests \
  -H "Content-Type: application/json" \
  -d '{
    "startDate": "2024-01-01T00:00:00Z",
    "endDate": "2024-01-10T00:00:00Z"
  }'
```

Use the returned `id` as `BACKTEST_ID`.

## 4. Review Results

```bash
curl http://localhost:8080/api/backtests/BACKTEST_ID
curl http://localhost:8080/api/backtests/BACKTEST_ID/trades
curl http://localhost:8080/api/backtests/BACKTEST_ID/metrics
curl http://localhost:8080/api/backtests/BACKTEST_ID/equity-series
curl http://localhost:8080/api/backtests/BACKTEST_ID/drawdown-series
```

In the dashboard, the backtest panel shows the same equity and drawdown series with latest, high, and low summaries. These charts are visual feedback for the assessment; they are not trade recommendations.

## 5. Score ML Risk

```bash
curl -X POST http://localhost:8080/api/backtests/BACKTEST_ID/ml-risk-score
```

Fetch the backtest again to confirm `mlRiskScore` and `mlRiskLabel` were persisted.

## Optional Phase 4 Risk Policy Flow

Create or update a strategy risk policy:

```bash
curl -X POST http://localhost:8080/api/strategies/STRATEGY_ID/risk-policy \
  -H "Content-Type: application/json" \
  -d '{
    "maxPositionSizePercent": 25,
    "stopLossPercent": 5,
    "takeProfitPercent": 12,
    "maxDailyLossPercent": 8,
    "cooldownMinutes": 30
  }'
```

Fetch the current policy:

```bash
curl http://localhost:8080/api/strategies/STRATEGY_ID/risk-policy
```

Evaluate a simulated order:

```bash
curl -X POST http://localhost:8080/api/risk/evaluate-order \
  -H "Content-Type: application/json" \
  -d '{
    "strategyId": STRATEGY_ID,
    "symbol": "BTC-USD",
    "side": "BUY",
    "quantity": 0.25,
    "price": 42000,
    "accountEquity": 100000,
    "currentDailyLoss": 0,
    "evaluatedAt": "2024-01-01T00:00:00Z"
  }'
```

## Optional Phase 5 Paper Trading Flow

Create a paper trading session:

```bash
curl -X POST http://localhost:8080/api/strategies/STRATEGY_ID/paper-sessions \
  -H "Content-Type: application/json" \
  -d '{
    "initialBalance": 100000
  }'
```

Use the returned `id` as `PAPER_SESSION_ID`, then start the session:

```bash
curl -X PATCH http://localhost:8080/api/paper-sessions/PAPER_SESSION_ID/start
```

Fetch the session directly or list sessions for a strategy:

```bash
curl http://localhost:8080/api/paper-sessions/PAPER_SESSION_ID
curl http://localhost:8080/api/strategies/STRATEGY_ID/paper-sessions
```

Submit a simulated buy order:

```bash
curl -X POST http://localhost:8080/api/paper-sessions/PAPER_SESSION_ID/orders \
  -H "Content-Type: application/json" \
  -d '{
    "side": "BUY",
    "symbol": "BTC-USD",
    "quantity": 0.25,
    "price": 42000
  }'
```

Review simulated activity:

```bash
curl http://localhost:8080/api/paper-sessions/PAPER_SESSION_ID/orders
curl http://localhost:8080/api/paper-sessions/PAPER_SESSION_ID/positions
curl http://localhost:8080/api/paper-sessions/PAPER_SESSION_ID/summary
```

The dashboard paper-trading panel exposes the same manual order path. Select the active paper session, enter side, symbol, quantity, and price, then submit the order to refresh orders, positions, summary, and audit state.

Replay imported candles through the running paper session:

```bash
curl -X POST http://localhost:8080/api/paper-sessions/PAPER_SESSION_ID/replay \
  -H "Content-Type: application/json" \
  -d '{
    "startDate": "2024-01-01T00:00:00Z",
    "endDate": "2024-01-10T00:00:00Z",
    "maxCandles": 250
  }'
```

Stop the session:

```bash
curl -X PATCH http://localhost:8080/api/paper-sessions/PAPER_SESSION_ID/stop
```

## Dashboard Summary

```bash
curl http://localhost:8080/api/dashboard/summary
curl http://localhost:8080/api/dashboard/strategy-performance
curl http://localhost:8080/api/dashboard/risk-alerts
```

Open `http://localhost:5173` to view the same demo state in the dashboard. The page shows summary cards, derived risk alerts, strategy performance, market regime status, and recent audit events.

The dashboard also includes workbench controls for the main demo flow:

- Import `data/btc-usd-1h-sample.csv`.
- Create a default BTC-USD 1h SMA crossover strategy.
- Run the January 1-10, 2024 backtest.
- Review the equity and drawdown chart summaries after the backtest.
- Score the latest backtest with the ML risk endpoint.
- Create a paper session, start or stop it, submit manual paper orders, and replay imported candles.
- Run regime replay to inspect the candlestick assessment chart with regime and trade markers.

## Market Regime Analysis

After importing candles, request a CPU-safe regime classification for the latest candle window:

```bash
curl "http://localhost:8080/api/market-regime?symbol=BTC-USD&timeframe=1h&limit=128"
```

The response includes a regime label, confidence, reasons, and derived features. The default service uses deterministic rule-based analysis. Optional torch-backed inference can be enabled only when a compatible local artifact is provided.

Run a simple anomaly check against the same recent candle window:

```bash
curl -X POST http://localhost:8080/api/anomaly-check \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "BTC-USD",
    "timeframe": "1h",
    "limit": 128
  }'
```

The anomaly response includes a score, label, reasons, derived features, and classifier source. It is a research warning only, not a trade signal.

The dashboard regime replay panel adds a candlestick assessment chart over the selected date range. The chart shows candle direction, price context, regime windows, and backtest trade markers when a backtest is available.

For optional torch experiments, install `ml-service/requirements-torch.txt`, train an artifact, and evaluate it before enabling torch mode:

```bash
cd ml-service
python scripts/train_market_regime_model.py \
  --csv-path ../data/btc-usd-1h-sample.csv \
  --output models/market-regime.pt \
  --cpu
python scripts/evaluate_market_regime_model.py \
  --csv-path ../data/btc-usd-1h-sample.csv \
  --artifact models/market-regime.pt \
  --output models/market-regime-evaluation.json
```

The optional evaluation report contains accuracy, per-label metrics, a confusion matrix, confidence summary, and sample predictions. Torch-mode regime responses also include provenance fields for the dashboard; rule mode remains the default demo path.

## Audit Events

Review recent audit events, or filter by entity:

```bash
curl http://localhost:8080/api/audit-events
curl "http://localhost:8080/api/audit-events?entityType=BACKTEST&limit=10"
```

## Known Simulation Limits

- Paper trading is deterministic simulation only; it does not place real broker orders and the project is not intended to add live order execution.
- SignalAttention is a research simulator, not a broker, exchange connector, custody system, investment adviser, or trade recommendation engine.
- Candle replay is manual and bounded by the request. There is no background scheduler yet.
- Paper fills use submitted or replay candle close prices and do not model slippage.
- Position summaries use the latest imported candle for mark-to-market values. If no candle exists for an open position, the position is returned as unpriced.
- Market regime classification defaults to the CPU-safe rules path; torch-backed inference is optional and remains a local research workflow.
- Anomaly checks are deterministic and only describe unusual recent candle behavior.
