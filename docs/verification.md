# Verification Matrix

Use this checklist from a clean clone or a freshly pulled branch.

| Area | Command | Expected result |
| --- | --- | --- |
| ML service tests | `cd ml-service && python3 -m pytest` | Health and strategy-risk tests pass. |
| Backend unit/integration tests | `cd backend && ./mvnw test` | Spring, service, and Testcontainers tests pass. |
| Compose syntax | `docker compose config` | Compose file renders without errors. |
| Full local stack | `docker compose up --build` | PostgreSQL, backend, and ML service start. |
| Backend docs | Open `http://localhost:8080/swagger-ui.html` | Swagger UI lists backend endpoints. |
| ML health | `curl http://localhost:8000/health` | Returns `{"status":"ok"}`. |
| Demo flow | Follow `docs/demo-flow.md` | Import, strategy, backtest, ML score, paper replay, dashboard, and audit flow works. |
| Market regime flow | `curl "http://localhost:8080/api/market-regime?symbol=BTC-USD&timeframe=1h&limit=128"` | Returns a regime label, confidence, reasons, and derived features after candles are imported. |

## Local Tooling Notes

- Java 21 is required for backend tests and the Maven wrapper.
- Docker Desktop WSL integration is required for Compose and Testcontainers in WSL.
- If Docker is unavailable, ML tests can still be run through the local Python environment.
- If Java is unavailable, backend compile/test verification remains blocked until Java is installed or the backend is built inside Docker.
