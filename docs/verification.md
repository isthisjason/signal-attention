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
| Backtest chart data | `curl http://localhost:8080/api/backtests/BACKTEST_ID/equity-series` and `curl http://localhost:8080/api/backtests/BACKTEST_ID/drawdown-series` | Returns timestamped points for dashboard charts. |
| Anomaly check | `curl -X POST http://localhost:8080/api/anomaly-check -H "Content-Type: application/json" -d '{"symbol":"BTC-USD","timeframe":"1h","limit":128}'` | Returns an anomaly score, label, reasons, feature details, and classifier source. |
| Optional torch training metadata | `cd ml-service && python scripts/train_market_regime_model.py --csv-path ../data/btc-usd-1h-sample.csv --output models/market-regime.pt --cpu --seed 42 --experiment-name btc-sample-v1` | Writes `models/market-regime.pt`, `models/market-regime.pt.manifest.json`, and updates `models/experiments/index.json`. The manifest records the seed, git commit, torch version, per epoch history, best epoch, and whether early stopping fired. |
| Optional torch run is reproducible | `cd ml-service && python scripts/train_market_regime_model.py --csv-path ../data/btc-usd-1h-sample.csv --output /tmp/m2.pt --cpu --seed 42` then compare `finalTrainLoss` and `validationAccuracy` in the two manifests | The same seed produces the same metrics, confirming the run is deterministic on CPU. |
| Optional torch evaluation report | `cd ml-service && python scripts/evaluate_market_regime_model.py --csv-path ../data/btc-usd-1h-sample.csv --artifact models/market-regime.pt --output models/market-regime-evaluation.json --holdout-ratio 0.2 --experiment-name btc-sample-v1` | Writes accuracy, per label metrics, a confusion matrix, a confidence summary, sample predictions, a majority class baseline, and the lift over that baseline, then updates the experiment registry. |
| Optional torch experiment comparison | `cd ml-service && python scripts/compare_market_regime_experiments.py --experiments-dir models/experiments` | Prints every recorded run sorted by accuracy, with seed, dropout, positional encoding, git commit, and lift columns. Re running a name keeps each run under its own run id rather than overwriting. |

## Local Tooling Notes

- Java 21 is required for backend tests and the Maven wrapper.
- The Maven wrapper is checked into the backend project; local Maven is optional.
- Docker Desktop WSL integration is required for Compose and Testcontainers in WSL.
- If Docker is unavailable, backend unit tests still pass and Docker-backed persistence tests are skipped.
- If Docker is unavailable, ML tests can still be run through the local Python environment.
- If Java is unavailable, backend compile/test verification remains blocked until Java is installed or the backend is built inside Docker.

## Latest Local Verification

Last checked on May 28, 2026:

- `cd backend && ./mvnw test`: passed, with 100 tests run and 4 Docker-backed persistence tests skipped because Docker was unavailable in this WSL environment.
- `cd ml-service && ../.venv/bin/python -m pytest`: passed, 81 tests.
- `cd frontend && npm run test`: passed, 23 tests.
- `cd frontend && npm run build`: passed.
- `python3 -m unittest scripts/smoke_demo_test.py`: passed, 3 tests.
- `docker compose config`: blocked because Docker CLI is unavailable in this WSL distro.
- `docker compose up --build -d`: not rerun because Docker CLI is unavailable in this WSL distro.
- `python3 scripts/smoke_demo.py`: not rerun because the local Compose stack could not be started in this WSL distro.
- Optional torch train/evaluate commands were not rerun. They remain optional because torch dependencies are intentionally outside the default setup.
