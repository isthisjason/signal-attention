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
