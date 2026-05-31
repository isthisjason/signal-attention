# Demo Evidence

Use this page after starting the local stack to collect proof that the project runs end to end. These notes are meant for a portfolio review, a pull request, or a final README pass.

## Start The Stack

```bash
docker compose up --build
```

Wait until PostgreSQL is healthy and both application services have started.

## Required Checks

Run these from the repository root in another terminal:

```bash
python3 scripts/smoke_demo.py --timeout-seconds 30
```

Expected final line:

```text
stack, core, paper, and analysis workflow smoke checks passed for strategy #..., backtest #..., session #...
```

Check backend OpenAPI:

```bash
curl http://localhost:8080/v3/api-docs
```

Expected result: JSON with a top-level `paths` field.

Check Swagger UI in a browser:

```text
http://localhost:8080/swagger-ui.html
```

Expected result: Swagger lists the SignalAttention backend endpoints.

Check ML service health:

```bash
curl http://localhost:8000/health
```

Expected result:

```json
{"status":"ok"}
```

Check frontend shell:

```text
http://localhost:5173
```

Expected result: the SignalAttention dashboard loads and shows the workbench panels.

## What To Save

For a portfolio pass, save:

- The smoke script output.
- A screenshot of Swagger with the endpoint list visible.
- A screenshot of the dashboard after the smoke demo has created data.
- Any Docker or environment blocker, especially if Docker is unavailable in WSL.

Do not save or commit local database contents, generated model artifacts, or machine-specific environment files.
