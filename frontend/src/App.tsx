import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { AuditEvent, fetchAuditEvents } from "./api/audit";
import {
  BacktestRun,
  BacktestTrade,
  MlRiskScore,
  fetchBacktest,
  fetchBacktestTrades,
  runBacktest,
  scoreBacktestRisk,
} from "./api/backtests";
import { errorMessage } from "./api/client";
import {
  DashboardRiskAlert,
  DashboardSummary,
  StrategyPerformance,
  fetchDashboardRiskAlerts,
  fetchDashboardSummary,
  fetchStrategyPerformance,
} from "./api/dashboard";
import { MarketDataImportSummary, importMarketData } from "./api/marketData";
import { MarketRegimeResponse, fetchMarketRegime } from "./api/marketRegime";
import {
  PaperReplayResult,
  PaperOrder,
  PaperPosition,
  PaperSession,
  PaperSessionSummary,
  createPaperSession,
  fetchPaperOrders,
  fetchPaperPositions,
  fetchPaperSessionSummary,
  fetchStrategyPaperSessions,
  replayPaperSession,
  startPaperSession,
  stopPaperSession,
  submitPaperOrder,
} from "./api/paperTrading";
import { Strategy, createStrategy, fetchStrategies } from "./api/strategies";

type LoadState<T> =
  | { status: "loading"; data: null; error: null }
  | { status: "success"; data: T; error: null }
  | { status: "error"; data: null; error: string };

const loadingSummary: LoadState<DashboardSummary> = { status: "loading", data: null, error: null };
const loadingStrategies: LoadState<StrategyPerformance[]> = {
  status: "loading",
  data: null,
  error: null,
};
const loadingAuditEvents: LoadState<AuditEvent[]> = { status: "loading", data: null, error: null };
const loadingRiskAlerts: LoadState<DashboardRiskAlert[]> = {
  status: "loading",
  data: null,
  error: null,
};
const loadingMarketRegime: LoadState<MarketRegimeResponse> = {
  status: "loading",
  data: null,
  error: null,
};
const loadingStrategyList: LoadState<Strategy[]> = { status: "loading", data: null, error: null };

type Notice = { tone: "success" | "error"; message: string } | null;

type StrategyFormState = {
  name: string;
  symbol: string;
  timeframe: string;
  shortWindow: string;
  longWindow: string;
  initialBalance: string;
  feePercent: string;
  positionSizePercent: string;
};

const defaultStart = "2024-01-01T00:00";
const defaultEnd = "2024-01-10T00:00";

function App() {
  const [summaryState, setSummaryState] = useState<LoadState<DashboardSummary>>(loadingSummary);
  const [strategiesState, setStrategiesState] =
    useState<LoadState<StrategyPerformance[]>>(loadingStrategies);
  const [auditState, setAuditState] = useState<LoadState<AuditEvent[]>>(loadingAuditEvents);
  const [riskAlertsState, setRiskAlertsState] =
    useState<LoadState<DashboardRiskAlert[]>>(loadingRiskAlerts);
  const [regimeState, setRegimeState] =
    useState<LoadState<MarketRegimeResponse>>(loadingMarketRegime);
  const [strategyListState, setStrategyListState] =
    useState<LoadState<Strategy[]>>(loadingStrategyList);

  const [selectedStrategyId, setSelectedStrategyId] = useState<number | null>(null);
  const [notice, setNotice] = useState<Notice>(null);
  const [busyAction, setBusyAction] = useState<string | null>(null);

  const [strategyForm, setStrategyForm] = useState({
    name: "BTC SMA Crossover",
    symbol: "BTC-USD",
    timeframe: "1h",
    shortWindow: "3",
    longWindow: "5",
    initialBalance: "10000",
    feePercent: "0.1",
    positionSizePercent: "50",
  });
  const [importSummary, setImportSummary] = useState<MarketDataImportSummary | null>(null);
  const [backtestForm, setBacktestForm] = useState({ startDate: defaultStart, endDate: defaultEnd });
  const [backtestRun, setBacktestRun] = useState<BacktestRun | null>(null);
  const [backtestTrades, setBacktestTrades] = useState<BacktestTrade[]>([]);
  const [riskScore, setRiskScore] = useState<MlRiskScore | null>(null);
  const [paperForm, setPaperForm] = useState({
    initialBalance: "100000",
    startDate: defaultStart,
    endDate: defaultEnd,
    maxCandles: "250",
    orderSide: "BUY",
    orderSymbol: "BTC-USD",
    orderQuantity: "0.25",
    orderPrice: "42000",
  });
  const [paperSessions, setPaperSessions] = useState<PaperSession[]>([]);
  const [selectedPaperSessionId, setSelectedPaperSessionId] = useState<number | null>(null);
  const [paperSummary, setPaperSummary] = useState<PaperSessionSummary | null>(null);
  const [paperReplay, setPaperReplay] = useState<PaperReplayResult | null>(null);
  const [paperOrders, setPaperOrders] = useState<PaperOrder[]>([]);
  const [paperPositions, setPaperPositions] = useState<PaperPosition[]>([]);

  const loadDashboard = useCallback(() => {
    setSummaryState(loadingSummary);
    setStrategiesState(loadingStrategies);
    setAuditState(loadingAuditEvents);
    setRiskAlertsState(loadingRiskAlerts);
    setRegimeState(loadingMarketRegime);
    setStrategyListState(loadingStrategyList);

    fetchDashboardSummary()
      .then((data) => setSummaryState({ status: "success", data, error: null }))
      .catch((error: unknown) =>
        setSummaryState({ status: "error", data: null, error: errorMessage(error) }),
      );
    fetchMarketRegime()
      .then((data) => setRegimeState({ status: "success", data, error: null }))
      .catch((error: unknown) =>
        setRegimeState({ status: "error", data: null, error: errorMessage(error) }),
      );
    fetchAuditEvents()
      .then((data) => setAuditState({ status: "success", data, error: null }))
      .catch((error: unknown) =>
        setAuditState({ status: "error", data: null, error: errorMessage(error) }),
      );
    fetchDashboardRiskAlerts()
      .then((data) => setRiskAlertsState({ status: "success", data, error: null }))
      .catch((error: unknown) =>
        setRiskAlertsState({ status: "error", data: null, error: errorMessage(error) }),
      );
    fetchStrategyPerformance()
      .then((data) => setStrategiesState({ status: "success", data, error: null }))
      .catch((error: unknown) =>
        setStrategiesState({ status: "error", data: null, error: errorMessage(error) }),
      );
    fetchStrategies()
      .then((data) => {
        setStrategyListState({ status: "success", data, error: null });
        setSelectedStrategyId((current) => current ?? data[0]?.id ?? null);
      })
      .catch((error: unknown) =>
        setStrategyListState({ status: "error", data: null, error: errorMessage(error) }),
      );
  }, []);

  useEffect(() => {
    loadDashboard();
  }, [loadDashboard]);

  const loadPaperSessions = useCallback((strategyId: number, preferredSessionId?: number) => {
    fetchStrategyPaperSessions(strategyId)
      .then((sessions) => {
        setPaperSessions(sessions);
        setSelectedPaperSessionId((current) =>
          selectPaperSessionId(current, sessions, preferredSessionId),
        );
      })
      .catch((error: unknown) => setNotice({ tone: "error", message: errorMessage(error) }));
  }, []);

  useEffect(() => {
    setPaperReplay(null);
    if (selectedStrategyId === null) {
      setPaperSessions([]);
      setSelectedPaperSessionId(null);
      return;
    }
    loadPaperSessions(selectedStrategyId);
  }, [loadPaperSessions, selectedStrategyId]);

  useEffect(() => {
    if (selectedPaperSessionId === null) {
      setPaperSummary(null);
      setPaperOrders([]);
      setPaperPositions([]);
      return;
    }
    Promise.all([
      fetchPaperSessionSummary(selectedPaperSessionId),
      fetchPaperOrders(selectedPaperSessionId),
      fetchPaperPositions(selectedPaperSessionId),
    ])
      .then(([summary, orders, positions]) => {
        setPaperSummary(summary);
        setPaperOrders(orders);
        setPaperPositions(positions);
      })
      .catch(() => {
        setPaperSummary(null);
        setPaperOrders([]);
        setPaperPositions([]);
      });
  }, [selectedPaperSessionId, paperReplay]);

  const selectedStrategy = useMemo(() => {
    if (strategyListState.status !== "success" || selectedStrategyId === null) {
      return null;
    }
    return strategyListState.data.find((strategy) => strategy.id === selectedStrategyId) ?? null;
  }, [selectedStrategyId, strategyListState]);

  const loading =
    summaryState.status === "loading" ||
    strategiesState.status === "loading" ||
    auditState.status === "loading" ||
    riskAlertsState.status === "loading" ||
    regimeState.status === "loading" ||
    strategyListState.status === "loading";

  async function runAction(action: string, work: () => Promise<void>) {
    setBusyAction(action);
    setNotice(null);
    try {
      await work();
    } catch (error: unknown) {
      setNotice({ tone: "error", message: errorMessage(error) });
    } finally {
      setBusyAction(null);
    }
  }

  function handleStrategySubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void runAction("strategy", async () => {
      const created = await createStrategy({
        name: strategyForm.name,
        symbol: strategyForm.symbol,
        timeframe: strategyForm.timeframe,
        strategyType: "SMA_CROSSOVER",
        rules: {
          shortWindow: Number(strategyForm.shortWindow),
          longWindow: Number(strategyForm.longWindow),
          initialBalance: Number(strategyForm.initialBalance),
          feePercent: Number(strategyForm.feePercent),
          positionSizePercent: Number(strategyForm.positionSizePercent),
        },
      });
      setSelectedStrategyId(created.id);
      setNotice({ tone: "success", message: `Created strategy #${created.id}.` });
      loadDashboard();
    });
  }

  function handleImportSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const fileInput = event.currentTarget.elements.namedItem("csv") as HTMLInputElement;
    const file = fileInput.files?.[0];
    if (!file) {
      setNotice({ tone: "error", message: "Choose a CSV file before importing." });
      return;
    }
    void runAction("import", async () => {
      const summary = await importMarketData(file);
      setImportSummary(summary);
      setNotice({ tone: "success", message: `Imported ${summary.rowsImported} candle rows.` });
      loadDashboard();
    });
  }

  function handleBacktestSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (selectedStrategyId === null) {
      setNotice({ tone: "error", message: "Create or select a strategy first." });
      return;
    }
    void runAction("backtest", async () => {
      const run = await runBacktest(selectedStrategyId, {
        startDate: toInstant(backtestForm.startDate, "Backtest start"),
        endDate: toInstant(backtestForm.endDate, "Backtest end"),
      });
      const trades = await fetchBacktestTrades(run.id);
      setBacktestRun(run);
      setBacktestTrades(trades);
      setRiskScore(null);
      setNotice({ tone: "success", message: `Backtest #${run.id} completed.` });
      loadDashboard();
    });
  }

  function handleRiskScore() {
    if (!backtestRun) {
      setNotice({ tone: "error", message: "Run a backtest before scoring risk." });
      return;
    }
    void runAction("risk", async () => {
      const score = await scoreBacktestRisk(backtestRun.id);
      const refreshed = await fetchBacktest(backtestRun.id);
      setRiskScore(score);
      setBacktestRun(refreshed);
      setNotice({ tone: "success", message: `Risk score saved as ${score.riskLabel}.` });
      loadDashboard();
    });
  }

  function handlePaperCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (selectedStrategyId === null) {
      setNotice({ tone: "error", message: "Create or select a strategy first." });
      return;
    }
    void runAction("paper-create", async () => {
      const session = await createPaperSession(selectedStrategyId, Number(paperForm.initialBalance));
      await loadPaperSessions(selectedStrategyId, session.id);
      setNotice({ tone: "success", message: `Created paper session #${session.id}.` });
      loadDashboard();
    });
  }

  function handlePaperStartStop(action: "start" | "stop") {
    if (selectedPaperSessionId === null || selectedStrategyId === null) {
      setNotice({ tone: "error", message: "Create or select a paper session first." });
      return;
    }
    void runAction(`paper-${action}`, async () => {
      const session =
        action === "start"
          ? await startPaperSession(selectedPaperSessionId)
          : await stopPaperSession(selectedPaperSessionId);
      await loadPaperSessions(selectedStrategyId);
      setSelectedPaperSessionId(session.id);
      setNotice({ tone: "success", message: `${action === "start" ? "Started" : "Stopped"} session #${session.id}.` });
      loadDashboard();
    });
  }

  function handlePaperReplay() {
    if (selectedPaperSessionId === null) {
      setNotice({ tone: "error", message: "Create or select a paper session first." });
      return;
    }
    void runAction("paper-replay", async () => {
      const result = await replayPaperSession(selectedPaperSessionId, {
        startDate: toInstant(paperForm.startDate, "Replay start"),
        endDate: toInstant(paperForm.endDate, "Replay end"),
        maxCandles: Number(paperForm.maxCandles),
      });
      setPaperReplay(result);
      setNotice({ tone: "success", message: `Replay filled ${result.filledOrders} orders.` });
      loadDashboard();
    });
  }

  function handlePaperOrder() {
    if (selectedPaperSessionId === null) {
      setNotice({ tone: "error", message: "Create or select a paper session first." });
      return;
    }
    void runAction("paper-order", async () => {
      const order = await submitPaperOrder(selectedPaperSessionId, {
        side: paperForm.orderSide === "SELL" ? "SELL" : "BUY",
        symbol: paperForm.orderSymbol,
        quantity: Number(paperForm.orderQuantity),
        price: Number(paperForm.orderPrice),
      });
      setPaperOrders(await fetchPaperOrders(selectedPaperSessionId));
      setPaperPositions(await fetchPaperPositions(selectedPaperSessionId));
      setPaperSummary(await fetchPaperSessionSummary(selectedPaperSessionId));
      setNotice({ tone: order.status === "FILLED" ? "success" : "error", message: `Order #${order.id} ${order.status.toLowerCase()}.` });
      loadDashboard();
    });
  }

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

      {notice ? <div className={`notice notice-${notice.tone}`}>{notice.message}</div> : null}

      <SummaryCards state={summaryState} />
      <RiskAlertsPanel state={riskAlertsState} />

      <section className="workflow-grid" aria-label="Research workflow controls">
        <MarketDataImportPanel
          busy={busyAction === "import"}
          importSummary={importSummary}
          onSubmit={handleImportSubmit}
        />
        <StrategyWorkflowPanel
          busy={busyAction === "strategy"}
          form={strategyForm}
          selectedStrategyId={selectedStrategyId}
          state={strategyListState}
          onSelect={setSelectedStrategyId}
          onSubmit={handleStrategySubmit}
          onUpdate={setStrategyForm}
        />
        <BacktestWorkflowPanel
          backtestRun={backtestRun}
          busy={busyAction === "backtest" || busyAction === "risk"}
          form={backtestForm}
          riskScore={riskScore}
          selectedStrategy={selectedStrategy}
          trades={backtestTrades}
          onRiskScore={handleRiskScore}
          onSubmit={handleBacktestSubmit}
          onUpdate={setBacktestForm}
        />
        <PaperTradingPanel
          busy={busyAction?.startsWith("paper") ?? false}
          form={paperForm}
          replay={paperReplay}
          selectedSessionId={selectedPaperSessionId}
          sessions={paperSessions}
          summary={paperSummary}
          onCreate={handlePaperCreate}
          onOrder={handlePaperOrder}
          onReplay={handlePaperReplay}
          onSelect={setSelectedPaperSessionId}
          onStart={() => handlePaperStartStop("start")}
          onStop={() => handlePaperStartStop("stop")}
          onUpdate={setPaperForm}
          orders={paperOrders}
          positions={paperPositions}
        />
      </section>

      <StrategyTable state={strategiesState} />
      <MarketRegimePanel state={regimeState} />
      <AuditTimeline state={auditState} />
    </main>
  );
}

function MarketDataImportPanel({
  busy,
  importSummary,
  onSubmit,
}: {
  busy: boolean;
  importSummary: MarketDataImportSummary | null;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <section className="panel">
      <h2>Market data</h2>
      <form className="control-stack" onSubmit={onSubmit}>
        <label>
          Candle CSV
          <input accept=".csv,text/csv" name="csv" type="file" />
        </label>
        <button className="button" disabled={busy} type="submit">
          {busy ? "Importing" : "Import CSV"}
        </button>
      </form>
      {importSummary ? (
        <ResultGrid
          items={[
            ["Rows read", importSummary.totalRows],
            ["Imported", importSummary.rowsImported],
            ["Rejected", importSummary.rowsRejected],
          ]}
        />
      ) : null}
      {importSummary?.errors.length ? (
        <ul className="compact-list">
          {importSummary.errors.slice(0, 4).map((error) => (
            <li key={`${error.rowNumber}-${error.message}`}>Row {error.rowNumber}: {error.message}</li>
          ))}
        </ul>
      ) : null}
    </section>
  );
}

function StrategyWorkflowPanel({
  busy,
  form,
  selectedStrategyId,
  state,
  onSelect,
  onSubmit,
  onUpdate,
}: {
  busy: boolean;
  form: StrategyFormState;
  selectedStrategyId: number | null;
  state: LoadState<Strategy[]>;
  onSelect: (id: number | null) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onUpdate: (form: StrategyFormState) => void;
}) {
  return (
    <section className="panel">
      <h2>Strategy</h2>
      <label>
        Active strategy
        <select
          disabled={state.status !== "success"}
          value={selectedStrategyId ?? ""}
          onChange={(event) => onSelect(event.target.value ? Number(event.target.value) : null)}
        >
          <option value="">None selected</option>
          {state.status === "success"
            ? state.data.map((strategy) => (
                <option key={strategy.id} value={strategy.id}>
                  #{strategy.id} {strategy.name}
                </option>
              ))
            : null}
        </select>
      </label>
      <form className="control-stack" onSubmit={onSubmit}>
        <div className="form-grid">
          <TextInput label="Name" name="name" state={form} setState={onUpdate} />
          <TextInput label="Symbol" name="symbol" state={form} setState={onUpdate} />
          <TextInput label="Timeframe" name="timeframe" state={form} setState={onUpdate} />
          <TextInput label="Short SMA" name="shortWindow" state={form} setState={onUpdate} type="number" />
          <TextInput label="Long SMA" name="longWindow" state={form} setState={onUpdate} type="number" />
          <TextInput label="Initial balance" name="initialBalance" state={form} setState={onUpdate} type="number" />
          <TextInput label="Fee %" name="feePercent" state={form} setState={onUpdate} type="number" />
          <TextInput
            label="Position size %"
            name="positionSizePercent"
            state={form}
            setState={onUpdate}
            type="number"
          />
        </div>
        <button className="button" disabled={busy} type="submit">
          {busy ? "Creating" : "Create SMA strategy"}
        </button>
      </form>
    </section>
  );
}

function BacktestWorkflowPanel({
  backtestRun,
  busy,
  form,
  riskScore,
  selectedStrategy,
  trades,
  onRiskScore,
  onSubmit,
  onUpdate,
}: {
  backtestRun: BacktestRun | null;
  busy: boolean;
  form: { startDate: string; endDate: string };
  riskScore: MlRiskScore | null;
  selectedStrategy: Strategy | null;
  trades: BacktestTrade[];
  onRiskScore: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onUpdate: (form: { startDate: string; endDate: string }) => void;
}) {
  return (
    <section className="panel">
      <h2>Backtest</h2>
      <p>{selectedStrategy ? `${selectedStrategy.symbol} ${selectedStrategy.timeframe}` : "Select a strategy first."}</p>
      <form className="control-stack" onSubmit={onSubmit}>
        <div className="form-grid">
          <DateInput label="Start" name="startDate" state={form} setState={onUpdate} />
          <DateInput label="End" name="endDate" state={form} setState={onUpdate} />
        </div>
        <div className="button-row">
          <button className="button" disabled={busy || !selectedStrategy} type="submit">
            {busy ? "Working" : "Run backtest"}
          </button>
          <button className="button button-secondary" disabled={busy || !backtestRun} onClick={onRiskScore} type="button">
            Score ML risk
          </button>
        </div>
      </form>
      {backtestRun ? (
        <>
          <ResultGrid
            items={[
              ["Run", `#${backtestRun.id}`],
              ["Return", formatPercent(backtestRun.totalReturn)],
              ["Drawdown", formatPercent(backtestRun.maxDrawdown)],
              ["Trades", backtestRun.tradeCount],
              ["Risk", backtestRun.mlRiskLabel || riskScore?.riskLabel || "Unscored"],
            ]}
          />
          <TradePreview trades={trades} />
        </>
      ) : null}
      {riskScore ? <ul className="compact-list">{riskScore.reasons.map((reason) => <li key={reason}>{reason}</li>)}</ul> : null}
    </section>
  );
}

function PaperTradingPanel({
  busy,
  form,
  orders,
  positions,
  replay,
  selectedSessionId,
  sessions,
  summary,
  onCreate,
  onOrder,
  onReplay,
  onSelect,
  onStart,
  onStop,
  onUpdate,
}: {
  busy: boolean;
  form: {
    initialBalance: string;
    startDate: string;
    endDate: string;
    maxCandles: string;
    orderSide: string;
    orderSymbol: string;
    orderQuantity: string;
    orderPrice: string;
  };
  orders: PaperOrder[];
  positions: PaperPosition[];
  replay: PaperReplayResult | null;
  selectedSessionId: number | null;
  sessions: PaperSession[];
  summary: PaperSessionSummary | null;
  onCreate: (event: FormEvent<HTMLFormElement>) => void;
  onOrder: () => void;
  onReplay: () => void;
  onSelect: (id: number | null) => void;
  onStart: () => void;
  onStop: () => void;
  onUpdate: (form: {
    initialBalance: string;
    startDate: string;
    endDate: string;
    maxCandles: string;
    orderSide: string;
    orderSymbol: string;
    orderQuantity: string;
    orderPrice: string;
  }) => void;
}) {
  return (
    <section className="panel">
      <h2>Paper trading</h2>
      <label>
        Paper session
        <select value={selectedSessionId ?? ""} onChange={(event) => onSelect(event.target.value ? Number(event.target.value) : null)}>
          <option value="">None selected</option>
          {sessions.map((session) => (
            <option key={session.id} value={session.id}>
              #{session.id} {session.status}
            </option>
          ))}
        </select>
      </label>
      <form className="control-stack" onSubmit={onCreate}>
        <TextInput label="Initial balance" name="initialBalance" state={form} setState={onUpdate} type="number" />
        <button className="button" disabled={busy} type="submit">
          Create session
        </button>
      </form>
      <div className="button-row">
        <button className="button button-secondary" disabled={busy || !selectedSessionId} onClick={onStart} type="button">
          Start
        </button>
        <button className="button button-secondary" disabled={busy || !selectedSessionId} onClick={onStop} type="button">
          Stop
        </button>
      </div>
      <div className="form-grid">
        <label>
          Side
          <select value={form.orderSide} onChange={(event) => onUpdate({ ...form, orderSide: event.target.value })}>
            <option value="BUY">Buy</option>
            <option value="SELL">Sell</option>
          </select>
        </label>
        <TextInput label="Symbol" name="orderSymbol" state={form} setState={onUpdate} />
        <TextInput label="Quantity" name="orderQuantity" state={form} setState={onUpdate} type="number" />
        <TextInput label="Price" name="orderPrice" state={form} setState={onUpdate} type="number" />
      </div>
      <button className="button button-secondary" disabled={busy || !selectedSessionId} onClick={onOrder} type="button">
        Submit order
      </button>
      <div className="form-grid">
        <DateInput label="Replay start" name="startDate" state={form} setState={onUpdate} />
        <DateInput label="Replay end" name="endDate" state={form} setState={onUpdate} />
        <TextInput label="Max candles" name="maxCandles" state={form} setState={onUpdate} type="number" />
      </div>
      <button className="button" disabled={busy || !selectedSessionId} onClick={onReplay} type="button">
        Replay candles
      </button>
      {summary ? (
        <ResultGrid
          items={[
            ["Status", summary.status],
            ["Cash", formatCurrency(summary.cashBalance)],
            ["Equity", formatCurrency(summary.totalEquity)],
            ["Open value", formatCurrency(summary.openPositionValue)],
            ["Unrealized", formatCurrency(summary.unrealizedPnl)],
          ]}
        />
      ) : null}
      {replay ? (
        <ResultGrid
          items={[
            ["Candles", replay.candlesRead],
            ["Signals", replay.signalsProcessed],
            ["Filled", replay.filledOrders],
            ["Rejected", replay.rejectedOrders],
          ]}
        />
      ) : null}
      {orders.length ? (
        <div className="mini-table">
          {orders.slice(0, 4).map((order) => (
            <div key={order.id}>
              <span>{order.side}</span>
              <strong>{order.status}</strong>
              <small>{formatCurrency(order.notional)}</small>
            </div>
          ))}
        </div>
      ) : null}
      {positions.length ? (
        <ul className="compact-list">
          {positions.slice(0, 4).map((position) => (
            <li key={position.id}>
              {position.status} {position.quantity} {position.symbol} at {formatCurrency(position.entryPrice)}
            </li>
          ))}
        </ul>
      ) : null}
    </section>
  );
}

function TextInput<T extends Record<string, string>>({
  label,
  name,
  state,
  setState,
  type = "text",
}: {
  label: string;
  name: keyof T & string;
  state: T;
  setState: (state: T) => void;
  type?: string;
}) {
  return (
    <label>
      {label}
      <input
        min={type === "number" ? "0" : undefined}
        step={type === "number" ? "any" : undefined}
        type={type}
        value={state[name]}
        onChange={(event) => setState({ ...state, [name]: event.target.value })}
      />
    </label>
  );
}

function DateInput<T extends Record<string, string>>({
  label,
  name,
  state,
  setState,
}: {
  label: string;
  name: keyof T & string;
  state: T;
  setState: (state: T) => void;
}) {
  return <TextInput label={label} name={name} state={state} setState={setState} type="datetime-local" />;
}

function ResultGrid({ items }: { items: Array<[string, string | number]> }) {
  return (
    <dl className="result-grid">
      {items.map(([label, value]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  );
}

function TradePreview({ trades }: { trades: BacktestTrade[] }) {
  if (trades.length === 0) {
    return <p>No trades were generated for this backtest.</p>;
  }
  return (
    <div className="mini-table">
      {trades.slice(0, 5).map((trade) => (
        <div key={trade.id}>
          <span>{trade.side}</span>
          <strong>{formatCurrency(trade.netPnl)}</strong>
          <small>{formatPercent(trade.returnPercent)}</small>
        </div>
      ))}
    </div>
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
    return <PanelMessage title="Dashboard summary" tone="error" message={state.error} />;
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

function RiskAlertsPanel({ state }: { state: LoadState<DashboardRiskAlert[]> }) {
  if (state.status === "loading") {
    return <PanelMessage title="Risk alerts" message="Loading risk alerts." />;
  }
  if (state.status === "error") {
    return <PanelMessage title="Risk alerts" tone="error" message={state.error} />;
  }
  if (state.data.length === 0) {
    return <PanelMessage title="Risk alerts" message="No risk alerts are active." />;
  }

  return (
    <section className="panel">
      <h2>Risk alerts</h2>
    </section>
  );
}

function StrategyTable({ state }: { state: LoadState<StrategyPerformance[]> }) {
  if (state.status === "loading") {
    return <PanelMessage title="Strategy performance" message="Loading strategy performance." />;
  }
  if (state.status === "error") {
    return <PanelMessage title="Strategy performance" tone="error" message={state.error} />;
  }
  if (state.data.length === 0) {
    return <PanelMessage title="Strategy performance" message="No strategies have been created yet." />;
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
  const provenance = marketRegimeProvenanceItems(state.data);

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
      {provenance.length ? <ResultGrid items={provenance} /> : null}
      <p className="muted">Classifier source: {state.data.classifierSource}</p>
    </section>
  );
}

function marketRegimeProvenanceItems(regime: MarketRegimeResponse): Array<[string, string | number]> {
  return [
    ["Mode", regime.mode],
    ["Model", regime.modelVersion],
    ["Features", regime.featureVersion],
    ["Sequence", regime.sequenceLength],
    ["Artifact", regime.artifactIdentifier],
  ].filter((item): item is [string, string | number] => item[1] !== null && item[1] !== undefined && item[1] !== "");
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

export function toInstant(value: string, label = "Date") {
  const date = new Date(value);
  if (!value || Number.isNaN(date.getTime())) {
    throw new Error(`${label} must be a valid date and time.`);
  }
  return date.toISOString();
}

export function selectPaperSessionId(
  current: number | null,
  sessions: PaperSession[],
  preferred?: number,
) {
  if (preferred !== undefined && sessions.some((session) => session.id === preferred)) {
    return preferred;
  }
  if (current !== null && sessions.some((session) => session.id === current)) {
    return current;
  }
  return sessions[0]?.id ?? null;
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

function formatPercent(value: number | null | undefined) {
  if (value === null || value === undefined) {
    return "N/A";
  }
  return `${Number(value).toFixed(2)}%`;
}

function formatCurrency(value: number | null | undefined) {
  if (value === null || value === undefined) {
    return "N/A";
  }
  return new Intl.NumberFormat(undefined, {
    currency: "USD",
    style: "currency",
    maximumFractionDigits: 2,
  }).format(Number(value));
}

export default App;
