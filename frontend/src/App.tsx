import { useCallback, useEffect, useState } from "react";
import { AuditEvent, fetchAuditEvents } from "./api/audit";
import { errorMessage } from "./api/client";
import {
  DashboardSummary,
  StrategyPerformance,
  fetchDashboardSummary,
  fetchStrategyPerformance,
} from "./api/dashboard";
import { MarketRegimeResponse, fetchMarketRegime } from "./api/marketRegime";

type LoadState<T> =
  | { status: "loading"; data: null; error: null }
  | { status: "success"; data: T; error: null }
  | { status: "error"; data: null; error: string };

const loadingSummary: LoadState<DashboardSummary> = {
  status: "loading",
  data: null,
  error: null,
};

const loadingStrategies: LoadState<StrategyPerformance[]> = {
  status: "loading",
  data: null,
  error: null,
};

const loadingAuditEvents: LoadState<AuditEvent[]> = {
  status: "loading",
  data: null,
  error: null,
};

const loadingMarketRegime: LoadState<MarketRegimeResponse> = {
  status: "loading",
  data: null,
  error: null,
};

function App() {
  const [summaryState, setSummaryState] = useState<LoadState<DashboardSummary>>(loadingSummary);
  const [strategiesState, setStrategiesState] =
    useState<LoadState<StrategyPerformance[]>>(loadingStrategies);
  const [auditState, setAuditState] = useState<LoadState<AuditEvent[]>>(loadingAuditEvents);
  const [regimeState, setRegimeState] =
    useState<LoadState<MarketRegimeResponse>>(loadingMarketRegime);

  const loadDashboard = useCallback(() => {
    setSummaryState(loadingSummary);
    setStrategiesState(loadingStrategies);
    setAuditState(loadingAuditEvents);
    setRegimeState(loadingMarketRegime);

    fetchDashboardSummary()
      .then((data) => {
        setSummaryState({ status: "success", data, error: null });
      })
      .catch((error: unknown) => {
        setSummaryState({ status: "error", data: null, error: errorMessage(error) });
      });

    fetchMarketRegime()
      .then((data) => {
        setRegimeState({ status: "success", data, error: null });
      })
      .catch((error: unknown) => {
        setRegimeState({ status: "error", data: null, error: errorMessage(error) });
      });

    fetchAuditEvents()
      .then((data) => {
        setAuditState({ status: "success", data, error: null });
      })
      .catch((error: unknown) => {
        setAuditState({ status: "error", data: null, error: errorMessage(error) });
      });

    fetchStrategyPerformance()
      .then((data) => {
        setStrategiesState({ status: "success", data, error: null });
      })
      .catch((error: unknown) => {
        setStrategiesState({ status: "error", data: null, error: errorMessage(error) });
      });
  }, []);

  useEffect(() => {
    loadDashboard();
  }, [loadDashboard]);

  const loading =
    summaryState.status === "loading" ||
    strategiesState.status === "loading" ||
    auditState.status === "loading" ||
    regimeState.status === "loading";

  return (
    <main className="app-shell">
      <header className="dashboard-header">
        <div>
          <p className="eyebrow">SignalAttention</p>
          <h1>Trading research dashboard</h1>
        </div>
        <button className="button" disabled={loading} onClick={loadDashboard} type="button">
          {loading ? "Refreshing" : "Refresh"}
        </button>
      </header>
      <SummaryCards state={summaryState} />
      <StrategyTable state={strategiesState} />
      <MarketRegimePanel state={regimeState} />
      <AuditTimeline state={auditState} />
    </main>
  );
}

function SummaryCards({ state }: { state: LoadState<DashboardSummary> }) {
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
    return (
      <PanelMessage title="Dashboard summary" tone="error" message={state.error} />
    );
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

function formatPercent(value: number | null) {
  if (value === null || value === undefined) {
    return "N/A";
  }
  return `${value.toFixed(2)}%`;
}

function StrategyTable({ state }: { state: LoadState<StrategyPerformance[]> }) {
  if (state.status === "loading") {
    return <PanelMessage title="Strategy performance" message="Loading strategy performance." />;
  }
  if (state.status === "error") {
    return <PanelMessage title="Strategy performance" tone="error" message={state.error} />;
  }
  if (state.data.length === 0) {
    return (
      <PanelMessage
        title="Strategy performance"
        message="No strategies have been created yet. Complete the demo flow to populate this table."
      />
    );
  }

  return (
    <section className="panel">
      <h2>Strategy performance</h2>
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              <th>Strategy</th>
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

function AuditTimeline({ state }: { state: LoadState<AuditEvent[]> }) {
  if (state.status === "loading") {
    return <PanelMessage title="Audit events" message="Loading audit events." />;
  }
  if (state.status === "error") {
    return <PanelMessage title="Audit events" tone="error" message={state.error} />;
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

function formatAction(value: string) {
  return value.replaceAll("_", " ").toLowerCase();
}

function formatDateTime(value: string | null) {
  if (!value) {
    return "N/A";
  }
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

function MarketRegimePanel({ state }: { state: LoadState<MarketRegimeResponse> }) {
  if (state.status === "loading") {
    return <PanelMessage title="Market regime" message="Loading BTC-USD 1h market regime." />;
  }
  if (state.status === "error") {
    return (
      <PanelMessage
        title="Market regime"
        tone="error"
        message={`${state.error} Import at least 20 BTC-USD 1h candles before using this panel.`}
      />
    );
  }

  const { features } = state.data;

  return (
    <section className="panel">
      <div className="panel-heading">
        <div>
          <h2>Market regime</h2>
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
      <div className="feature-grid">
        <Feature label="Latest return" value={features.latestReturnPercent} suffix="%" />
        <Feature label="Avg return" value={features.averageReturnPercent} suffix="%" />
        <Feature label="Volatility" value={features.volatilityPercent} suffix="%" />
        <Feature label="Trend slope" value={features.trendSlopePercent} suffix="%" />
        <Feature label="SMA distance" value={features.smaDistancePercent} suffix="%" />
        <Feature label="Volume z-score" value={features.volumeZScore} />
      </div>
      <p className="muted">Classifier source: {state.data.classifierSource}</p>
    </section>
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

function PanelMessage({
  title,
  message,
  tone = "neutral",
}: {
  title: string;
  message: string;
  tone?: "neutral" | "error";
}) {
  return (
    <section className={`panel panel-message panel-message-${tone}`}>
      <h2>{title}</h2>
      <p>{message}</p>
    </section>
  );
}

export default App;
