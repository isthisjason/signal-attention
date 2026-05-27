# Verification Matrix

Use this checklist from a clean clone or a freshly pulled branch.

| Area | Command | Expected result |
| --- | --- | --- |
| ML service tests | `cd ml-service && python3 -m pytest` | Health and strategy-risk tests pass. |
| Backend unit tests | `cd backend && ./mvnw test` | Spring and service tests pass. Docker-backed Testcontainers tests run when Docker is available and skip when it is not. |
| Backend persistence integration tests | `cd backend && ./mvnw -Dgroups=integration test` | Flyway/JPA persistence tests pass when Docker is available. |
| Frontend tests | `cd frontend && npm run test` | API client and dashboard state tests pass. |
| Frontend build | `cd frontend && npm run build` | TypeScript and Vite production build complete. |
| Smoke helper tests | `python3 -m unittest scripts/smoke_demo_test.py` | Smoke script validation helpers pass. |
| Running-stack smoke demo | `python3 scripts/smoke_demo.py` | Service reachability, import, strategy, backtest, ML score, paper trading, dashboard, regime, and audit checks pass when the stack is running. |
| Compose syntax | `docker compose config` | Compose file renders without errors. |
| Full local stack | `docker compose up --build` | PostgreSQL, backend, and ML service start. |
| Backend docs | Open `http://localhost:8080/swagger-ui.html` | Swagger UI lists backend endpoints. |
| ML health | `curl http://localhost:8000/health` | Returns `{"status":"ok"}`. |
| Demo flow | Follow `docs/demo-flow.md` | Import, strategy, backtest, ML score, paper replay, dashboard, and audit flow works. |
| Dashboard risk alerts | `curl http://localhost:8080/api/dashboard/risk-alerts` | Returns derived drawdown and ML-risk alerts, or an empty list. |
| Market regime flow | `curl "http://localhost:8080/api/market-regime?symbol=BTC-USD&timeframe=1h&limit=128"` | Returns a regime label, confidence, reasons, and derived features after candles are imported. |
| Optional torch training metadata | `cd ml-service && python scripts/train_market_regime_model.py --csv-path ../data/btc-usd-1h-sample.csv --output models/market-regime.pt --cpu --experiment-name btc-sample-v1` | Writes `models/market-regime.pt`, `models/market-regime.pt.manifest.json`, and updates `models/experiments/index.json` with split-aware training metrics. |
| Optional torch evaluation report | `cd ml-service && python scripts/evaluate_market_regime_model.py --csv-path ../data/btc-usd-1h-sample.csv --artifact models/market-regime.pt --output models/market-regime-evaluation.json --experiment-name btc-sample-v1` | Writes accuracy, per-label metrics, confusion matrix, confidence summary, sample predictions, and updates the experiment registry. |

## Local Tooling Notes

- Java 21 is required for backend tests and the Maven wrapper.
- The Maven wrapper is checked into the backend project; local Maven is optional.
- Docker Desktop WSL integration is required for Compose and Testcontainers in WSL.
- If Docker is unavailable, backend unit tests still pass and Docker-backed persistence tests are skipped.
- If Docker is unavailable, ML tests can still be run through the local Python environment.
- If Java is unavailable, backend compile/test verification remains blocked until Java is installed or the backend is built inside Docker.

## Latest Local Verification

Last checked on May 27, 2026:

- `cd backend && ./mvnw test`: passed, with 92 tests run and 4 Docker-backed persistence tests skipped because Docker was unavailable in this WSL environment.
- `cd ml-service && ../.venv/bin/python -m pytest`: passed, 65 tests.
- `cd frontend && npm run test`: passed, 20 tests.
- `python3 -m unittest scripts/smoke_demo_test.py`: passed, 3 tests.
- `docker compose config`: blocked because the `docker` command was not available in this WSL distro. Docker Desktop WSL integration is still needed here.
- `python3 scripts/smoke_demo.py`: blocked in this WSL distro because the local stack cannot be started without Docker.
- `cd frontend && npm run build`: not rerun in this pass.
- Optional torch train/evaluate commands were not rerun. They remain optional because torch dependencies are intentionally outside the default setup.
