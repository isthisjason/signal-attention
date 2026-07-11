import { Bar, BarChart, CartesianGrid, Tooltip, XAxis, YAxis } from "recharts";
import { ChartShell, ChartState } from "../ChartShell";
import { AnomalyResponse } from "../api/anomaly";
import { AuditEvent } from "../api/audit";
import { DashboardRiskAlert, DashboardSummary, StrategyPerformance } from "../api/dashboard";
import { MarketRegimeFeatures, MarketRegimeResponse } from "../api/marketRegime";
import { formatAction, formatCurrency, formatDateTime, formatPercent } from "./format";
import { PanelMessage, ResultGrid, serviceErrorMessage } from "./presentation";
import { LoadState } from "./types";

export function SummaryCards({ state }: { state: LoadState<DashboardSummary> }) {
  if (state.status === "loading") {
    return (
      <section className="metric-grid">
        {["Strategies", "Backtests", "Paper sessions", "Latest backtest"].map((label) => (
          <article className="metric-card" key={label}>
            <span>{label}</span>
            <strong>Loading</strong>
          </article>
        ))}
      </section>
    );
  }
  if (state.status === "error") {
    return <PanelMessage title="Dashboard summary" tone="error" message={serviceErrorMessage(state.error, "backend")} />;
  }

  const latest = state.data.latestBacktest;

  return (
    <section className="metric-grid" aria-label="Dashboard summary">
      <article className="metric-card">
        <span>Strategies</span>
        <strong>{state.data.strategyCount}</strong>
      </article>
      <article className="metric-card">
        <span>Backtests</span>
        <strong>{state.data.backtestCount}</strong>
      </article>
      <article className="metric-card">
        <span>Running paper sessions</span>
        <strong>{state.data.activePaperSessionCount}</strong>
      </article>
      <article className="metric-card">
        <span>Latest backtest</span>
        <strong>{latest ? formatPercent(latest.totalReturn) : "None"}</strong>
        {latest ? <small>{latest.mlRiskLabel || "No ML score"}</small> : null}
      </article>
    </section>
  );
}

export function RiskAlertsPanel({ state }: { state: LoadState<DashboardRiskAlert[]> }) {
  if (state.status === "loading") {
    return <PanelMessage title="Risk alerts" message="Loading risk alerts." />;
  }
  if (state.status === "error") {
    return <PanelMessage title="Risk alerts" tone="error" message={serviceErrorMessage(state.error, "backend")} />;
  }
  if (state.data.length === 0) {
    return <PanelMessage title="Risk alerts" message="No risk alerts are active." />;
  }

  return (
    <section className="panel risk-alerts-panel">
      <h2>Risk alerts</h2>
      <div className="risk-alert-list">
        {state.data.map((alert) => (
          <article className="risk-alert" key={`${alert.category}-${alert.entityType}-${alert.entityId}-${alert.createdAt}`}>
            <span className={`severity-badge severity-${alert.severity.toLowerCase()}`}>{alert.severity.toLowerCase()}</span>
            <div>
              <strong>{formatAction(alert.category)}</strong>
              <p>{alert.message}</p>
              <small>
                {alert.entityType} #{alert.entityId} · {formatDateTime(alert.createdAt)}
              </small>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

export function StrategyTable({ state }: { state: LoadState<StrategyPerformance[]> }) {
  if (state.status === "loading") {
    return <PanelMessage title="Baseline performance" message="Loading baseline performance." />;
  }
  if (state.status === "error") {
    return <PanelMessage title="Baseline performance" tone="error" message={serviceErrorMessage(state.error, "backend")} />;
  }
  if (state.data.length === 0) {
    return <PanelMessage title="Baseline performance" message="No baselines have been created yet. Start by importing candles, then create an SMA baseline." />;
  }

  return (
    <section className="panel" id="audit">
      <h2>Baseline performance</h2>
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              <th>Baseline</th>
              <th>Market</th>
              <th>Status</th>
              <th>Return</th>
              <th>Drawdown</th>
              <th>Trades</th>
              <th>ML risk</th>
              <th>Paper sessions</th>
            </tr>
          </thead>
          <tbody>
            {state.data.map((strategy) => (
              <tr key={strategy.strategyId}>
                <td>
                  <strong>{strategy.name}</strong>
                  <small>#{strategy.strategyId}</small>
                </td>
                <td>
                  {strategy.symbol}
                  <small>{strategy.timeframe}</small>
                </td>
                <td>{strategy.status}</td>
                <td>{formatPercent(strategy.latestTotalReturn)}</td>
                <td>{formatPercent(strategy.latestMaxDrawdown)}</td>
                <td>{strategy.latestTradeCount ?? "N/A"}</td>
                <td>{strategy.latestMlRiskLabel || "Unscored"}</td>
                <td>{strategy.paperSessionCount}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

export function StrategyComparisonPanel({ state }: { state: LoadState<StrategyPerformance[]> }) {
  if (state.status !== "success" || state.data.length < 2) {
    return null;
  }

  const strategiesWithRuns = state.data.filter((strategy) => strategy.latestBacktestId !== null);
  if (strategiesWithRuns.length < 2) {
    return null;
  }

  const bestReturn = maxBy(strategiesWithRuns, (strategy) => strategy.latestTotalReturn ?? Number.NEGATIVE_INFINITY);
  const lowestDrawdown = minBy(strategiesWithRuns, (strategy) => strategy.latestMaxDrawdown ?? Number.POSITIVE_INFINITY);
  const mostTrades = maxBy(strategiesWithRuns, (strategy) => strategy.latestTradeCount ?? Number.NEGATIVE_INFINITY);

  return (
    <section className="panel">
      <h2>Baseline comparison</h2>
      <StrategyComparisonChart strategies={strategiesWithRuns} />
      <div className="comparison-grid">
        <ComparisonCard title="Best return" strategy={bestReturn} value={formatPercent(bestReturn.latestTotalReturn)} />
        <ComparisonCard title="Lowest drawdown" strategy={lowestDrawdown} value={formatPercent(lowestDrawdown.latestMaxDrawdown)} />
        <ComparisonCard title="Most trades" strategy={mostTrades} value={mostTrades.latestTradeCount ?? "N/A"} />
      </div>
    </section>
  );
}

function StrategyComparisonChart({ strategies }: { strategies: StrategyPerformance[] }) {
  const data = strategies.map((strategy) => ({
    name: strategy.name,
    return: strategy.latestTotalReturn ?? 0,
    drawdown: strategy.latestMaxDrawdown ?? 0,
    trades: strategy.latestTradeCount ?? 0,
  }));

  return (
    <ChartShell title="Baseline metrics" height={220}>
      <BarChart data={data} margin={{ top: 12, right: 12, bottom: 0, left: 0 }}>
        <CartesianGrid stroke="var(--border)" strokeDasharray="4 5" vertical={false} />
        <XAxis dataKey="name" tick={{ fontSize: 11 }} />
        <YAxis hide />
        <Tooltip formatter={(value, name) => name === "trades" ? Number(value).toFixed(0) : formatPercent(Number(value))} />
        <Bar dataKey="return" fill="#0f766e" name="return" radius={[4, 4, 0, 0]} />
        <Bar dataKey="drawdown" fill="#b42318" name="drawdown" radius={[4, 4, 0, 0]} />
        <Bar dataKey="trades" fill="#2d6cdf" name="trades" radius={[4, 4, 0, 0]} />
      </BarChart>
    </ChartShell>
  );
}

function ComparisonCard({
  title,
  strategy,
  value,
}: {
  title: string;
  strategy: StrategyPerformance;
  value: string | number;
}) {
  return (
    <article className="comparison-card">
      <span>{title}</span>
      <strong>{value}</strong>
      <p>{strategy.name}</p>
      <small>
        {strategy.symbol} {strategy.timeframe} · {strategy.latestMlRiskLabel || "unscored"}
      </small>
    </article>
  );
}

function maxBy<T>(items: T[], valueFor: (item: T) => number): T {
  return items.reduce((best, item) => (valueFor(item) > valueFor(best) ? item : best));
}

function minBy<T>(items: T[], valueFor: (item: T) => number): T {
  return items.reduce((best, item) => (valueFor(item) < valueFor(best) ? item : best));
}

export function AuditTimeline({ state }: { state: LoadState<AuditEvent[]> }) {
  if (state.status === "loading") {
    return <PanelMessage title="Audit events" message="Loading audit events." />;
  }
  if (state.status === "error") {
    return <PanelMessage title="Audit events" tone="error" message={serviceErrorMessage(state.error, "backend")} />;
  }
  if (state.data.length === 0) {
    return <PanelMessage title="Audit events" message="No audit events have been recorded yet." />;
  }

  return (
    <section className="panel">
      <h2>Audit events</h2>
      <ol className="timeline">
        {state.data.map((event) => (
          <li key={event.id}>
            <div>
              <strong>{formatAction(event.action)}</strong>
              <span>
                {event.entityType} #{event.entityId}
              </span>
            </div>
            <time dateTime={event.createdAt}>{formatDateTime(event.createdAt)}</time>
            <p>{event.message}</p>
          </li>
        ))}
      </ol>
    </section>
  );
}

export function MarketRegimePanel({ state }: { state: LoadState<MarketRegimeResponse> }) {
  if (state.status === "loading") {
    return <PanelMessage title="Attention regime" message="Loading BTC-USD 1h attention regime." />;
  }
  if (state.status === "error") {
    return (
      <PanelMessage
        title="Attention regime"
        tone="error"
        message={`${serviceErrorMessage(state.error, "ml")} Import at least 20 BTC-USD 1h candles before using this panel.`}
      />
    );
  }

  const { features } = state.data;
  const provenance = marketRegimeProvenanceItems(state.data);

  return (
    <section className="panel">
      <div className="panel-heading">
        <div>
          <h2>Attention regime</h2>
          <p>BTC-USD, 1h, latest 128 candles</p>
        </div>
        <div className="regime-badge">
          <strong>{formatAction(state.data.regimeLabel)}</strong>
          <span>{state.data.confidence.toFixed(2)}% confidence</span>
        </div>
      </div>
      <ul className="reason-list">
        {state.data.reasons.map((reason) => (
          <li key={reason}>{reason}</li>
        ))}
      </ul>
      <FeatureBarChart title="Regime features" features={features} />
      <div className="feature-grid">
        <Feature label="Latest return" value={features.latestReturnPercent} suffix="%" />
        <Feature label="Avg return" value={features.averageReturnPercent} suffix="%" />
        <Feature label="Volatility" value={features.volatilityPercent} suffix="%" />
        <Feature label="Trend slope" value={features.trendSlopePercent} suffix="%" />
        <Feature label="SMA distance" value={features.smaDistancePercent} suffix="%" />
        <Feature label="Volume z-score" value={features.volumeZScore} />
      </div>
      {provenance.length ? <ResultGrid items={provenance} /> : null}
    </section>
  );
}

export function AnomalyPanel({
  anomaly,
  busy,
  onCheck,
}: {
  anomaly: AnomalyResponse | null;
  busy: boolean;
  onCheck: () => void;
}) {
  return (
    <section className="panel">
      <div className="panel-heading">
        <div>
          <h2>Anomaly check</h2>
          <p>BTC-USD, 1h, latest 128 candles</p>
        </div>
        <button className="button" disabled={busy} onClick={onCheck} type="button">
          {busy ? "Checking" : "Check"}
        </button>
      </div>
      {anomaly ? (
        <>
          <ResultGrid
            items={[
              ["Label", formatAction(anomaly.anomalyLabel)],
              ["Score", anomaly.anomalyScore.toFixed(2)],
              ["Source", anomaly.classifierSource],
            ]}
          />
          <ul className="reason-list">
            {anomaly.reasons.map((reason) => (
              <li key={reason}>{reason}</li>
            ))}
          </ul>
          <FeatureBarChart title="Anomaly features" features={anomaly.features} />
          <div className="feature-grid">
            <Feature label="Latest return" value={anomaly.features.latestReturnPercent} suffix="%" />
            <Feature label="Avg return" value={anomaly.features.averageReturnPercent} suffix="%" />
            <Feature label="Volatility" value={anomaly.features.volatilityPercent} suffix="%" />
            <Feature label="Trend slope" value={anomaly.features.trendSlopePercent} suffix="%" />
            <Feature label="SMA distance" value={anomaly.features.smaDistancePercent} suffix="%" />
            <Feature label="Volume z-score" value={anomaly.features.volumeZScore} />
          </div>
        </>
      ) : (
        <ChartState
          title="No anomaly check yet"
          message="Run this after importing enough candles. It is just a research warning, not a trade signal."
        />
      )}
    </section>
  );
}

function marketRegimeProvenanceItems(regime: MarketRegimeResponse): Array<[string, string | number]> {
  return [
    ["Source", regime.classifierSource],
    ["Mode", regime.mode],
    ["Model", regime.modelVersion],
    ["Features", regime.featureVersion],
    ["Sequence", regime.sequenceLength],
    ["Artifact", regime.artifactIdentifier],
  ].filter((item): item is [string, string | number] => item[1] !== null && item[1] !== undefined && item[1] !== "");
}

function FeatureBarChart({ title, features }: { title: string; features: MarketRegimeFeatures }) {
  const data = [
    ["Latest", features.latestReturnPercent],
    ["Average", features.averageReturnPercent],
    ["Volatility", features.volatilityPercent],
    ["Trend", features.trendSlopePercent],
    ["SMA gap", features.smaDistancePercent],
    ["Volume z", features.volumeZScore],
  ].map(([name, value]) => ({ name, value }));

  return (
    <ChartShell title={title} height={180}>
      <BarChart data={data} margin={{ top: 12, right: 12, bottom: 0, left: 0 }}>
        <CartesianGrid stroke="var(--border)" strokeDasharray="4 5" vertical={false} />
        <XAxis dataKey="name" tick={{ fontSize: 11 }} />
        <YAxis hide />
        <Tooltip formatter={(value) => Number(value).toFixed(2)} />
        <Bar dataKey="value" fill="#2d6cdf" radius={[4, 4, 0, 0]} />
      </BarChart>
    </ChartShell>
  );
}

function Feature({ label, value, suffix = "" }: { label: string; value: number; suffix?: string }) {
  return (
    <div className="feature">
      <span>{label}</span>
      <strong>
        {value.toFixed(2)}
        {suffix}
      </strong>
    </div>
  );
}
