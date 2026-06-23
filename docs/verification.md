# Verification Matrix

Use this checklist from a clean clone or a freshly pulled branch.

## Quick Verification

Run these first when changing the project:

```bash
cd backend && ./mvnw test
cd ../ml-service && python3 -m pytest
cd ../frontend && npm run test
npm run build
cd .. && python3 -m unittest scripts/smoke_demo_test.py
```

When Docker is available, also run:

```bash
docker compose config
docker compose up --build
python3 scripts/smoke_demo.py --timeout-seconds 30
```

| Area | Command | Expected result |
| --- | --- | --- |
| ML service tests | `cd ml-service && python3 -m pytest` | Health and strategy-risk tests pass. |
| Backend unit tests | `cd backend && ./mvnw test` | Spring and service tests pass. Docker-backed Testcontainers tests run when Docker is available and skip when it is not. |
| Backend persistence integration tests | `cd backend && ./mvnw -Dgroups=integration test` | Flyway/JPA persistence tests pass when Docker is available. |
| Frontend tests | `cd frontend && npm run test` | API client and dashboard state tests pass. |
| Frontend build | `cd frontend && npm run build` | TypeScript and Vite production build complete. |
| Smoke helper tests | `python3 -m unittest scripts/smoke_demo_test.py` | Smoke script validation helpers pass. |
| Running-stack smoke demo | `python3 scripts/smoke_demo.py` | Service reachability, import, market data quality, strategy, backtest, ML score, paper trading, dashboard, model status, promoted artifact status fields, persisted regime runs, attention showcase summary, regime run comparison, attention diagnostics, evidence snapshots, regime grouped backtest analysis, assistant reviewable actions, anomaly, and audit checks pass when the stack is running. |
| Compose syntax | `docker compose config` | Compose file renders without errors. |
| Full local stack | `docker compose up --build` | PostgreSQL, backend, and ML service start. |
| Backend docs | Open `http://localhost:8080/swagger-ui.html` | Swagger UI lists backend endpoints. |
| ML health | `curl http://localhost:8000/health` | Returns `{"status":"ok"}`. |
| Demo flow | Follow `docs/demo-flow.md` | Import, strategy, backtest, ML score, paper replay, dashboard, and audit flow works. |
| Demo evidence guide | Follow `docs/demo-evidence.md` | Smoke output, Swagger, frontend, and ML health checks can be recorded for review. |
| Screenshot guide | Follow `docs/screenshots.md` | Required portfolio screenshots are listed without committing local artifacts by default. |
| Market data quality | `curl "http://localhost:8080/api/market-data/quality?symbol=BTC-USD&timeframe=1h"` | Returns candle count, first/last candle time, expected interval, gap count, invalid OHLC count, bad volume count, and warnings. |
| Dashboard risk alerts | `curl http://localhost:8080/api/dashboard/risk-alerts` | Returns derived drawdown and ML-risk alerts, or an empty list. |
| Market regime flow | `curl "http://localhost:8080/api/market-regime?symbol=BTC-USD&timeframe=1h&limit=128"` | Returns a regime label, confidence, reasons, and derived features after candles are imported. |
| Market regime status | `curl "http://localhost:8080/api/market-regime/status"` | Returns requested/effective mode, artifact metadata, fallback warnings, promotion status, promoted run id when present, and artifact hash verification state. |
| Attention showcase summary | `curl http://localhost:8080/api/attention-showcase/summary` | Returns model readiness, latest replay quality, robustness label, evidence snapshot count, baseline disagreement summary, and a next review action. |
| Persisted regime flow | `POST /api/regime-runs`, then `GET /api/regime-runs/{id}` and `GET /api/backtests/{id}/regime-analysis?regimeRunId={id}` | Saves model provenance, prediction points, baseline comparison fields, quality summaries, and grouped backtest metrics. |
| Regime run comparison | `GET /api/regime-runs/comparison?symbol=BTC-USD&timeframe=1h&limit=10` | Returns recent saved runs with average confidence, baseline disagreement rate, dominant regime, anomaly count, and deltas from the prior saved run. |
| Attention evidence flow | `GET /api/market-regime/diagnostics?symbol=BTC-USD&timeframe=1h&limit=20`, then `GET /api/market-regime/evidence-snapshots?symbol=BTC-USD&timeframe=1h&limit=5` | Returns the selected window prediction, baseline comparison, evidence source, top timesteps, feature evidence, and a saved snapshot for later review. |
| Assistant review flow | `POST /api/assistant/sessions`, `POST /api/assistant/sessions/{id}/messages`, then confirm or reject a proposed action | Persists messages, creates whitelisted action proposals, requires confirmation before execution, and records assistant audit events. |
| Backtest chart data | `curl http://localhost:8080/api/backtests/BACKTEST_ID/equity-series` and `curl http://localhost:8080/api/backtests/BACKTEST_ID/drawdown-series` | Returns timestamped points for dashboard charts. |
| Anomaly check | `curl -X POST http://localhost:8080/api/anomaly-check -H "Content-Type: application/json" -d '{"symbol":"BTC-USD","timeframe":"1h","limit":128}'` | Returns an anomaly score, label, reasons, feature details, and classifier source. |
| Optional torch training metadata | `cd ml-service && python scripts/train_market_regime_model.py --csv-path ../data/btc-usd-1h-sample.csv --output models/market-regime.pt --cpu --seed 42 --experiment-name btc-sample-v1` | Writes `models/market-regime.pt`, `models/market-regime.pt.manifest.json`, and updates `models/experiments/index.json`. The manifest records the seed, git commit, torch version, per epoch history, best epoch, and whether early stopping fired. |
| Optional torch run is reproducible | `cd ml-service && python scripts/train_market_regime_model.py --csv-path ../data/btc-usd-1h-sample.csv --output /tmp/m2.pt --cpu --seed 42` then compare `finalTrainLoss` and `validationAccuracy` in the two manifests | The same seed produces the same metrics, confirming the run is deterministic on CPU. |
| Optional torch evaluation report | `cd ml-service && python scripts/evaluate_market_regime_model.py --csv-path ../data/btc-usd-1h-sample.csv --artifact models/market-regime.pt --output models/market-regime-evaluation.json --holdout-ratio 0.2 --experiment-name btc-sample-v1` | Writes accuracy, per label metrics, a confusion matrix, a confidence summary, sample predictions, a majority class baseline, and the lift over that baseline, then updates the experiment registry. |
| Optional torch experiment comparison | `cd ml-service && python scripts/compare_market_regime_experiments.py --experiments-dir models/experiments` | Prints every recorded run sorted by accuracy, with seed, dropout, positional encoding, git commit, lift, confidence, rules disagreement, label drift, and attention concentration columns when available. Re running a name keeps each run under its own run id rather than overwriting. |
| Optional torch diagnostics | `cd ml-service && python scripts/diagnose_market_regime_experiments.py --experiments-dir models/experiments --markdown-output models/experiments/market-regime-diagnostics.md` | Writes local JSON and Markdown diagnostics with registry counts, best evaluated run, incomplete runs, promotion gate status, weak labels, and confusion pairs when evaluation reports exist. |
| Optional torch promotion | `cd ml-service && python scripts/promote_market_regime_experiment.py --experiments-dir models/experiments` | Writes a local promotion summary when an evaluated run passes holdout, accuracy, lift, and artifact-hash gates. |
| Optional promoted auto mode | Start ML with `MARKET_REGIME_MODE=auto` and a valid `MARKET_REGIME_PROMOTION_PATH` or `MARKET_REGIME_ARTIFACT_PATH`, then call `/api/market-regime/status` | Reports `effectiveMode=torch`, the promoted run id, artifact metadata, and `promotionArtifactMatches=true`; missing or invalid promotion summaries fall back to rules with warnings. |
| Optional torch model card | `cd ml-service && python scripts/generate_market_regime_model_card.py --promotion models/experiments/promoted-market-regime.json` | Writes a local Markdown model card for the promoted research candidate. |
| Optional torch sweep dry run | `cd ml-service && python scripts/run_market_regime_sweep.py --dry-run` | Prints train/evaluate command pairs for the default CPU sweep without requiring PyTorch. |

## Local Tooling Notes

- Java 21 is required for backend tests and the Maven wrapper.
- The Maven wrapper is checked into the backend project; local Maven is optional.
- Docker Desktop WSL integration is required for Compose and Testcontainers in WSL.
- If Docker is unavailable, backend unit tests still pass and Docker-backed persistence tests are skipped.
- If Docker is unavailable, ML tests can still be run through the local Python environment.
- If Java is unavailable, backend compile/test verification remains blocked until Java is installed or the backend is built inside Docker.

## Latest Local Verification

Last checked on June 4, 2026:

- `cd ml-service && ../.venv/bin/python -m pytest`: passed, 135 tests.
- `cd ml-service && ../.venv/bin/python scripts/diagnose_market_regime_experiments.py --experiments-dir /tmp/signal-attention-empty-experiments --output /tmp/signal-attention-empty-experiments/market-regime-diagnostics.json --markdown-output /tmp/signal-attention-empty-experiments/market-regime-diagnostics.md`: passed.
- `python3 -m unittest scripts/smoke_demo_test.py`: passed, 7 tests.
- `docker compose config`: passed.
- `cd backend && ./mvnw test`: not rerun in this diagnostics wave because backend code did not change.
- `cd frontend && npm run test`: not rerun in this diagnostics wave because frontend code did not change.
- `cd frontend && npm run build`: not rerun in this diagnostics wave because frontend code did not change.
- `docker compose up --build -d`: not rerun in this governance wave.
- `python3 scripts/smoke_demo.py --timeout-seconds 30`: not rerun in this governance wave because no runtime API or frontend workflow changed.
- Portfolio screenshots were captured from the running frontend and Swagger UI and committed under `docs/assets/screenshots/`.
- Optional torch train/evaluate commands were not rerun. They remain optional because torch dependencies are intentionally outside the default setup.
