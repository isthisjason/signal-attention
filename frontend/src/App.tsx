import { useEffect, useState } from "react";
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

function App() {
  const [summaryState, setSummaryState] = useState<LoadState<DashboardSummary>>(loadingSummary);
  const [strategiesState, setStrategiesState] =
    useState<LoadState<StrategyPerformance[]>>(loadingStrategies);

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
      <section className="panel">
        <h2>Strategy performance</h2>
        <p>{strategyStatusText(strategiesState)}</p>
      </section>
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

function strategyStatusText(state: LoadState<StrategyPerformance[]>) {
  if (state.status === "loading") {
    return "Loading strategy performance.";
  }
  if (state.status === "error") {
    return state.error;
  }
  return `Loaded ${state.data.length} strategy rows.`;
}

export default App;
