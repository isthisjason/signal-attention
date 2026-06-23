# SignalAttention Implementation Plan

## 1. Project Direction

SignalAttention is a local research workbench for showing how an attention based sequence model can add richer regime evidence than traditional rule based analysis alone.

The core product question is no longer just whether a simple strategy made or lost money in a backtest. The stronger portfolio question is what an attention mechanism reveals about regime dependence, confidence, anomaly overlap, and sequence level evidence that traditional indicators or rule based labels may miss.

Current pitch:

SignalAttention compares an attention based market regime workflow against traditional SMA and rule based baselines, using backtesting, paper replay, diagnostics, audit trails, and evidence snapshots to show where attention adds insight and where it does not.

The first implementation phase proved the practical foundation: import market data, store it cleanly, run repeatable backtests, simulate paper sessions, call a separate ML service, and leave an audit trail. The current phase makes the attention model the center of the product. Traditional indicators, SMA crossover rules, rule based risk scoring, and deterministic regime labels remain, but their role is secondary. They are baselines, references, and sanity checks used to explain whether the attention based path adds useful sequence level context beyond older methods.

## 2. Product Boundaries

SignalAttention is not a trading bot and should not be described as one.

Permanent exclusions:

- No broker, exchange, custody, or live order routing integration.
- No real money trading.
- No investment advice or trade recommendations.
- No promises of profitable outcomes.
- No high frequency trading.

Current non-goals:

- Authentication, users, roles, and account isolation.
- Cloud deployment, Kubernetes, or managed infrastructure.
- External LLM credentials as a requirement for the assistant.
- Treating weak rule derived labels as independent market truth.

The app should stay local, reproducible, CPU safe by default, and honest about uncertainty. Optional torch artifacts may be used when locally available, but a fresh clone must still run with deterministic rule based fallbacks.

## 3. Architecture

The project is split into four main layers:

- `backend`: Java 21 Spring Boot 3 API. Owns persistence, strategy rules, candle import, backtests, paper trading, risk policies, audit events, dashboard aggregation, assistant action execution, and ML service integration.
- `ml-service`: Python FastAPI service. Owns market regime inference, strategy risk scoring, anomaly checks, rule based baselines, optional torch attention inference, diagnostics, experiment registry tools, and artifact workflow scripts.
- `frontend`: React, TypeScript, Vite, Recharts, Vitest, and Testing Library. Provides the local workbench for importing data, running backtests, replaying paper sessions, inspecting regimes, comparing baselines, and reviewing assistant actions.
- `data` and `docs`: Local sample BTC-USD candle data, verification notes, architecture notes, screenshot evidence, and demo workflow documentation.

Core infrastructure:

- PostgreSQL 16 through Docker Compose.
- Flyway migrations for backend tables.
- Swagger/OpenAPI for backend API inspection.
- Local model artifacts and experiment registries under ignored model directories.
- Optional torch Compose profile for artifact backed inference.

## 4. Attention Based Workflow

The main research workflow should be:

1. Import historical OHLCV candle data.
2. Review market data quality warnings before relying on the sample.
3. Train, evaluate, promote, or load an attention based market regime artifact.
4. Run rolling regime inference across candle windows.
5. Persist regime runs, prediction points, model provenance, feature version, artifact identity, baseline labels, and inference timing.
6. Run strategy backtests over the same candle range.
7. Analyze backtest performance grouped by inferred regime.
8. Compare attention labels against the rule based baseline and call out agreement, disagreement, and low confidence cases.
9. Inspect attention evidence, confidence, feature evidence, top timesteps, and saved evidence snapshots to show what the sequence model paid attention to.
10. Replay paper sessions through selected windows to understand behavior under different inferred regimes.
11. Use the assistant to explain state and propose reviewable actions, with explicit user confirmation before state changes.

The default demo should remain usable without local torch artifacts. In `rules` mode the service uses deterministic baselines. In `auto` mode it may prefer a valid promoted attention artifact when one is configured and present, then fall back to rules with clear status warnings.

## 5. Baselines And Traditional Evaluation

Traditional evaluations remain important, but they are no longer the main product story. Their job is to make the attention workflow credible by giving it clear comparison points.

Secondary and comparison roles:

- SMA crossover is the simple strategy baseline used for deterministic backtests and demo repeatability.
- Rule based regime labels provide weak labels for training and a baseline for disagreement analysis.
- Rule based ML risk scoring remains a sanity check for strategy fragility, drawdown, trade count, volatility, win rate, and profit factor.
- Market data quality checks protect the demo from misleading imported data.
- Anomaly checks flag unusual candle behavior as research warnings.
- Backtest metrics, equity curves, drawdown, trades, and paper summaries provide the traditional evaluation surface that attention based regime analysis is compared against.

The project should not hide these baselines. It should show them beside the attention model so the user can see where the learned model agrees, disagrees, improves explanation, or fails to add value. The showcase claim should be that attention gives a more inspectable sequence aware review, not that it guarantees better trades or is always more accurate.

## 6. Backend Plan

The backend should treat market regime inference and model provenance as first class application data.

Implemented foundation:

- Strategy CRUD and SMA crossover rule validation.
- CSV market candle import and market data quality endpoint.
- Deterministic backtest runs with trades, metrics, equity series, and drawdown series.
- Risk policies and simulated order evaluation.
- Paper trading sessions, orders, positions, summaries, and candle replay.
- Audit event persistence and query APIs.
- Dashboard aggregation APIs.
- ML risk, anomaly, market regime, diagnostics, and model status clients.
- Regime run history, regime prediction persistence, baseline comparison, regime grouped backtest analysis, robustness summaries, experiment diagnostics proxying, and evidence snapshots.
- Assistant sessions, messages, proposed actions, confirmation, rejection, execution results, and audit events.

Continuing backend targets:

- Keep experiment diagnostics read only; training, evaluation, and promotion remain script driven.
- Preserve backward compatible API response additions through optional fields.
- Keep assistant execution whitelisted and reviewable.
- Keep audit events clear for inference, diagnostics, assistant actions, paper replay, and analysis runs.

## 7. ML Service And Artifact Lifecycle

The ML service owns both the baseline and attention paths.

Implemented foundation:

- Rule based strategy risk scoring.
- Rule based market regime classification.
- Candle anomaly checks.
- Torch market regime feature extraction.
- Transformer v1 artifact loading and inference.
- Attention transformer v2 architecture with inspectable attention weights.
- Artifact metadata validation for model version, feature version, labels, sequence length, feature order, normalization, architecture, and config.
- Training script with seeded mini batch training, chronological validation, early stopping, normalization from train windows only, dropout, positional encoding, and selectable architecture.
- Evaluation script with accuracy, per label metrics, confusion matrix, confidence summary, majority class baseline, lift, holdout scoring, and registry integration.
- Experiment comparison, diagnostics, promotion, sweep dry run, and model card scripts.
- Read only experiment diagnostics endpoint for model lab review.

Completed artifact lifecycle wave:

1. Document promoted artifact lifecycle. Done.
2. Train inspectable attention artifacts through a selectable v2 architecture path. Done.
3. Evaluate v2 attention diagnostics while keeping v1 fallback attribution compatible. Done.
4. Promote artifacts with runnable manifests and clear gate results. Done.
5. Load promoted artifacts in auto mode when a valid local promotion exists. Done.
6. Report promotion status through backend model status. Done.
7. Surface promotion state in the workbench. Done.
8. Extend smoke checks for promoted artifacts without breaking the default rules path. Done.
9. Update demo documentation for promoted attention mode. Done.
10. Verify the promoted attention lifecycle. Done.

Generated `.pt` files, experiment registries, diagnostics, promotion outputs, and model cards are local research artifacts and should stay uncommitted by default.

## 8. Frontend Workbench

The frontend should feel like an ML workbench, not a generic trading terminal or marketing site.

Implemented surfaces:

- Import sample data and show quality summary.
- Create the default BTC-USD SMA crossover baseline.
- Run the January 2024 comparison backtest.
- Review metrics, trades, equity, and drawdown.
- Score baseline risk and show persisted risk labels.
- Manage paper sessions, simulated orders, positions, summary, and replay.
- Show dashboard summary, risk alerts, baseline performance, and audit events.
- Show model status, model lab diagnostics, saved regime runs, run quality summaries, recent run comparison, baseline disagreement, anomaly context, regime grouped backtest analysis, robustness review, attention evidence, and saved evidence snapshots.
- Provide an assistant panel with message history, proposed actions, confirmation controls, and action refresh behavior.

Continuing frontend targets:

- Put a compact attention showcase summary near the top of the workbench so model readiness, latest replay quality, robustness state, and evidence snapshots are visible before the older trading controls.
- Keep model lab and promoted artifact state visible without implying deployment quality.
- Keep rules fallback status clear.
- Keep attention evidence close to regime replay and baseline comparison.
- Preserve responsive behavior and chart readability.

## 9. Assistant And Reviewable Actions

The assistant is a workflow explainer and orchestrator, not an autonomous trader.

Implemented behavior:

- Provider neutral backend contract.
- Deterministic local provider for reproducible demos.
- Session, message, proposed action, status, payload, execution result, and confirmation persistence.
- Reviewable actions for backtests, regime replay, attention diagnostics, model lab review, robustness review, paper session start, and paper replay.
- Confirmation and rejection APIs.
- Audit events for assistant proposals and executed actions.
- Frontend assistant panel integrated with workbench state.

Assistant boundaries:

- It may explain model state, baseline disagreement, backtest behavior, paper replay results, risk, uncertainty, and useful next experiments.
- It may propose actions for the user to review.
- It must not silently mutate state.
- It must not recommend buying, selling, or holding assets.

## 10. Implementation Progress

Completed major waves:

- Backend MVP foundation: strategy management, market data import, indicators, backtesting, ML risk integration, audit logging, Docker setup, and Swagger documentation.
- Paper trading and risk policy foundation: simulated sessions, orders, positions, replay, and policy evaluation.
- Frontend workbench: dashboard workflow for import, strategy, backtest, ML risk, paper trading, charts, audit, anomaly, and regimes.
- Verification and portfolio evidence: smoke demo, verification docs, screenshots, README polish, and demo evidence workflow.
- Market data quality wave: read only quality endpoint, workbench rendering, smoke coverage, and documentation.
- ML experiment governance and diagnostics: training history, evaluation, comparison, promotion gates, model cards, diagnostics, and local artifact hygiene.
- ML first regime workflow: model status, backend model status, regime run persistence, saved replay results, baseline comparison, regime grouped backtest analysis, dashboard surfacing, and smoke verification.
- Assistant orchestration wave: persistence, provider contract, confirmed actions, workbench panel, and verification docs.
- Attention diagnostics wave: artifact metadata status, v2 attention architecture, compatibility comments, diagnostics generation, backend diagnostics endpoint, evidence snapshots, experiment comparison by attention behavior, workbench evidence surfacing, assistant diagnostics actions, and verification.
- Promoted artifact lifecycle wave: v2-aware evaluation, verified promotion summaries, auto loading from promoted artifacts, backend status fields, workbench visibility, smoke coverage, and documentation.
- Regime run comparison wave: derived run quality summaries, read only comparison API, assistant comparison context, workbench comparison table, smoke coverage, and documentation.
- Model lab and robustness wave: read only experiment diagnostics API, backend proxy, workbench Model Lab panel, regime robustness summaries, assistant model review actions, tests, and documentation.
- ML-first cleanup wave: removed the duplicate frontend regime-analysis table in favor of the robustness review path, extracted shared regime quality and robustness labels, optimized assistant context lookup, typed model lab diagnostics schemas, refocused workbench copy around attention regimes and baselines, and expanded smoke checks for model lab plus robustness review.

Next showcase wave:

- Add a read only attention showcase summary that joins model status, latest saved regime quality, robustness review, evidence snapshot count, and promotion state.
- Make replay evidence easier to scan by putting confidence, baseline disagreement, anomaly overlap, and trade context together.
- Add a selected-window evidence drilldown that keeps top timesteps, feature evidence, and provenance near the replay.
- Summarize baseline disagreement patterns as research context, not proof that the model is right.
- Keep README and comments human without turning comments into filler.

Current recent commits:

```text
804b26c expanded smoke coverage for model lab robustness
0116363 showcased attention workflow in workbench copy
691d099 typed model lab diagnostics schemas
7ae6d66 optimized assistant regime context lookup
f215cf7 extracted market regime quality summaries
3ee3ba9 removed duplicate regime analysis ui
9b10489 updated ml first verification docs
88029d3 extended assistant model review context
a11589e displayed attention robustness review
7bc5bf4 added regime robustness summaries
4d4ff4f surfaced model lab in the workbench
ea71d1c proxied model lab diagnostics through backend
```

## 11. Verification Matrix

Use this matrix after implementation waves:

```bash
cd backend && ./mvnw test
cd ml-service && ../.venv/bin/python -m pytest
cd frontend && npm run test
cd frontend && npm run build
python3 -m unittest scripts/smoke_demo_test.py
docker compose config
python3 scripts/smoke_demo.py --timeout-seconds 30
```

For focused promoted artifact lifecycle work:

```bash
cd ml-service
../.venv/bin/python -m pytest tests/test_train_market_regime_model.py tests/test_market_regime_torch_model.py
```

The running stack smoke demo requires Docker and local service access. Docker backed persistence tests may be skipped when the Docker socket is unavailable.

## 12. Assumptions

- The canonical implementation plan is this file: `IMPLEMENTATION_PLAN.md`.
- The earlier follow-up planning content has been merged here and should not be kept as a second source of truth.
- Ignored local progress trackers are working notes only; public progress belongs in this plan and project docs.
- Traditional indicators and rule based methods remain useful baselines.
- The attention based market regime path is the main product direction.
- Default local setup remains CPU safe.
- Optional torch artifacts and model outputs stay local unless explicitly curated for documentation.
- Generated model artifacts are research outputs, not deployment approvals.
