# SignalAttention Implementation Plan Part 2

## 1. Project Repositioning

SignalAttention started as a local trading research sandbox with a practical backend first foundation. That first version was the right place to begin. It proved that the project could import real candle data, store it cleanly, run repeatable backtests, simulate paper trading, call a separate ML service, and leave behind an audit trail that explains what happened.

Part 2 changes the center of gravity.

The attention based market regime model should no longer feel like an optional experiment sitting beside the main app. It should become the main engine of the product. Traditional indicators, SMA crossover rules, deterministic risk scoring, and rule based regime labels should remain in the project, but their role changes. They become baselines, references, and sanity checks that help explain whether the learned model is doing anything useful.

The new pitch is:

SignalAttention evaluates trading strategy robustness across ML inferred market regimes using an attention based sequence model, with backtesting, paper simulation, audit trails, and baseline comparison built around it.

This still does not make the app a trading bot. It does not connect to brokers, place real orders, manage money, or give investment advice. The goal is to show how a strategy behaves when market context is inferred from candle sequences, then make that behavior inspectable enough that a user can learn from it.

## 2. What I Learned From Part 1

The original implementation plan intentionally avoided starting with the flashiest ML idea. That was useful. Building the backend, persistence model, API boundaries, and demo flow first made the ML work easier to trust later.

I learned that a trading ML project needs structure before it needs ambition. If the app cannot import data reproducibly, run the same backtest twice, persist the result, and explain what changed, then a model score is just decoration.

I also learned that simple methods are not a weakness. The rule based classifier and SMA strategy are valuable because they give the attention model something to compare against. A learned model is more convincing when the project can say what the baseline did, where the model improved, and where it did not.

The optional torch path also clarified what a credible ML workflow needs. A model artifact should not just appear in a folder. It needs training configuration, evaluation output, a manifest, promotion criteria, and a model card. Those pieces make the project feel more honest and easier to explain.

The frontend taught another lesson. A dashboard should not hide the model behind one score. It should show the regime timeline, confidence, model version, reasons, and the effect those regimes had on strategy behavior.

Paper trading also became more useful than expected. It is not there to imitate live trading. It is there to let a user replay decisions and understand how a strategy behaves as candle windows move through different regimes.

## 3. Updated Technology Stack

The backend uses Java 21, Spring Boot 3, Spring Web, Spring Data JPA, Hibernate, Jakarta Bean Validation, Springdoc OpenAPI with Swagger UI, Maven, JUnit 5, Mockito, Testcontainers, the PostgreSQL driver, and Flyway.

The ML service uses Python, FastAPI, Pydantic, pandas, NumPy, PyTorch, a lightweight Transformer encoder, sinusoidal positional encoding, local pt model artifacts, JSON model manifests, a local experiment registry, pytest, and optional scikit learn for classical baselines and future anomaly experiments.

The data and infrastructure layer uses PostgreSQL 16, Docker Compose, local CSV candle data, environment variables, Git, GitHub, and optional NVIDIA GPU support for future local acceleration. The default path should remain able to run on a normal CPU.

The frontend uses React, TypeScript, Vite, Recharts, Vitest, Testing Library, and CSS.

The assistant layer should use a provider neutral backend adapter, a chat session API, a structured tool action schema, reviewable user confirmation, audit events, and a provider implementation that can later point to OpenAI, a local model, or another hosted model.

## 4. ML First Product Direction

The main workflow should become:

1. Import candle data.

2. Train, evaluate, promote, or load an attention based market regime model.

3. Run rolling inference across candle windows.

4. Store regime predictions with confidence, model provenance, feature version, artifact identity, and inference time.

5. Run strategy backtests against the same candle range.

6. Break strategy performance down by inferred regime.

7. Compare the attention model against the rule based baseline.

8. Explain where the strategy looks robust, fragile, overfit, or dependent on a specific market state.

The default demo path should load a promoted local artifact when it exists. Training should remain visible and documented, but the normal demo should not require training the model every time. That keeps the app reproducible while still making the ML workflow central.

The rule based classifier should remain available as the baseline. Its output should be shown next to the attention model where useful, especially in evaluation views, model diagnostics, and regime replay screens.

## 5. Backend Plan

The backend should treat ML regime inference as a first class part of the application.

Add persistence for model artifact metadata, regime prediction records, regime run history, and backtest performance grouped by inferred regime. Each record should preserve enough provenance to answer which model produced the prediction, which feature version was used, which candle window was analyzed, and when inference happened.

The existing strategy, market data, backtest, paper trading, risk, dashboard, and audit APIs should keep working. New APIs should add ML centered workflows instead of replacing the current ones.

The backend should expose endpoints for model status, stored regime runs, regime grouped backtest analysis, baseline comparison, and assistant sessions. The dashboard should be able to ask the backend for a complete picture of one strategy assessment without reconstructing everything in the browser.

Audit events should be expanded so model inference, model comparison, assistant proposed actions, assistant confirmations, paper replay actions, and important analysis runs are recorded clearly.

## 6. ML Service Plan

The ML service should promote torch mode from optional experiment to the primary inference path.

The service should load the promoted artifact by default when one is available. Every market regime response should include model provenance, including mode, model version, feature version, sequence length, artifact identifier, and classifier source.

The rule based regime classifier should stay in the service as a baseline. The baseline is not a failure path only. It is part of the ML story because it gives the learned model something concrete to beat or disagree with.

The training, evaluation, diagnostics, promotion, and model card scripts should become part of the official workflow. The project should keep being honest that labels derived from rules are weak labels, not independent ground truth.

Evaluation should continue to report accuracy, per label metrics, confusion matrix, confidence summary, majority class baseline, and lift over baseline. Promotion should require holdout evaluation, acceptable lift, artifact hash, and a generated model card.

## 7. Assistant Plan

Add an assistant interface that helps the user understand and operate the research workflow. The assistant should sit on the side of the dashboard on desktop and open as a bottom panel on smaller screens.

The assistant should explain and orchestrate, not silently mutate state.

It should be able to answer questions such as:

1. What regime does the model think this market is in?

2. How confident is the model?

3. Where does the attention model disagree with the baseline?

4. Why did this backtest lose money in one regime but not another?

5. What happened during this paper replay?

6. What should I test next if I want to understand this strategy better?

The assistant should also propose actions such as importing sample data, running a backtest, starting a paper session, replaying candles, refreshing a regime run, or comparing baseline and attention outputs.

Those actions must be reviewable. The assistant can prepare the action, but the user confirms before execution. The backend should record the prompt, response summary, proposed action, confirmation state, execution result, and audit event.

The assistant should avoid investment advice language. It can explain risk, uncertainty, model limitations, and simulation results. It should not tell the user what to buy or sell.

## 8. Paper Testing Plan

Paper testing should become more connected to ML regimes.

A user should be able to create a paper session from a strategy, replay candles through it, and see which inferred regimes were active during the replay. Orders, positions, session summary, drawdown, and risk decisions should be shown beside regime context.

The assistant should make paper testing easier by offering guided actions. For example, it can propose replaying a date range where the model saw high volatility, or comparing a session during a trending regime against a session during a ranging regime.

The important point is learning. Paper testing should help the user understand strategy behavior under different model inferred market states, not suggest that the simulated result will repeat in real markets.

## 9. UI Plan

UI refinement should come at the end of this phase.

The first priority is to make the ML workflow real. The interface can be functional at first as long as it clearly exposes the model status, regime output, baseline comparison, backtest result, and paper testing flow.

After the backend and ML workflows are working, refine the dashboard around the ML story.

The final dashboard should include:

1. A model status area that shows whether the promoted attention artifact is loaded.

2. A price chart with a regime timeline beneath it.

3. A confidence and explanation panel for the current regime.

4. A baseline versus attention comparison view.

5. Strategy performance grouped by regime.

6. Paper testing controls connected to regime context.

7. An assistant panel on the side for desktop and a bottom panel for smaller screens.

8. Cleaner spacing, hierarchy, responsive behavior, and chart readability after the workflows are complete.

The dashboard should feel like an ML workbench. It should not feel like a generic trading terminal or a marketing page.

## 10. Success Criteria

Part 2 is complete when:

1. The project documentation presents SignalAttention as an ML centered strategy research workbench.

2. A promoted attention model artifact can be loaded for the main demo.

3. The backend can persist and retrieve regime prediction history.

4. A backtest can be analyzed by model inferred regime.

5. The rule based classifier is visible as a baseline and reference system.

6. The frontend makes model status, regime timeline, confidence, and baseline comparison visible.

7. The assistant can explain app state and propose reviewable paper testing actions.

8. Assistant actions are confirmed before execution and recorded through audit events.

9. The demo remains local, reproducible, and clear about not giving investment advice.

## 11. Implementation Order

1. Create this Part 2 implementation plan.

2. Update the README later so the project identity matches the new ML centered direction.

3. Promote the ML artifact workflow and make the promoted artifact the default demo target.

4. Add backend persistence and APIs for model runs, prediction history, and regime grouped backtest analysis.

5. Add the provider neutral assistant backend contract and reviewable action flow.

6. Add frontend surfaces for ML status, regime timelines, baseline comparison, and assistant interaction.

7. Refine the UI last after the workflows are working.

## 12. Test Plan

Run the backend Maven test suite from the backend folder.

Run the Python pytest suite from the ML service folder.

Run the frontend Vitest suite from the frontend folder.

Run the full local stack with Docker Compose.

Run the smoke demo script after the stack is available.

Add new tests for model provenance, regime prediction persistence, baseline comparison, assistant action confirmation, assistant audit events, and frontend rendering of the ML first workflow.

## 13. Assumptions

The new plan lives at the repo root as `IMPLEMENTATION_PLAN_PART_2.md`.

The LLM assistant is provider neutral in the plan.

The default ML demo loads a promoted artifact instead of training every time.

Traditional indicators and rule based methods stay in the project as baselines and references.

UI polish comes last, after the ML workflow and assistant behavior are implemented.

The document avoids dash characters so the writing matches the requested style.

## 14. Next Implementation Wave

The next wave makes the market regime workflow real as an ML first product path before adding the assistant layer.

Planned commit sized work:

1. Document the ML first regime wave and track progress locally.

2. Add model status to the ML service so the app can explain whether rules, auto, or torch mode is active.

3. Expose model status through the backend.

4. Persist regime run history and prediction points.

5. Save regime replay results and expose retrieval endpoints.

6. Compare the primary classifier against the rule based baseline.

7. Add backtest performance analysis grouped by inferred regime.

8. Surface model status, saved runs, baseline disagreement, and regime grouped backtest results in the dashboard.

9. Extend the smoke workflow and verification docs around the ML first regime path.

This wave deliberately keeps the default local setup CPU safe. Auto mode may use a promoted torch artifact when one is configured and loadable, but a fresh clone must still run with the rule based classifier.

## 15. Assistant Orchestration Wave

The next wave adds the provider neutral assistant layer after the ML first regime workflow is in place.

The assistant should explain current research state, propose reviewable actions, and require explicit confirmation before any state changing workflow runs. The first provider should be local and deterministic so the app stays reproducible without external LLM credentials.

Planned commit sized work:

1. Document the assistant orchestration wave and track progress locally.

2. Add assistant persistence for sessions, messages, proposed actions, statuses, payloads, execution results, and confirmation timestamps.

3. Add a provider neutral assistant contract with a local provider that can summarize dashboard, regime, backtest, and paper testing state.

4. Add reviewable action proposal APIs for backtests, regime replay, paper session start, and paper replay.

5. Add confirmation and rejection APIs that execute only whitelisted actions and record audit events.

6. Surface an assistant panel in the React workbench with message history, proposed actions, confirmation controls, and action refresh behavior.

7. Extend smoke and verification docs around the assistant action flow.

8. Verify the assistant orchestration wave and record results locally.

This wave does not add external LLM configuration, auth, broker connections, live trading, or investment advice.
