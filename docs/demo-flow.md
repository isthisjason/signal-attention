# SignalAttention MVP Demo Flow

This flow assumes the local stack is running:

```bash
docker compose up --build
```

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
```

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
```

## Market Regime Analysis

After importing candles, request a CPU-safe regime classification for the latest candle window:

```bash
curl "http://localhost:8080/api/market-regime?symbol=BTC-USD&timeframe=1h&limit=128"
```

The response includes a regime label, confidence, reasons, and derived features. This is deterministic rule-based analysis, not trained PyTorch Transformer inference.

## Audit Events

Review recent audit events, or filter by entity:

```bash
curl http://localhost:8080/api/audit-events
curl "http://localhost:8080/api/audit-events?entityType=BACKTEST&limit=10"
```

## Known Simulation Limits

- Paper trading is deterministic simulation only; it does not place real broker orders.
- Candle replay is manual and bounded by the request. There is no background scheduler yet.
- Paper fills use submitted or replay candle close prices and do not model slippage.
- Position summaries use the latest imported candle for mark-to-market values. If no candle exists for an open position, the position is returned as unpriced.
- Market regime classification is CPU-safe and rule-based; trained attention-model inference remains future scope.
