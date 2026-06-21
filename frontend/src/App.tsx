import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { Area, AreaChart, Bar, BarChart, CartesianGrid, Tooltip, XAxis, YAxis } from "recharts";
import { ChartShell, ChartState } from "./ChartShell";
import { AnomalyResponse, checkAnomaly } from "./api/anomaly";
import {
  AssistantAction,
  AssistantSession,
  confirmAssistantAction,
  createAssistantSession,
  rejectAssistantAction,
  sendAssistantMessage,
} from "./api/assistant";
import { AuditEvent, fetchAuditEvents } from "./api/audit";
import {
  BacktestRun,
  BacktestTrade,
  BacktestDrawdownPoint,
  BacktestEquityPoint,
  MlRiskScore,
  fetchBacktest,
  fetchBacktestDrawdownSeries,
  fetchBacktestEquitySeries,
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
import { MarketDataImportSummary, MarketDataQuality, fetchMarketDataQuality, importMarketData } from "./api/marketData";
import {
  MarketRegimeFeatures,
  MarketRegimeDiagnostics,
  MarketRegimeExperimentDiagnostics,
  MarketRegimeResponse,
  MarketRegimeStatus,
  RegimeEvidenceSnapshot,
  RegimeRunComparison,
  RegimeRunComparisonDelta,
  RegimeRobustnessSummary,
  RegimeRunResponse,
  RegimeRunSummary,
  fetchMarketRegimeDiagnostics,
  fetchMarketRegime,
  fetchMarketRegimeExperiments,
  fetchRegimeEvidenceSnapshots,
  fetchMarketRegimeStatus,
  fetchRegimeRunComparison,
  fetchRegimeRobustness,
  fetchRegimeRuns,
  runRegimeReplay,
} from "./api/marketRegime";
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
const loadingMarketRegimeStatus: LoadState<MarketRegimeStatus> = {
  status: "loading",
  data: null,
  error: null,
};
const loadingMarketRegimeExperiments: LoadState<MarketRegimeExperimentDiagnostics> = {
  status: "loading",
  data: null,
  error: null,
};
const loadingRegimeRuns: LoadState<RegimeRunSummary[]> = {
  status: "loading",
  data: null,
  error: null,
};
const loadingRegimeComparison: LoadState<RegimeRunComparison> = {
  status: "loading",
  data: null,
  error: null,
};
const loadingStrategyList: LoadState<Strategy[]> = { status: "loading", data: null, error: null };
const loadingMarketDataQuality: LoadState<MarketDataQuality> = {
  status: "loading",
  data: null,
  error: null,
};

type Notice = { tone: "success" | "error"; message: string } | null;

export type WorkbenchActionId =
  | "import-data"
  | "create-strategy"
  | "run-backtest"
  | "score-risk"
  | "run-analysis"
  | "review-results";

export type WorkbenchAction = {
  id: WorkbenchActionId;
  title: string;
  detail: string;
};

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
  const [regimeStatusState, setRegimeStatusState] =
    useState<LoadState<MarketRegimeStatus>>(loadingMarketRegimeStatus);
  const [regimeExperimentsState, setRegimeExperimentsState] =
    useState<LoadState<MarketRegimeExperimentDiagnostics>>(loadingMarketRegimeExperiments);
  const [regimeRunsState, setRegimeRunsState] =
    useState<LoadState<RegimeRunSummary[]>>(loadingRegimeRuns);
  const [regimeComparisonState, setRegimeComparisonState] =
    useState<LoadState<RegimeRunComparison>>(loadingRegimeComparison);
  const [strategyListState, setStrategyListState] =
    useState<LoadState<Strategy[]>>(loadingStrategyList);
  const [marketDataQualityState, setMarketDataQualityState] =
    useState<LoadState<MarketDataQuality>>(loadingMarketDataQuality);

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
  const [backtestEquity, setBacktestEquity] = useState<BacktestEquityPoint[]>([]);
  const [backtestDrawdown, setBacktestDrawdown] = useState<BacktestDrawdownPoint[]>([]);
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
  const [anomaly, setAnomaly] = useState<AnomalyResponse | null>(null);
  const [regimeReplay, setRegimeReplay] = useState<RegimeRunResponse | null>(null);
  const [regimeRobustness, setRegimeRobustness] = useState<RegimeRobustnessSummary | null>(null);
  const [regimeDiagnostics, setRegimeDiagnostics] = useState<MarketRegimeDiagnostics | null>(null);
  const [evidenceSnapshots, setEvidenceSnapshots] = useState<RegimeEvidenceSnapshot[]>([]);
  const [assistantSession, setAssistantSession] = useState<AssistantSession | null>(null);
  const [assistantPrompt, setAssistantPrompt] = useState("What should I inspect next?");

  const loadDashboard = useCallback(async () => {
    // Refresh each panel independently so one failed endpoint does not blank the whole dashboard.
    setSummaryState(loadingSummary);
    setStrategiesState(loadingStrategies);
    setAuditState(loadingAuditEvents);
    setRiskAlertsState(loadingRiskAlerts);
    setRegimeState(loadingMarketRegime);
    setRegimeStatusState(loadingMarketRegimeStatus);
    setRegimeExperimentsState(loadingMarketRegimeExperiments);
    setRegimeRunsState(loadingRegimeRuns);
    setRegimeComparisonState(loadingRegimeComparison);
    setStrategyListState(loadingStrategyList);
    setMarketDataQualityState(loadingMarketDataQuality);

    const summaryLoad = fetchDashboardSummary()
      .then((data) => setSummaryState({ status: "success", data, error: null }))
      .catch((error: unknown) =>
        setSummaryState({ status: "error", data: null, error: errorMessage(error) }),
      );
    const regimeLoad = fetchMarketRegime()
      .then((data) => setRegimeState({ status: "success", data, error: null }))
      .catch((error: unknown) =>
        setRegimeState({ status: "error", data: null, error: errorMessage(error) }),
      );
    const regimeStatusLoad = fetchMarketRegimeStatus()
      .then((data) => setRegimeStatusState({ status: "success", data, error: null }))
      .catch((error: unknown) =>
        setRegimeStatusState({ status: "error", data: null, error: errorMessage(error) }),
      );
    const regimeExperimentsLoad = fetchMarketRegimeExperiments()
      .then((data) => setRegimeExperimentsState({ status: "success", data, error: null }))
      .catch((error: unknown) =>
        setRegimeExperimentsState({ status: "error", data: null, error: errorMessage(error) }),
      );
    const regimeRunsLoad = fetchRegimeRuns()
      .then((data) => setRegimeRunsState({ status: "success", data, error: null }))
      .catch((error: unknown) =>
        setRegimeRunsState({ status: "error", data: null, error: errorMessage(error) }),
      );
    const regimeComparisonLoad = fetchRegimeRunComparison()
      .then((data) => setRegimeComparisonState({ status: "success", data, error: null }))
      .catch((error: unknown) =>
        setRegimeComparisonState({ status: "error", data: null, error: errorMessage(error) }),
      );
    const evidenceSnapshotsLoad = fetchRegimeEvidenceSnapshots()
      .then(setEvidenceSnapshots)
      .catch(() => setEvidenceSnapshots([]));
    const auditLoad = fetchAuditEvents()
      .then((data) => setAuditState({ status: "success", data, error: null }))
      .catch((error: unknown) =>
        setAuditState({ status: "error", data: null, error: errorMessage(error) }),
      );
    const riskAlertsLoad = fetchDashboardRiskAlerts()
      .then((data) => setRiskAlertsState({ status: "success", data, error: null }))
      .catch((error: unknown) =>
        setRiskAlertsState({ status: "error", data: null, error: errorMessage(error) }),
      );
    const strategyPerformanceLoad = fetchStrategyPerformance()
      .then((data) => setStrategiesState({ status: "success", data, error: null }))
      .catch((error: unknown) =>
        setStrategiesState({ status: "error", data: null, error: errorMessage(error) }),
      );
    const strategiesLoad = fetchStrategies()
      .then((data) => {
        setStrategyListState({ status: "success", data, error: null });
        setSelectedStrategyId((current) => current ?? data[0]?.id ?? null);
      })
      .catch((error: unknown) =>
        setStrategyListState({ status: "error", data: null, error: errorMessage(error) }),
      );
    const qualityLoad = fetchMarketDataQuality()
      .then((data) => setMarketDataQualityState({ status: "success", data, error: null }))
      .catch((error: unknown) =>
        setMarketDataQualityState({ status: "error", data: null, error: errorMessage(error) }),
      );

    await Promise.all([
      summaryLoad,
      regimeLoad,
      regimeStatusLoad,
      regimeExperimentsLoad,
      regimeRunsLoad,
      regimeComparisonLoad,
      evidenceSnapshotsLoad,
      auditLoad,
      riskAlertsLoad,
      strategyPerformanceLoad,
      strategiesLoad,
      qualityLoad,
    ]);
  }, []);

  useEffect(() => {
    void loadDashboard();
  }, [loadDashboard]);

  const loadPaperSessions = useCallback(async (strategyId: number, preferredSessionId?: number) => {
    try {
      const sessions = await fetchStrategyPaperSessions(strategyId);
      setPaperSessions(sessions);
      // Keep the current session when possible, but prefer a newly created one after create.
      setSelectedPaperSessionId((current) =>
        selectPaperSessionId(current, sessions, preferredSessionId),
      );
      return sessions;
    } catch (error: unknown) {
      setNotice({ tone: "error", message: errorMessage(error) });
      return [];
    }
  }, []);

  useEffect(() => {
    // Changing strategies resets replay output because sessions and chart context belong to one strategy.
    setPaperReplay(null);
    if (selectedStrategyId === null) {
      setPaperSessions([]);
      setSelectedPaperSessionId(null);
      return;
    }
    void loadPaperSessions(selectedStrategyId);
  }, [loadPaperSessions, selectedStrategyId]);

  useEffect(() => {
    // Session details are kept in local state because several actions update them together.
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

  const nextAction = deriveWorkbenchAction({
    candleCount:
      marketDataQualityState.status === "success"
        ? marketDataQualityState.data.candleCount
        : importSummary?.rowsImported ?? 0,
    strategyCount:
      strategyListState.status === "success"
        ? strategyListState.data.length
        : summaryState.status === "success"
          ? summaryState.data.strategyCount
          : 0,
    hasBacktest: Boolean(backtestRun || (summaryState.status === "success" && summaryState.data.latestBacktest)),
    hasRiskScore: Boolean(
      riskScore ||
        backtestRun?.mlRiskLabel ||
        (summaryState.status === "success" && summaryState.data.latestBacktest?.mlRiskLabel),
    ),
    hasRegimeReplay: Boolean(regimeReplay),
  });

  const loading =
    // The top-level refresh button reflects all dashboard bootstrap requests.
    summaryState.status === "loading" ||
    strategiesState.status === "loading" ||
    auditState.status === "loading" ||
    riskAlertsState.status === "loading" ||
    regimeState.status === "loading" ||
    strategyListState.status === "loading" ||
    marketDataQualityState.status === "loading";

  async function runAction(action: string, work: () => Promise<void>) {
    // One wrapper keeps button busy states and notices consistent across the workbench.
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
      await loadDashboard();
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
      const quality = await fetchMarketDataQuality();
      setMarketDataQualityState({ status: "success", data: quality, error: null });
      setNotice({ tone: "success", message: `Imported ${summary.rowsImported} candle rows.` });
      await loadDashboard();
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
      // Backtest charts are loaded with the run so the result panel is internally consistent.
      const [trades, equity, drawdown] = await Promise.all([
        fetchBacktestTrades(run.id),
        fetchBacktestEquitySeries(run.id),
        fetchBacktestDrawdownSeries(run.id),
      ]);
      setBacktestRun(run);
      setBacktestTrades(trades);
      setBacktestEquity(equity);
      setBacktestDrawdown(drawdown);
      setRiskScore(null);
      setNotice({ tone: "success", message: `Backtest #${run.id} completed.` });
      await loadDashboard();
    });
  }

  function handleRiskScore() {
    if (!backtestRun) {
      setNotice({ tone: "error", message: "Run a backtest before scoring risk." });
      return;
    }
    void runAction("risk", async () => {
      const score = await scoreBacktestRisk(backtestRun.id);
      // Refresh the run because the backend persists the ML score onto the backtest record.
      const refreshed = await fetchBacktest(backtestRun.id);
      setRiskScore(score);
      setBacktestRun(refreshed);
      setNotice({ tone: "success", message: `Risk score saved as ${score.riskLabel}.` });
      await loadDashboard();
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
      await loadDashboard();
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
      await loadDashboard();
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
      // Replay can create orders and positions, so reload the paper details immediately.
      const [summary, orders, positions] = await Promise.all([
        fetchPaperSessionSummary(selectedPaperSessionId),
        fetchPaperOrders(selectedPaperSessionId),
        fetchPaperPositions(selectedPaperSessionId),
      ]);
      setPaperSummary(summary);
      setPaperOrders(orders);
      setPaperPositions(positions);
      setNotice({ tone: "success", message: `Replay filled ${result.filledOrders} orders.` });
      await loadDashboard();
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
      // Orders can affect both cash and positions, so all paper panes reload together.
      setPaperOrders(await fetchPaperOrders(selectedPaperSessionId));
      setPaperPositions(await fetchPaperPositions(selectedPaperSessionId));
      setPaperSummary(await fetchPaperSessionSummary(selectedPaperSessionId));
      setNotice({ tone: order.status === "FILLED" ? "success" : "error", message: `Order #${order.id} ${order.status.toLowerCase()}.` });
      await loadDashboard();
    });
  }

  function handleAnomalyCheck() {
    void runAction("anomaly", async () => {
      const result = await checkAnomaly();
      setAnomaly(result);
      setNotice({ tone: "success", message: `Anomaly check returned ${result.anomalyLabel}.` });
    });
  }

  function handleRegimeReplay() {
    if (!selectedStrategy) {
      setNotice({ tone: "error", message: "Select a strategy first." });
      return;
    }
    void runAction("regime-replay", async () => {
      // Trade markers are included only when a backtest has been run in this browser session.
      const replay = await runRegimeReplay({
        symbol: selectedStrategy.symbol,
        timeframe: selectedStrategy.timeframe,
        startDate: toInstant(backtestForm.startDate, "Replay start"),
        endDate: toInstant(backtestForm.endDate, "Replay end"),
        windowSize: 20,
        stride: 8,
        includeAnomalies: true,
        backtestId: backtestRun?.id ?? null,
      });
      setRegimeReplay(replay);
      setRegimeRobustness(await fetchRegimeRobustness(replay.id, backtestRun?.id ?? null));
      const latestWindow = replay.points.at(-1);
      if (latestWindow) {
        // Diagnostics are requested after replay so the evidence panel explains the same window the chart ends on.
        const diagnostics = await fetchMarketRegimeDiagnostics(
          selectedStrategy.symbol,
          selectedStrategy.timeframe,
          20,
          latestWindow.windowEnd,
        );
        setRegimeDiagnostics(diagnostics);
        setEvidenceSnapshots(await fetchRegimeEvidenceSnapshots(selectedStrategy.symbol, selectedStrategy.timeframe));
      }
      await fetchRegimeRuns(selectedStrategy.symbol, selectedStrategy.timeframe).then((data) =>
        setRegimeRunsState({ status: "success", data, error: null }),
      );
      await fetchRegimeRunComparison(selectedStrategy.symbol, selectedStrategy.timeframe).then((data) =>
        setRegimeComparisonState({ status: "success", data, error: null }),
      );
      setNotice({ tone: "success", message: `Regime replay loaded ${replay.pointCount} windows.` });
    });
  }

  function handleAssistantMessage(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void runAction("assistant-message", async () => {
      const session = assistantSession ?? await createAssistantSession();
      // The prompt carries the current workbench selection so proposals are concrete and reviewable.
      const updated = await sendAssistantMessage(session.id, {
        prompt: assistantPrompt,
        strategyId: selectedStrategyId,
        backtestId: backtestRun?.id ?? null,
        paperSessionId: selectedPaperSessionId,
        startDate: toInstant(backtestForm.startDate, "Assistant start"),
        endDate: toInstant(backtestForm.endDate, "Assistant end"),
      });
      setAssistantSession(updated);
      setAssistantPrompt("");
    });
  }

  function handleAssistantActionConfirm(action: AssistantAction) {
    void runAction(`assistant-confirm-${action.id}`, async () => {
      const updatedAction = await confirmAssistantAction(action.id);
      await refreshAfterAssistantAction(updatedAction);
      if (assistantSession) {
        setAssistantSession({
          ...assistantSession,
          actions: assistantSession.actions.map((existing) =>
            existing.id === updatedAction.id ? updatedAction : existing,
          ),
        });
      }
      setNotice({ tone: updatedAction.status === "EXECUTED" ? "success" : "error", message: assistantActionNotice(updatedAction) });
    });
  }

  function handleAssistantActionReject(action: AssistantAction) {
    void runAction(`assistant-reject-${action.id}`, async () => {
      const updatedAction = await rejectAssistantAction(action.id);
      if (assistantSession) {
        setAssistantSession({
          ...assistantSession,
          actions: assistantSession.actions.map((existing) =>
            existing.id === updatedAction.id ? updatedAction : existing,
          ),
        });
      }
      setNotice({ tone: "success", message: "Assistant action rejected." });
    });
  }

  async function refreshAfterAssistantAction(action: AssistantAction) {
    // Confirmed actions can update several panels, so reload the dashboard and targeted details.
    await loadDashboard();
    if (selectedPaperSessionId !== null && action.actionType.startsWith("REPLAY_PAPER")) {
      setPaperSummary(await fetchPaperSessionSummary(selectedPaperSessionId));
      setPaperOrders(await fetchPaperOrders(selectedPaperSessionId));
      setPaperPositions(await fetchPaperPositions(selectedPaperSessionId));
    }
  }

  return (
    <main className="app-shell" id="overview">
      <header className="dashboard-header">
        <div>
          <p className="eyebrow">SignalAttention local attention workbench</p>
          <h1>Attention regime workbench</h1>
        </div>
        <button className="button" disabled={loading} onClick={() => void loadDashboard()} type="button">
          {loading ? "Refreshing" : "Refresh"}
        </button>
      </header>
      <WorkbenchNav />

      {notice ? (
        <div aria-live="polite" className={`notice notice-${notice.tone}`}>
          {notice.message}
        </div>
      ) : null}

      <NextActionPanel action={nextAction} />
      <AssistantPanel
        busy={busyAction?.startsWith("assistant") ?? false}
        prompt={assistantPrompt}
        session={assistantSession}
        onConfirm={handleAssistantActionConfirm}
        onPrompt={setAssistantPrompt}
        onReject={handleAssistantActionReject}
        onSubmit={handleAssistantMessage}
      />
      <SummaryCards state={summaryState} />
      <RiskAlertsPanel state={riskAlertsState} />

      <section className="workflow-grid" id="workflow" aria-label="Attention workflow controls">
        <MarketDataImportPanel
          busy={busyAction === "import"}
          importSummary={importSummary}
          qualityState={marketDataQualityState}
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
          drawdownSeries={backtestDrawdown}
          equitySeries={backtestEquity}
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
      <StrategyComparisonPanel state={strategiesState} />
      <MarketRegimePanel state={regimeState} />
      <RegimeReplayPanel
        busy={busyAction === "regime-replay"}
        diagnostics={regimeDiagnostics}
        evidenceSnapshots={evidenceSnapshots}
        experimentsState={regimeExperimentsState}
        comparisonState={regimeComparisonState}
        replay={regimeReplay}
        robustness={regimeRobustness}
        runsState={regimeRunsState}
        selectedStrategy={selectedStrategy}
        statusState={regimeStatusState}
        onReplay={handleRegimeReplay}
      />
      <AnomalyPanel anomaly={anomaly} busy={busyAction === "anomaly"} onCheck={handleAnomalyCheck} />
      <AuditTimeline state={auditState} />
    </main>
  );
}

function AssistantPanel({
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

function RegimeReplayPanel({
  busy,
  diagnostics,
  evidenceSnapshots,
  experimentsState,
  comparisonState,
  replay,
  robustness,
  runsState,
  selectedStrategy,
  statusState,
  onReplay,
}: {
  busy: boolean;
  diagnostics: MarketRegimeDiagnostics | null;
  evidenceSnapshots: RegimeEvidenceSnapshot[];
  experimentsState: LoadState<MarketRegimeExperimentDiagnostics>;
  comparisonState: LoadState<RegimeRunComparison>;
  replay: RegimeRunResponse | null;
  robustness: RegimeRobustnessSummary | null;
  runsState: LoadState<RegimeRunSummary[]>;
  selectedStrategy: Strategy | null;
  statusState: LoadState<MarketRegimeStatus>;
  onReplay: () => void;
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
                ["Lift", formatRatioPercent(bestRun.liftOverBaseline)],
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

function AttentionEvidencePanel({
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

function assistantActionNotice(action: AssistantAction) {
  if (action.status === "EXECUTED") {
    return `${formatAction(action.actionType)} executed.`;
  }
  return action.failureMessage || `${formatAction(action.actionType)} failed.`;
}

function ModelStatusStrip({ state }: { state: LoadState<MarketRegimeStatus> }) {
  if (state.status === "loading") {
    return <p className="muted">Loading model status.</p>;
  }
  if (state.status === "error") {
    return <p className="error-text">{state.error}</p>;
  }
  const status = state.data;
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

function WorkbenchNav() {
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

function NextActionPanel({ action }: { action: WorkbenchAction }) {
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
              {/* Wider transparent targets make dense candle charts easier to inspect. */}
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

function MarketDataImportPanel({
  busy,
  importSummary,
  qualityState,
  onSubmit,
}: {
  busy: boolean;
  importSummary: MarketDataImportSummary | null;
  qualityState: LoadState<MarketDataQuality>;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <section className="panel" id="data">
      <SectionTitle title="Market data" status={qualityState.status === "success" && qualityState.data.candleCount > 0 ? "ready" : "needs data"} />
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
      ) : (
        <p className="muted">No import has run in this browser session.</p>
      )}
      {importSummary?.errors.length ? (
        <ul className="compact-list">
          {importSummary.errors.slice(0, 4).map((error) => (
            <li key={`${error.rowNumber}-${error.message}`}>Row {error.rowNumber}: {error.message}</li>
          ))}
        </ul>
      ) : null}
      <MarketDataQualityPanel state={qualityState} />
    </section>
  );
}

function SectionTitle({ title, status }: { title: string; status: string }) {
  return (
    <div className="section-title">
      <h2>{title}</h2>
      <span>{status}</span>
    </div>
  );
}

function MarketDataQualityPanel({ state }: { state: LoadState<MarketDataQuality> }) {
  if (state.status === "loading") {
    return <p className="muted">Checking BTC-USD 1h data quality.</p>;
  }
  if (state.status === "error") {
    return <p className="muted">{serviceErrorMessage(state.error, "backend")}</p>;
  }

  const quality = state.data;
  return (
    <div className="quality-summary" aria-label="Market data quality">
      <div className="series-heading">
        <h3>Data quality</h3>
        <strong>{quality.symbol} {quality.timeframe}</strong>
      </div>
      <ResultGrid
        items={[
          ["Candles", quality.candleCount],
          ["Gaps", quality.gapCount],
          ["Bad OHLC", quality.invalidOhlcCount],
          ["Bad volume", quality.zeroOrNegativeVolumeCount],
          ["Interval", `${quality.expectedIntervalMinutes}m`],
          ["Duplicates", quality.duplicateTimestampCount],
        ]}
      />
      <p className="muted quality-range">
        {quality.firstOpenTime && quality.lastOpenTime
          ? `${formatDateTime(quality.firstOpenTime)} to ${formatDateTime(quality.lastOpenTime)}`
          : "No candles found for the default market."}
      </p>
      <ul className="compact-list">
        {quality.warnings.slice(0, 4).map((warning) => (
          <li key={warning}>{warning}</li>
        ))}
      </ul>
    </div>
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
    <section className="panel" id="strategy">
      <SectionTitle title="Baseline strategy" status={selectedStrategyId ? "ready" : "needs setup"} />
      <label>
        Active baseline
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
      {state.status === "loading" ? <p className="muted">Loading saved strategies.</p> : null}
      {state.status === "error" ? <p className="muted">{serviceErrorMessage(state.error, "backend")}</p> : null}
      {state.status === "success" && state.data.length === 0 ? (
        <p className="muted">No saved baselines yet. Create the sample SMA baseline to unlock comparisons and paper sessions.</p>
      ) : null}
      <form className="control-stack" onSubmit={onSubmit}>
        <div className="form-grid">
          <TextInput label="Name" name="name" state={form} setState={onUpdate} />
          <TextInput label="Symbol" name="symbol" state={form} setState={onUpdate} />
          <TextInput label="Timeframe" name="timeframe" state={form} setState={onUpdate} />
        </div>
        <details className="advanced-controls">
          <summary>Baseline tuning</summary>
          <div className="form-grid">
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
        </details>
        <button className="button" disabled={busy} type="submit">
          {busy ? "Creating" : "Create SMA baseline"}
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
  drawdownSeries,
  equitySeries,
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
  drawdownSeries: BacktestDrawdownPoint[];
  equitySeries: BacktestEquityPoint[];
  trades: BacktestTrade[];
  onRiskScore: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onUpdate: (form: { startDate: string; endDate: string }) => void;
}) {
  return (
    <section className="panel" id="backtest">
      <SectionTitle title="Baseline comparison" status={backtestRun ? "complete" : selectedStrategy ? "ready" : "needs baseline"} />
      <p>
        {selectedStrategy
          ? `${selectedStrategy.symbol} ${selectedStrategy.timeframe}`
          : "Create or select a baseline first. Comparisons use the selected baseline rules."}
      </p>
      <form className="control-stack" onSubmit={onSubmit}>
        <div className="form-grid">
          <DateInput label="Start" name="startDate" state={form} setState={onUpdate} />
          <DateInput label="End" name="endDate" state={form} setState={onUpdate} />
        </div>
        <div className="button-row">
          <button className="button" disabled={busy || !selectedStrategy} type="submit">
            {busy ? "Working" : "Run comparison"}
          </button>
          <button className="button button-secondary" disabled={busy || !backtestRun} onClick={onRiskScore} type="button">
            Score baseline risk
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
          <EquityChart
            title="Equity"
            points={equitySeries.map((point) => ({
              timestamp: point.timestamp,
              value: point.equity,
            }))}
            formatValue={formatCurrency}
          />
          <SeriesChart
            title="Drawdown"
            points={drawdownSeries.map((point) => ({
              timestamp: point.timestamp,
              value: point.drawdownPercent,
            }))}
            formatValue={formatPercent}
          />
          <TradePreview trades={trades} />
        </>
      ) : null}
      {riskScore ? <ul className="compact-list">{riskScore.reasons.map((reason) => <li key={reason}>{reason}</li>)}</ul> : null}
      {!backtestRun ? <p className="muted">No backtest has run in this browser session. Import candles and choose a strategy first.</p> : null}
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
    <section className="panel" id="paper">
      <SectionTitle title="Paper trading" status={summary?.status.toLowerCase() || (selectedSessionId ? "ready" : "optional")} />
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
      {sessions.length === 0 ? <p className="muted">No paper sessions for the selected strategy. Create one after selecting a strategy.</p> : null}
      <form className="control-stack" onSubmit={onCreate}>
        <TextInput label="Initial balance" name="initialBalance" state={form} setState={onUpdate} type="number" />
        <button className="button" disabled={busy} type="submit">
          {busy ? "Working" : "Create session"}
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
      <details className="advanced-controls">
        <summary>Manual order</summary>
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
          {busy ? "Working" : "Submit order"}
        </button>
      </details>
      <details className="advanced-controls">
        <summary>Replay settings</summary>
        <div className="form-grid">
          <DateInput label="Replay start" name="startDate" state={form} setState={onUpdate} />
          <DateInput label="Replay end" name="endDate" state={form} setState={onUpdate} />
          <TextInput label="Max candles" name="maxCandles" state={form} setState={onUpdate} type="number" />
        </div>
      </details>
      <button className="button" disabled={busy || !selectedSessionId} onClick={onReplay} type="button">
        {busy ? "Working" : "Replay candles"}
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
      ) : (
        <p className="muted">No paper orders for the selected session.</p>
      )}
      {positions.length ? (
        <ul className="compact-list">
          {positions.slice(0, 4).map((position) => (
            <li key={position.id}>
              {position.status} {position.quantity} {position.symbol} at {formatCurrency(position.entryPrice)}
            </li>
          ))}
        </ul>
      ) : (
        <p className="muted">No paper positions for the selected session.</p>
      )}
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

function EquityChart({
  title,
  points,
  formatValue,
}: {
  title: string;
  points: Array<{ timestamp: string; value: number }>;
  formatValue: (value: number) => string;
}) {
  if (points.length === 0) {
    return (
      <ChartState
        title={`${title} chart unavailable`}
        message="Run a backtest with enough imported candles to populate this series."
      />
    );
  }

  const values = points.map((point) => point.value);
  const low = Math.min(...values);
  const high = Math.max(...values);
  const first = points[0];
  const last = points[points.length - 1];
  const data = points.map((point) => ({
    ...point,
    label: formatDateTime(point.timestamp),
  }));

  return (
    <ChartShell
      title={title}
      value={formatValue(last.value)}
      footer={
        <>
          <div className="series-meta">
            <span>{formatDateTime(first.timestamp)}</span>
            <span>{formatDateTime(last.timestamp)}</span>
          </div>
          <div className="series-summary" aria-label={`${title} chart summary`}>
            <span>Low {formatValue(low)}</span>
            <span>High {formatValue(high)}</span>
            <span>Latest {formatValue(last.value)}</span>
          </div>
        </>
      }
    >
      <AreaChart data={data} margin={{ top: 12, right: 12, bottom: 0, left: 0 }}>
        <defs>
          <linearGradient id="equity-fill" x1="0" x2="0" y1="0" y2="1">
            <stop offset="5%" stopColor="#0f766e" stopOpacity={0.28} />
            <stop offset="95%" stopColor="#0f766e" stopOpacity={0.02} />
          </linearGradient>
        </defs>
        <CartesianGrid stroke="var(--border)" strokeDasharray="4 5" vertical={false} />
        <XAxis dataKey="label" hide />
        <YAxis hide domain={["dataMin", "dataMax"]} />
        <Tooltip
          formatter={(value) => formatValue(Number(value))}
          labelFormatter={(label) => String(label)}
        />
        <Area
          dataKey="value"
          fill="url(#equity-fill)"
          isAnimationActive={false}
          stroke="#0f766e"
          strokeWidth={3}
          type="monotone"
        />
      </AreaChart>
    </ChartShell>
  );
}

function SeriesChart({
  title,
  points,
  formatValue,
}: {
  title: string;
  points: Array<{ timestamp: string; value: number }>;
  formatValue: (value: number) => string;
}) {
  if (points.length === 0) {
    return (
      <ChartState
        title={`${title} chart unavailable`}
        message="Run a backtest with enough imported candles to populate this series."
      />
    );
  }

  const values = points.map((point) => point.value);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const first = points[0];
  const last = points[points.length - 1];
  const low = min;
  const high = max;
  const data = points.map((point) => ({
    ...point,
    label: formatDateTime(point.timestamp),
  }));

  return (
    <ChartShell
      title={title}
      value={formatValue(last.value)}
      footer={
        <>
          <div className="series-meta">
            <span>{formatDateTime(first.timestamp)}</span>
            <span>{formatDateTime(last.timestamp)}</span>
          </div>
          <div className="series-summary" aria-label={`${title} chart summary`}>
            <span>Low {formatValue(low)}</span>
            <span>High {formatValue(high)}</span>
            <span>Latest {formatValue(last.value)}</span>
          </div>
        </>
      }
    >
      <AreaChart data={data} margin={{ top: 12, right: 12, bottom: 0, left: 0 }}>
        <defs>
          <linearGradient id="drawdown-fill" x1="0" x2="0" y1="0" y2="1">
            <stop offset="5%" stopColor="#b42318" stopOpacity={0.28} />
            <stop offset="95%" stopColor="#b42318" stopOpacity={0.02} />
          </linearGradient>
        </defs>
        <CartesianGrid stroke="var(--border)" strokeDasharray="4 5" vertical={false} />
        <XAxis dataKey="label" hide />
        <YAxis hide domain={["dataMin", "dataMax"]} />
        <Tooltip
          formatter={(value) => formatValue(Number(value))}
          labelFormatter={(label) => String(label)}
        />
        <Area
          dataKey="value"
          fill="url(#drawdown-fill)"
          isAnimationActive={false}
          stroke="#b42318"
          strokeWidth={3}
          type="monotone"
        />
      </AreaChart>
    </ChartShell>
  );
}

function TradePreview({ trades }: { trades: BacktestTrade[] }) {
  if (trades.length === 0) {
    return <p>No trades were generated for this backtest.</p>;
  }
  return (
    <div className="trade-detail">
      <h3>Trades</h3>
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              <th>Side</th>
              <th>Entry</th>
              <th>Exit</th>
              <th>Fees</th>
              <th>Net P&L</th>
              <th>Return</th>
            </tr>
          </thead>
          <tbody>
            {trades.slice(0, 8).map((trade) => (
              <tr key={trade.id}>
                <td>{trade.side}</td>
                <td>
                  {formatCurrency(trade.entryPrice)}
                  <small>{formatDateTime(trade.entryTime)}</small>
                </td>
                <td>
                  {trade.exitPrice === null ? "Open" : formatCurrency(trade.exitPrice)}
                  {trade.exitTime ? <small>{formatDateTime(trade.exitTime)}</small> : null}
                </td>
                <td>{formatCurrency(trade.fees)}</td>
                <td>{formatCurrency(trade.netPnl)}</td>
                <td>{formatPercent(trade.returnPercent)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {trades.length > 8 ? <p className="muted">Showing 8 of {trades.length} trades.</p> : null}
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

function RiskAlertsPanel({ state }: { state: LoadState<DashboardRiskAlert[]> }) {
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

function StrategyTable({ state }: { state: LoadState<StrategyPerformance[]> }) {
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

function StrategyComparisonPanel({ state }: { state: LoadState<StrategyPerformance[]> }) {
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

function AuditTimeline({ state }: { state: LoadState<AuditEvent[]> }) {
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

function MarketRegimePanel({ state }: { state: LoadState<MarketRegimeResponse> }) {
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

function AnomalyPanel({
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

function serviceErrorMessage(message: string, service: "backend" | "ml") {
  const serviceName = service === "backend" ? "backend API" : "ML service";
  return `${message} Check that the ${serviceName} is running, then refresh this dashboard.`;
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

export function deriveWorkbenchAction({
  candleCount,
  strategyCount,
  hasBacktest,
  hasRiskScore,
  hasRegimeReplay,
}: {
  candleCount: number;
  strategyCount: number;
  hasBacktest: boolean;
  hasRiskScore: boolean;
  hasRegimeReplay: boolean;
}): WorkbenchAction {
  if (candleCount === 0) {
    return {
      id: "import-data",
      title: "Import market data",
      detail: "Load the BTC-USD sample candles so baselines, comparisons, and attention analysis have data.",
    };
  }
  if (strategyCount === 0) {
    return {
      id: "create-strategy",
      title: "Create the SMA baseline",
      detail: "Use the default BTC-USD 1h SMA crossover setup as the traditional comparison baseline.",
    };
  }
  if (!hasBacktest) {
    return {
      id: "run-backtest",
      title: "Run baseline comparison",
      detail: "Generate return, drawdown, trade, equity, and risk inputs for the selected baseline.",
    };
  }
  if (!hasRiskScore) {
    return {
      id: "score-risk",
      title: "Score baseline risk",
      detail: "Persist an explainable risk label so the baseline comparison and alerts can reflect it.",
    };
  }
  if (!hasRegimeReplay) {
    return {
      id: "run-analysis",
      title: "Run attention replay",
      detail: "Show candle context with attention-derived regime windows and baseline trade markers.",
    };
  }
  return {
    id: "review-results",
    title: "Review results",
    detail: "The main demo path is ready: review attention evidence, model lab diagnostics, robustness, and baseline comparisons.",
  };
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

function formatRatioPercent(value: number | null | undefined) {
  if (value === null || value === undefined) {
    return "N/A";
  }
  return `${(Number(value) * 100).toFixed(2)}%`;
}

function promotionValue(source: Record<string, unknown> | null | undefined, path: string) {
  if (!source) {
    return null;
  }
  const value = path.split(".").reduce<unknown>((current, key) => {
    if (current && typeof current === "object" && key in current) {
      return (current as Record<string, unknown>)[key];
    }
    return null;
  }, source);
  return typeof value === "string" ? value : null;
}

function shortModelLabel(run: RegimeRunSummary) {
  if (run.artifactIdentifier) {
    return run.artifactIdentifier.slice(0, 8);
  }
  return run.modelVersion || run.classifierSource || "rules";
}

function formatComparisonDelta(delta: RegimeRunComparisonDelta | null | undefined) {
  if (!delta) {
    return "baseline";
  }
  const markers = [
    delta.averageConfidenceDelta === null || delta.averageConfidenceDelta === undefined
      ? null
      : `conf ${formatSignedPercent(delta.averageConfidenceDelta)}`,
    delta.baselineDisagreementRateDelta === null || delta.baselineDisagreementRateDelta === undefined
      ? null
      : `gap ${formatSignedPercent(delta.baselineDisagreementRateDelta)}`,
    delta.pointCountDelta === null || delta.pointCountDelta === undefined ? null : `windows ${formatSignedNumber(delta.pointCountDelta)}`,
    delta.modeChanged ? "mode changed" : null,
    delta.artifactChanged ? "artifact changed" : null,
  ].filter(Boolean);
  return markers.length ? markers.join(" | ") : "no material change";
}

function formatSignedPercent(value: number) {
  return `${value >= 0 ? "+" : ""}${value.toFixed(2)}%`;
}

function formatSignedNumber(value: number) {
  return `${value >= 0 ? "+" : ""}${value}`;
}

function formatNumber(value: number | null | undefined) {
  if (value === null || value === undefined) {
    return "N/A";
  }
  return Number(value).toFixed(2);
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
