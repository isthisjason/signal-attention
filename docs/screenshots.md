# Screenshot Guide

Use these screenshots when the local stack is running and the smoke demo has created data. The goal is to show the actual app, not a staged mockup.

Committed screenshots live here:

```text
docs/assets/screenshots/
```

## Capture List

1. Dashboard overview after demo data
   - File: `docs/assets/screenshots/dashboard-overview.png`
   - Open `http://localhost:5173`.
   - Show the summary cards, risk alerts, and at least part of the workflow controls.

2. Backtest metrics and charts
   - File: `docs/assets/screenshots/backtest-charts.png`
   - Show a completed backtest with return, drawdown, trade count, equity chart, and drawdown chart visible.

3. Risk alerts and audit trail
   - File: `docs/assets/screenshots/risk-audit.png`
   - Show the risk alerts panel and audit events panel after a backtest and risk score have run.

4. Paper trading panel
   - File: `docs/assets/screenshots/paper-trading.png`
   - Show a paper session after start, manual order, replay, and stop.
   - Include session status, cash/equity values, and recent orders or positions.

5. Market regime and anomaly panels
   - File: `docs/assets/screenshots/market-regime-anomaly.png`
   - Show the current market regime result and an anomaly check result after sample candles are imported.

6. Swagger endpoint overview
   - File: `docs/assets/screenshots/swagger-endpoints.png`
   - Open `http://localhost:8080/swagger-ui.html`.
   - Show the backend endpoint groups for strategies, market data, backtests, risk, paper trading, dashboard, audit, market regime, and anomaly checks.

## Notes

- Use the sample BTC-USD data so screenshots match the documented demo flow.
- The committed dashboard screenshots are full-page captures of the real local frontend after the smoke demo has created data.
- Do not include `.env` values, local database credentials, browser profiles, or generated model artifacts.
- If Docker is unavailable in the current WSL environment, record that blocker in `PROJECT_PROGRESS.local.md` and capture screenshots later from a Docker-enabled environment.
