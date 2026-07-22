import { FormEvent, useState } from "react";
import { ChartState } from "../ChartShell";
import { AssistantAction, AssistantSession } from "../api/assistant";
import { AttentionShowcaseSummary } from "../api/attentionShowcase";
import {
  MarketRegimeDiagnostics,
  MarketRegimeExperimentDiagnostics,
  MarketRegimeStatus,
  RegimeEvidenceSnapshot,
  RegimeRobustnessSummary,
  RegimeRunComparison,
  RegimeRunResponse,
  RegimeRunSummary,
} from "../api/marketRegime";
import { Strategy } from "../api/strategies";
import {
  formatAction,
  formatComparisonDelta,
  formatCurrency,
  formatDateTime,
  formatNumber,
  formatPercent,
  formatRatioPercent,
  promotionValue,
  shortModelLabel,
} from "./format";
import { PanelMessage, ResultGrid } from "./presentation";
import { LoadState, WorkbenchAction, WorkbenchActionId } from "./types";

export function AttentionShowcasePanel({ state }: { state: LoadState<AttentionShowcaseSummary> }) {
  if (state.status === "loading") {
    return (
      <section className="panel showcase-panel" aria-label="Attention showcase readiness">
        <ChartState title="Loading showcase summary" message="Checking model status and latest replay evidence." />
      </section>
    );
  }
  if (state.status === "error") {
    return (
      <section className="panel showcase-panel" aria-label="Attention showcase readiness">
        <PanelMessage tone="error" title="Showcase summary unavailable" message={state.error} />
      </section>
    );
  }

  const summary = state.data;
  const latestRun = summary.latestRun;
  return (
    <section className="panel showcase-panel" aria-label="Attention showcase readiness">
      <div className="panel-heading">
        <div>
          <h2>Attention showcase readiness</h2>
          <p>{summary.nextAction}</p>
        </div>
        <span className={`status-pill status-${summary.modelReady ? "ready" : "review"}`}>
          {summary.modelReady ? "model ready" : "check model"}
        </span>
      </div>
      <ResultGrid
        items={[
          ["Mode", summary.effectiveMode || summary.classifierSource || "unknown"],
          ["Promotion", summary.promotionStatus || "not promoted"],
          ["Latest replay", latestRun ? `#${latestRun.id} ${latestRun.symbol} ${latestRun.timeframe}` : "none"],
          ["Robustness", formatAction(summary.robustnessLabel)],
          ["Evidence snapshots", summary.evidenceSnapshotCount],
          ["Baseline gaps", `${summary.disagreementSummary.disagreementCount}/${summary.disagreementSummary.totalWindows}`],
        ]}
      />
      {latestRun?.qualitySummary ? (
        <p className="muted">
          Latest replay averaged {formatPercent(latestRun.qualitySummary.averageConfidence)} confidence with{" "}
          {formatPercent(summary.disagreementSummary.disagreementRate)} baseline disagreement.
        </p>
      ) : (
        <p className="muted">Run a replay to connect model readiness with saved attention evidence.</p>
      )}
      {summary.disagreementSummary.lowestConfidenceWindows.length ? (
        <div className="mini-list" aria-label="Lowest confidence disagreement windows">
          {summary.disagreementSummary.lowestConfidenceWindows.map((window) => (
            <article key={window.windowEnd}>
              <span>{new Date(window.windowEnd).toLocaleString()}</span>
              <strong>
                {formatAction(window.regimeLabel)} vs {window.baselineRegimeLabel ? formatAction(window.baselineRegimeLabel) : "baseline n/a"}
              </strong>
              <p>{formatPercent(window.confidence)} confidence{window.anomalyLabel ? `, ${formatAction(window.anomalyLabel)}` : ""}</p>
            </article>
          ))}
        </div>
      ) : null}
      {summary.warnings.length ? <p className="muted">{summary.warnings.join(" ")}</p> : null}
    </section>
  );
}

export function AssistantPanel({
  busy,
  prompt,
  session,
  onConfirm,
  onPrompt,
  onReject,
  onSubmit,
}: {
  busy: boolean;
  prompt: string;
  session: AssistantSession | null;
  onConfirm: (action: AssistantAction) => void;
  onPrompt: (prompt: string) => void;
  onReject: (action: AssistantAction) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  // Suggestions stay separate from messages because nothing should run until the user confirms it here.
  const proposedActions = session?.actions.filter((action) => action.status === "PROPOSED") ?? [];
  return (
    <section className="panel assistant-panel" aria-label="Research assistant">
      <div className="panel-heading">
        <div>
          <h2>Assistant</h2>
          <p>Explain state and prepare reviewable research actions.</p>
        </div>
      </div>
      <form className="assistant-form" onSubmit={onSubmit}>
        <label>
          Prompt
          <textarea
            value={prompt}
            onChange={(event) => onPrompt(event.target.value)}
            placeholder="Ask about regimes, backtests, or paper replay"
            rows={3}
          />
        </label>
        <button className="button" disabled={busy || prompt.trim().length === 0} type="submit">
          {busy ? "Working" : "Send"}
        </button>
      </form>
      <div className="assistant-messages" aria-label="Assistant messages">
        {session?.messages.length ? (
          session.messages.map((message) => (
            <article className={`assistant-message assistant-message-${message.role.toLowerCase()}`} key={message.id}>
              <span>{message.role === "USER" ? "You" : "Assistant"}</span>
              <p>{message.content}</p>
            </article>
          ))
        ) : (
          <p className="muted">Ask for a regime replay, backtest check, or paper replay suggestion.</p>
        )}
      </div>
      {proposedActions.length ? (
        <div className="assistant-actions" aria-label="Proposed assistant actions">
          {proposedActions.map((action) => (
            <article className="assistant-action" key={action.id}>
              <span>{formatAction(action.actionType)}</span>
              <strong>{action.summary}</strong>
              <p>{assistantPayloadSummary(action)}</p>
              <div className="button-row">
                <button className="button" disabled={busy} onClick={() => onConfirm(action)} type="button">
                  Confirm
                </button>
                <button className="button button-secondary" disabled={busy} onClick={() => onReject(action)} type="button">
                  Reject
                </button>
              </div>
            </article>
          ))}
        </div>
      ) : null}
    </section>
  );
}

export function RegimeReplayPanel({
  busy,
  diagnostics,
  evidenceSnapshots,
  experimentsState,
  comparisonState,
  replay,
  robustness,
  runsState,
  selectedWindowEnd,
  selectedStrategy,
  statusState,
  onReplay,
  onSelectWindow,
}: {
  busy: boolean;
  diagnostics: MarketRegimeDiagnostics | null;
  evidenceSnapshots: RegimeEvidenceSnapshot[];
  experimentsState: LoadState<MarketRegimeExperimentDiagnostics>;
  comparisonState: LoadState<RegimeRunComparison>;
  replay: RegimeRunResponse | null;
  robustness: RegimeRobustnessSummary | null;
  runsState: LoadState<RegimeRunSummary[]>;
  selectedWindowEnd: string | null;
  selectedStrategy: Strategy | null;
  statusState: LoadState<MarketRegimeStatus>;
  onReplay: () => void;
  onSelectWindow: (point: RegimeRunResponse["points"][number]) => void;
}) {
  return (
    <section className="panel analysis-panel" id="analysis">
      <div className="panel-heading">
        <div>
          <h2>Attention regime replay</h2>
          <p>{selectedStrategy ? `${selectedStrategy.symbol} ${selectedStrategy.timeframe}` : "Select a baseline strategy first."}</p>
        </div>
        <button className="button" disabled={busy || !selectedStrategy} onClick={onReplay} type="button">
          {busy ? "Loading" : "Run replay"}
        </button>
      </div>
      <ModelStatusStrip state={statusState} />
      <ModelLabPanel state={experimentsState} />
      {replay ? (
        <>
          <ResultGrid
            items={[
              ["Run", `#${replay.id}`],
              ["Mode", replay.effectiveMode || replay.classifierSource || "unknown"],
              ["Windows", replay.pointCount],
              ["Baseline gaps", replay.points.filter((point) => point.disagreesWithBaseline).length],
            ]}
          />
          <CandlestickReplayChart replay={replay} />
          <RegimeWindowTable
            points={replay.points}
            selectedWindowEnd={selectedWindowEnd}
            onSelectWindow={onSelectWindow}
          />
          <AttentionEvidencePanel diagnostics={diagnostics} snapshots={evidenceSnapshots} />
          <RegimeRobustnessPanel robustness={robustness} />
        </>
      ) : (
        <ChartState title="No assessment chart yet" message="Run replay after selecting a strategy and date range." />
      )}
      <RegimeRunComparisonTable comparisonState={comparisonState} runsState={runsState} />
    </section>
  );
}

export function RegimeWindowTable({
  points,
  selectedWindowEnd,
  onSelectWindow,
}: {
  points: RegimeRunResponse["points"];
  selectedWindowEnd: string | null;
  onSelectWindow: (point: RegimeRunResponse["points"][number]) => void;
}) {
  // Eight recent windows are enough to browse without turning the review panel into another run history table.
  const rows = points.slice(-8).reverse();
  return (
    <div className="table-scroll">
      <table className="data-table regime-window-table" aria-label="Selectable regime replay windows">
        <thead>
          <tr>
            <th>Window end</th>
            <th>Regime</th>
            <th>Confidence</th>
            <th>Baseline</th>
            <th>Anomaly</th>
            <th>Evidence</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((point) => (
            <tr className={point.windowEnd === selectedWindowEnd ? "selected-row" : undefined} key={point.windowEnd}>
              <td>{formatDateTime(point.windowEnd)}</td>
              <td>{formatAction(point.regimeLabel)}</td>
              <td>{formatPercent(point.confidence)}</td>
              <td>{point.disagreesWithBaseline ? "disagrees" : "aligned"}</td>
              <td>{point.anomalyLabel ? formatAction(point.anomalyLabel) : "none"}</td>
              <td>
                <button className="text-button" onClick={() => onSelectWindow(point)} type="button">
                  Inspect
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function ModelLabPanel({ state }: { state: LoadState<MarketRegimeExperimentDiagnostics> }) {
  if (state.status === "loading") {
    return <p className="muted">Loading model lab diagnostics.</p>;
  }
  if (state.status === "error") {
    return <p className="error-text">{state.error}</p>;
  }
  const diagnostics = state.data;
  const bestRun = diagnostics.summary.bestRun ?? diagnostics.runs[0] ?? null;
  const promotionStatus = promotionValue(diagnostics.promotion, "status") ?? "not promoted";
  const selectedRun = promotionValue(diagnostics.promotion, "selectedRun.runId");
  const forwardOutcomes = bestRun?.forwardOutcomeSummary;
  return (
    <div className="model-lab-panel" aria-label="Model lab diagnostics">
      <div>
        <h3>Model lab</h3>
        <p className="muted">Local experiment registry review; promotion is a research candidate, not deployment approval.</p>
      </div>
      <ResultGrid
        items={[
          ["Runs", diagnostics.summary.totalRuns],
          ["Evaluated", diagnostics.summary.evaluatedRuns],
          ["Eligible", diagnostics.summary.promotionEligibleRuns],
          ["Promotion", selectedRun ? `${promotionStatus} (${selectedRun})` : promotionStatus],
        ]}
      />
      {bestRun ? (
        <div className="model-lab-grid">
          <div>
            <h4>Best run</h4>
            <ResultGrid
              items={[
                ["Run", bestRun.runId || "unknown"],
                ["Accuracy", formatRatioPercent(bestRun.accuracy)],
                ["Macro F1", formatRatioPercent(bestRun.macroF1)],
                ["Balanced accuracy", formatRatioPercent(bestRun.balancedAccuracy)],
                ["Lift", formatRatioPercent(bestRun.liftOverBaseline)],
                ["Holdout", bestRun.holdoutSource || "legacy or full"],
                ["Gate", bestRun.promotionGate?.eligible ? "eligible" : "needs review"],
              ]}
            />
            {bestRun.promotionGate?.failures?.length ? (
              <ul className="compact-list">
                {bestRun.promotionGate.failures.slice(0, 3).map((failure) => (
                  <li key={failure}>{failure}</li>
                ))}
              </ul>
            ) : null}
          </div>
          <div>
            <h4>Review signals</h4>
            {bestRun.weakestLabels?.length ? (
              <ul className="compact-list">
                {bestRun.weakestLabels.slice(0, 3).map((label) => (
                  <li key={label.label ?? "unknown"}>
                    <span>{formatAction(label.label ?? "unknown")}</span>
                    <strong>f1 {formatRatioPercent(label.f1)}</strong>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="muted">No weak-label diagnostics recorded.</p>
            )}
            {bestRun.confusionPairs?.length ? (
              <ul className="compact-list">
                {bestRun.confusionPairs.slice(0, 3).map((pair) => (
                  <li key={`${pair.expected}-${pair.predicted}`}>
                    {formatAction(pair.expected ?? "unknown")} to {formatAction(pair.predicted ?? "unknown")} ({pair.count ?? 0})
                  </li>
                ))}
              </ul>
            ) : null}
            {forwardOutcomes ? (
              <>
                <h4>Forward outcomes</h4>
                <ResultGrid
                  items={[
                    ["Horizon", `${forwardOutcomes.horizonCandles ?? 0} candles`],
                    ["Windows", forwardOutcomes.eligibleWindowCount ?? 0],
                    [
                      "Highest volatility",
                      formatForwardOutcome(
                        forwardOutcomes.highestForwardVolatility?.label,
                        forwardOutcomes.highestForwardVolatility?.meanRealizedVolatilityPercent,
                      ),
                    ],
                    [
                      "Largest move",
                      formatForwardOutcome(
                        forwardOutcomes.strongestAverageAbsoluteMove?.label,
                        forwardOutcomes.strongestAverageAbsoluteMove?.meanAbsoluteForwardReturnPercent,
                      ),
                    ],
                  ]}
                />
                <p className="muted">Observed after each prediction; this is context, not a profit score.</p>
              </>
            ) : null}
          </div>
        </div>
      ) : (
        <p className="muted">No experiments recorded yet. Use the local training and evaluation scripts to populate the registry.</p>
      )}
      {diagnostics.warnings.length ? (
        <ul className="compact-list">
          {diagnostics.warnings.slice(0, 4).map((warning) => (
            <li key={warning}>{warning}</li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}

function formatForwardOutcome(label: string | null | undefined, value: number | null | undefined) {
  if (!label || value === null || value === undefined) {
    return "not recorded";
  }
  return `${formatAction(label)} (${formatNumber(value)}%)`;
}

function RegimeRunComparisonTable({
  comparisonState,
  runsState,
}: {
  comparisonState: LoadState<RegimeRunComparison>;
  runsState: LoadState<RegimeRunSummary[]>;
}) {
  if (comparisonState.status === "loading" || runsState.status === "loading") {
    return <p className="muted">Loading saved regime runs.</p>;
  }
  if (comparisonState.status === "error") {
    return <p className="error-text">{comparisonState.error}</p>;
  }
  if (comparisonState.data.runs.length === 0) {
    return <p className="muted">No saved regime runs yet.</p>;
  }
  return (
    <table className="data-table regime-comparison-table" aria-label="Regime run comparison">
      <thead>
        <tr>
          <th>Run</th>
          <th>Mode</th>
          <th>Model</th>
          <th>Windows</th>
          <th>Avg conf</th>
          <th>Baseline gap</th>
          <th>Dominant</th>
          <th>Anomalies</th>
          <th>Delta</th>
        </tr>
      </thead>
      <tbody>
        {comparisonState.data.runs.slice(0, 5).map(({ run, deltaFromPrevious }) => {
          const summary = run.qualitySummary;
          return (
            <tr key={run.id}>
              <td>#{run.id}</td>
              <td>{run.effectiveMode || run.classifierSource || "unknown"}</td>
              <td>{shortModelLabel(run)}</td>
              <td>{run.pointCount}</td>
              <td>{formatPercent(summary?.averageConfidence)}</td>
              <td>{formatPercent(summary?.baselineDisagreementRate)}</td>
              <td>{summary?.dominantRegimeLabel ? formatAction(summary.dominantRegimeLabel) : "n/a"}</td>
              <td>{summary?.anomalyCount ?? 0}</td>
              <td>{formatComparisonDelta(deltaFromPrevious)}</td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}

export function AttentionEvidencePanel({
  diagnostics,
  snapshots,
}: {
  diagnostics: MarketRegimeDiagnostics | null;
  snapshots: RegimeEvidenceSnapshot[];
}) {
  const latestSnapshot = snapshots[0];
  return (
    <div className="evidence-panel" aria-label="Attention evidence">
      <div>
        <h3>Attention evidence</h3>
        <p className="muted">
          {diagnostics
            ? `${formatAction(diagnostics.regimeLabel)} vs ${formatAction(diagnostics.baselineRegimeLabel)}`
            : latestSnapshot
              ? `Latest saved snapshot: ${formatAction(latestSnapshot.regimeLabel)}`
              : "Run replay to inspect the latest model evidence."}
        </p>
      </div>
      {diagnostics ? (
        <>
          <ResultGrid
            items={[
              ["Evidence", diagnostics.evidenceSource],
              ["Confidence", formatPercent(diagnostics.confidence)],
              ["Baseline gap", diagnostics.disagreesWithBaseline ? "yes" : "no"],
              ["Model", diagnostics.modelVersion || diagnostics.classifierSource || "rules"],
            ]}
          />
          <div className="evidence-grid">
            <div>
              <h4>Top timesteps</h4>
              <ul className="compact-list">
                {diagnostics.topTimesteps.map((point) => (
                  <li key={point.openTime}>
                    <span>{formatDateTime(point.openTime)}</span>
                    <strong>{formatNumber(point.attentionScore)}</strong>
                  </li>
                ))}
              </ul>
            </div>
            <div>
              <h4>Feature evidence</h4>
              <ul className="compact-list">
                {diagnostics.featureEvidence.map((feature) => (
                  <li key={feature.name}>
                    <span>{formatAction(feature.name)}</span>
                    <strong>{formatNumber(feature.importance)}</strong>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </>
      ) : latestSnapshot ? (
        <ResultGrid
          items={[
            ["Evidence", latestSnapshot.evidenceSource],
            ["Confidence", formatPercent(latestSnapshot.confidence)],
            ["Baseline gap", latestSnapshot.disagreesWithBaseline ? "yes" : "no"],
            ["Saved", formatDateTime(latestSnapshot.createdAt)],
          ]}
        />
      ) : null}
    </div>
  );
}

function RegimeRobustnessPanel({ robustness }: { robustness: RegimeRobustnessSummary | null }) {
  if (!robustness) {
    return <p className="muted">Run replay to generate the attention robustness review.</p>;
  }
  const quality = robustness.qualitySummary;
  return (
    <div className="robustness-panel" aria-label="Attention robustness review">
      <div>
        <h3>Robustness review</h3>
        <p className="muted">Descriptive review of confidence, baseline gaps, anomalies, and optional backtest overlap.</p>
      </div>
      <ResultGrid
        items={[
          ["Review", formatAction(robustness.reviewLabel)],
          ["Confidence", formatPercent(quality.averageConfidence)],
          ["Baseline gap", formatPercent(quality.baselineDisagreementRate)],
          ["Anomalies", quality.anomalyCount],
        ]}
      />
      <ul className="compact-list">
        {robustness.reviewReasons.map((reason) => (
          <li key={reason}>{reason}</li>
        ))}
      </ul>
      {robustness.regimes.length ? (
        <table className="data-table">
          <thead>
            <tr>
              <th>Regime</th>
              <th>Trades</th>
              <th>Win rate</th>
              <th>Net PnL</th>
              <th>Baseline gaps</th>
            </tr>
          </thead>
          <tbody>
            {robustness.regimes.map((regime) => (
              <tr key={regime.regimeLabel}>
                <td>{formatAction(regime.regimeLabel)}</td>
                <td>{regime.tradeCount}</td>
                <td>{formatPercent(regime.winRate)}</td>
                <td>{formatCurrency(regime.totalNetPnl)}</td>
                <td>{regime.baselineDisagreementCount}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}
    </div>
  );
}

function assistantPayloadSummary(action: AssistantAction) {
  try {
    const payload = JSON.parse(action.payloadJson) as Record<string, unknown>;
    const ids = ["strategyId", "backtestId", "paperSessionId"]
      .filter((key) => payload[key] !== undefined)
      .map((key) => `${key}: ${payload[key]}`);
    return ids.length ? ids.join(" | ") : "Ready for review.";
  } catch {
    return "Payload preview unavailable.";
  }
}

export function assistantActionNotice(action: AssistantAction) {
  if (action.status === "EXECUTED") {
    return `${formatAction(action.actionType)} executed.`;
  }
  return action.failureMessage || `${formatAction(action.actionType)} failed.`;
}

export function ModelStatusStrip({ state }: { state: LoadState<MarketRegimeStatus> }) {
  if (state.status === "loading") {
    return <p className="muted">Loading model status.</p>;
  }
  if (state.status === "error") {
    return <p className="error-text">{state.error}</p>;
  }
  const status = state.data;
  // Rules mode is still a valid result since a fresh clone is supposed to work without a local artifact.
  const statusItems: Array<[string, string | number]> = [
    ["Requested", status.mode],
    ["Effective", `${status.effectiveMode} mode`],
    ["Ready", status.ready ? "yes" : "no"],
    ["Artifact", status.artifactName || (status.artifactExists ? "loaded" : "not loaded")],
  ];
  if (status.architecture) {
    statusItems.push(["Architecture", status.architecture]);
  }
  if (status.promotedRunId) {
    statusItems.push(["Promoted run", status.promotedRunId]);
  }
  if (status.promotionStatus) {
    statusItems.push(["Promotion", `${status.promotionStatus}${status.promotionArtifactMatches === false ? " (unverified)" : ""}`]);
  }
  const warnings = [...(status.warnings ?? []), ...(status.promotionWarnings ?? [])];
  return (
    <>
      <ResultGrid items={statusItems} />
      {warnings.length ? (
        <ul className="compact-list">
          {warnings.slice(0, 4).map((warning) => (
            <li key={warning}>{warning}</li>
          ))}
        </ul>
      ) : null}
    </>
  );
}

function SavedRegimeRuns({ state }: { state: LoadState<RegimeRunSummary[]> }) {
  if (state.status === "loading") {
    return <p className="muted">Loading saved regime runs.</p>;
  }
  if (state.status === "error") {
    return <p className="error-text">{state.error}</p>;
  }
  if (state.data.length === 0) {
    return <p className="muted">No saved regime runs yet.</p>;
  }
  return (
    <div className="mini-list">
      {state.data.slice(0, 3).map((run) => (
        <span key={run.id}>
          #{run.id} {run.effectiveMode || run.classifierSource || "unknown"} · {run.pointCount} windows
        </span>
      ))}
    </div>
  );
}

export function WorkbenchNav() {
  const links = [
    ["Overview", "#overview"],
    ["Data", "#data"],
    ["Baseline", "#strategy"],
    ["Compare", "#backtest"],
    ["Paper", "#paper"],
    ["Analysis", "#analysis"],
    ["Audit", "#audit"],
  ];
  return (
    <nav className="workbench-nav" aria-label="Workbench sections">
      {links.map(([label, href]) => (
        <a key={href} href={href}>
          {label}
        </a>
      ))}
    </nav>
  );
}

export function NextActionPanel({ action }: { action: WorkbenchAction }) {
  return (
    <section className="next-action-panel" aria-label="Next recommended action">
      <div>
        <span>Next action</span>
        <strong>{action.title}</strong>
        <p>{action.detail}</p>
      </div>
      <a className="button" href={nextActionTarget(action.id)}>
        Go
      </a>
    </section>
  );
}

function nextActionTarget(action: WorkbenchActionId) {
  // Most workflow actions live in the main control grid; analysis and review have dedicated anchors.
  if (action === "run-analysis") {
    return "#analysis";
  }
  if (action === "review-results") {
    return "#overview";
  }
  return "#workflow";
}

function CandlestickReplayChart({ replay }: { replay: RegimeRunResponse }) {
  const [selectedIndex, setSelectedIndex] = useState(0);
  if (!replay.candles.length) return null;
  const width = 900;
  const height = 280;
  const pad = { top: 18, right: 72, bottom: 34, left: 18 };
  // Price bounds come from high/low so every wick fits inside the chart area.
  const highs = replay.candles.map((c) => c.high);
  const lows = replay.candles.map((c) => c.low);
  const min = Math.min(...lows);
  const max = Math.max(...highs);
  const span = max - min || 1;
  // Map candle index and price into the fixed SVG coordinate space.
  const x = (i: number) => pad.left + (i / Math.max(1, replay.candles.length - 1)) * (width - pad.left - pad.right);
  const y = (p: number) => height - pad.bottom - ((p - min) / span) * (height - pad.top - pad.bottom);
  // Regime points are keyed by window end time so markers align with candles.
  const midByTime = new Map(replay.points.map((p) => [p.windowEnd, p]));
  const colorFor = (label: string) => (label.includes("DOWN") ? "#cc3b3b" : label.includes("SIDE") ? "#9f7a28" : "#2a8f54");
  const firstCandle = replay.candles[0];
  const lastCandle = replay.candles[replay.candles.length - 1];
  const priceTicks = [max, min + span / 2, min];
  const selectedCandle = replay.candles[Math.min(selectedIndex, replay.candles.length - 1)];
  const selectedRegime = midByTime.get(selectedCandle.openTime);

  return (
    <div className="series-card">
      <svg className="series-chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Regime replay candlestick chart">
        {priceTicks.map((tick) => (
          <g key={tick.toFixed(6)}>
            <line x1={pad.left} x2={width - pad.right} y1={y(tick)} y2={y(tick)} className="chart-grid-line" />
            <text x={width - pad.right + 10} y={y(tick) + 4} className="chart-axis-label">
              {formatCurrency(tick)}
            </text>
          </g>
        ))}
        {replay.candles.map((c, i) => {
          const wickX = x(i);
          const o = y(c.openPrice);
          const cl = y(c.close);
          const hi = y(c.high);
          const lo = y(c.low);
          const up = c.close >= c.openPrice;
          const point = midByTime.get(c.openTime);
          return (
            <g
              key={c.openTime}
              className="candle-hit-target"
              role="button"
              tabIndex={0}
              aria-label={`${formatDateTime(c.openTime)} open ${formatCurrency(c.openPrice)} high ${formatCurrency(c.high)} low ${formatCurrency(c.low)} close ${formatCurrency(c.close)}`}
              onBlur={() => setSelectedIndex(i)}
              onFocus={() => setSelectedIndex(i)}
              onMouseEnter={() => setSelectedIndex(i)}
            >
              {/* The visible candle is tiny, so this larger invisible target makes inspection much less fiddly. */}
              <rect x={wickX - 6} y={pad.top} width="12" height={height - pad.top - pad.bottom} fill="transparent" />
              <line x1={wickX} x2={wickX} y1={hi} y2={lo} stroke="#7c8796" strokeWidth="1" />
              <rect
                x={wickX - 2.5}
                y={Math.min(o, cl)}
                width="5"
                height={Math.max(1, Math.abs(cl - o))}
                fill={up ? "#28a745" : "#d73a49"}
                stroke={selectedIndex === i ? "#111827" : "transparent"}
                strokeWidth="1.5"
              />
              {point ? (
                <circle aria-label={`${point.regimeLabel} regime marker`} cx={wickX} cy={y(c.high) - 6} r="2.5" fill={colorFor(point.regimeLabel)} />
              ) : null}
            </g>
          );
        })}
        {replay.tradeMarkers.map((t) => {
          const idx = replay.candles.findIndex((c) => c.openTime === t.entryTime);
          if (idx < 0) return null;
          const markerX = x(idx);
          const markerY = y(t.entryPrice);
          return (
            <g key={t.tradeId} aria-label={`${t.side.toLowerCase()} trade marker`}>
              {t.side === "BUY" ? (
                <path d={`M ${markerX} ${markerY - 6} L ${markerX + 5} ${markerY + 4} L ${markerX - 5} ${markerY + 4} Z`} fill="#2d6cdf" />
              ) : (
                <path d={`M ${markerX} ${markerY + 6} L ${markerX + 5} ${markerY - 4} L ${markerX - 5} ${markerY - 4} Z`} fill="#e65a00" />
              )}
            </g>
          );
        })}
      </svg>
      <div className="chart-legend" aria-label="Candlestick chart legend">
        <span><i className="legend-swatch legend-up" /> Up candle</span>
        <span><i className="legend-swatch legend-down" /> Down candle</span>
        <span><i className="legend-dot legend-regime" /> Regime</span>
        <span><i className="legend-triangle legend-buy" /> Buy</span>
        <span><i className="legend-triangle legend-sell" /> Sell</span>
      </div>
      <div className="series-meta">
        <span>{formatDateTime(firstCandle.openTime)}</span>
        <span>{formatDateTime(lastCandle.openTime)}</span>
      </div>
      <div className="candle-detail" aria-live="polite">
        <span>{formatDateTime(selectedCandle.openTime)}</span>
        <span>O {formatCurrency(selectedCandle.openPrice)}</span>
        <span>H {formatCurrency(selectedCandle.high)}</span>
        <span>L {formatCurrency(selectedCandle.low)}</span>
        <span>C {formatCurrency(selectedCandle.close)}</span>
        <strong>{selectedRegime?.regimeLabel ? formatAction(selectedRegime.regimeLabel) : "no regime window"}</strong>
      </div>
    </div>
  );
}
