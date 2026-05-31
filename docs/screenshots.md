# Screenshot Guide

Use these screenshots when the local stack is running and the smoke demo has created data. The goal is to show the actual app, not a staged mockup.

Recommended folder if screenshots are committed later:

```text
docs/assets/screenshots/
```

## Capture List

1. Dashboard overview after demo data
   - Open `http://localhost:5173`.
   - Show the summary cards, risk alerts, and at least part of the workflow controls.

2. Backtest metrics and charts
   - Show a completed backtest with return, drawdown, trade count, equity chart, and drawdown chart visible.

3. Risk alerts and audit trail
   - Show the risk alerts panel and audit events panel after a backtest and risk score have run.

4. Paper trading panel
   - Show a paper session after start, manual order, replay, and stop.
   - Include session status, cash/equity values, and recent orders or positions.

5. Market regime and anomaly panels
   - Show the current market regime result and an anomaly check result after sample candles are imported.

6. Swagger endpoint overview
   - Open `http://localhost:8080/swagger-ui.html`.
   - Show the backend endpoint groups for strategies, market data, backtests, risk, paper trading, dashboard, audit, market regime, and anomaly checks.

## Notes

- Use the sample BTC-USD data so screenshots match the documented demo flow.
- Do not include `.env` values, local database credentials, browser profiles, or generated model artifacts.
- If Docker is unavailable in the current WSL environment, record that blocker in `PROJECT_PROGRESS.local.md` and capture screenshots later from a Docker-enabled environment.
