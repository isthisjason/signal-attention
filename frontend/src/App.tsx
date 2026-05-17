import { useEffect, useState } from "react";
import { AuditEvent, fetchAuditEvents } from "./api/audit";
import { errorMessage } from "./api/client";
import {
  DashboardSummary,
  StrategyPerformance,
  fetchDashboardSummary,
  fetchStrategyPerformance,
} from "./api/dashboard";

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

function App() {
  const [summaryState, setSummaryState] = useState<LoadState<DashboardSummary>>(loadingSummary);
  const [strategiesState, setStrategiesState] =
    useState<LoadState<StrategyPerformance[]>>(loadingStrategies);
  const [auditState, setAuditState] = useState<LoadState<AuditEvent[]>>(loadingAuditEvents);

  useEffect(() => {
    let active = true;

    fetchDashboardSummary()
      .then((data) => {
        if (active) {
          setSummaryState({ status: "success", data, error: null });
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setSummaryState({ status: "error", data: null, error: errorMessage(error) });
        }
      });

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    let active = true;

    fetchAuditEvents()
      .then((data) => {
        if (active) {
          setAuditState({ status: "success", data, error: null });
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setAuditState({ status: "error", data: null, error: errorMessage(error) });
        }
      });

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    let active = true;

    fetchStrategyPerformance()
      .then((data) => {
        if (active) {
          setStrategiesState({ status: "success", data, error: null });
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setStrategiesState({ status: "error", data: null, error: errorMessage(error) });
        }
      });

    return () => {
      active = false;
    };
  }, []);

  return (
    <main className="app-shell">
      <header className="dashboard-header">
        <div>
          <p className="eyebrow">SignalAttention</p>
          <h1>Trading research dashboard</h1>
        </div>
      </header>
      <SummaryCards state={summaryState} />
      <StrategyTable state={strategiesState} />
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
      <section className="panel">
        <h2>Dashboard summary</h2>
        <p>{state.error}</p>
      </section>
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
    return (
      <section className="panel">
        <h2>Strategy performance</h2>
        <p>Loading strategy performance.</p>
      </section>
    );
  }
  if (state.status === "error") {
    return (
      <section className="panel">
        <h2>Strategy performance</h2>
        <p>{state.error}</p>
      </section>
    );
  }
  if (state.data.length === 0) {
    return (
      <section className="panel">
        <h2>Strategy performance</h2>
        <p>No strategies have been created yet.</p>
      </section>
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
    return (
      <section className="panel">
        <h2>Audit events</h2>
        <p>Loading audit events.</p>
      </section>
    );
  }
  if (state.status === "error") {
    return (
      <section className="panel">
        <h2>Audit events</h2>
        <p>{state.error}</p>
      </section>
    );
  }
  if (state.data.length === 0) {
    return (
      <section className="panel">
        <h2>Audit events</h2>
        <p>No audit events have been recorded yet.</p>
      </section>
    );
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

export default App;
