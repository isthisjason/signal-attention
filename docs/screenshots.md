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
   - Show the sticky workbench navigation, next-action panel, attention showcase readiness panel, summary cards, and risk alerts.

2. Backtest metrics and charts
   - File: `docs/assets/screenshots/backtest-charts.png`
   - Show a completed backtest with return, drawdown, trade count, and the responsive equity and drawdown charts visible.

3. Risk alerts and audit trail
   - File: `docs/assets/screenshots/risk-audit.png`
   - Show the risk alerts panel and audit events panel after a backtest and risk score have run.

4. Paper trading panel
   - File: `docs/assets/screenshots/paper-trading.png`
   - Show a paper session after start, manual order, replay, and stop.
   - Include session status, cash/equity values, and recent orders or positions.

5. Market regime and anomaly panels
   - File: `docs/assets/screenshots/market-regime-anomaly.png`
   - Show the current market regime result, feature chart, selectable replay window table, attention diagnostics, and an anomaly check result after sample candles are imported.

6. Swagger endpoint overview
   - File: `docs/assets/screenshots/swagger-endpoints.png`
   - Open `http://localhost:8080/swagger-ui.html`.
   - Show the backend endpoint groups for strategies, market data, backtests, risk, paper trading, dashboard, audit, market regime, and anomaly checks.

7. Attention evidence drilldown
   - File: `docs/assets/screenshots/attention-evidence.png`
   - Run a regime replay, select a disagreement or low-confidence window, and keep its top timesteps, feature evidence, provenance, and robustness review visible.

## Notes

- Use the sample BTC-USD data so screenshots match the documented demo flow.
- The committed dashboard screenshots are real historical captures, but they predate the current attention-showcase layout and should not be treated as current UI evidence.
- Replace `dashboard-overview.png` and `market-regime-anomaly.png`, then add `attention-evidence.png`, after running the current smoke workflow in a browser-enabled environment.
- Do not include `.env` values, local database credentials, browser profiles, or generated model artifacts.
- If Docker is unavailable in the current WSL environment, record that blocker in `PROJECT_PROGRESS.local.md` and capture screenshots later from a Docker-enabled environment.
