import { useEffect, useState } from "react";
import { errorMessage } from "./api/client";
import { DashboardSummary, fetchDashboardSummary } from "./api/dashboard";

type LoadState<T> =
  | { status: "loading"; data: null; error: null }
  | { status: "success"; data: T; error: null }
  | { status: "error"; data: null; error: string };

const loadingSummary: LoadState<DashboardSummary> = {
  status: "loading",
  data: null,
  error: null,
};

function App() {
  const [summaryState, setSummaryState] = useState<LoadState<DashboardSummary>>(loadingSummary);

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

  return (
    <main className="app-shell">
      <header className="dashboard-header">
        <div>
          <p className="eyebrow">SignalAttention</p>
          <h1>Trading research dashboard</h1>
        </div>
      </header>
      <section className="panel">
        <h2>Dashboard summary</h2>
        <p>{summaryStatusText(summaryState)}</p>
      </section>
    </main>
  );
}

function summaryStatusText(state: LoadState<DashboardSummary>) {
  if (state.status === "loading") {
    return "Loading backend summary.";
  }
  if (state.status === "error") {
    return state.error;
  }
  return `Loaded ${state.data.strategyCount} strategies and ${state.data.backtestCount} backtests.`;
}

export default App;
