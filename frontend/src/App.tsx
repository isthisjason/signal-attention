import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { AnomalyResponse, checkAnomaly } from "./api/anomaly";
import {
  AssistantAction,
  AssistantSession,
  confirmAssistantAction,
  createAssistantSession,
  rejectAssistantAction,
  sendAssistantMessage,
} from "./api/assistant";
import { AttentionShowcaseSummary, fetchAttentionShowcaseSummary } from "./api/attentionShowcase";
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
  MarketRegimeDiagnostics,
  MarketRegimeExperimentDiagnostics,
  MarketRegimeResponse,
  MarketRegimeStatus,
  RegimeEvidenceSnapshot,
  RegimeRunComparison,
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
import { LoadState } from "./workbench/types";
import { deriveWorkbenchAction, selectPaperSessionId, toInstant } from "./workbench/workflow";
import {
  BacktestWorkflowPanel,
  MarketDataImportPanel,
  PaperTradingPanel,
  StrategyFormState,
  StrategyWorkflowPanel,
} from "./workbench/BaselineWorkflow";
import {
  AssistantPanel,
  AttentionShowcasePanel,
  NextActionPanel,
  RegimeReplayPanel,
  WorkbenchNav,
  assistantActionNotice,
} from "./workbench/AttentionReview";
import {
  AnomalyPanel,
  AuditTimeline,
  MarketRegimePanel,
  RiskAlertsPanel,
  StrategyComparisonPanel,
  StrategyTable,
  SummaryCards,
} from "./workbench/DashboardPanels";

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
const loadingAttentionShowcase: LoadState<AttentionShowcaseSummary> = {
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

const defaultStart = "2024-01-01T00:00";
const defaultEnd = "2024-01-10T00:00";

function App() {
  const [summaryState, setSummaryState] = useState<LoadState<DashboardSummary>>(loadingSummary);
  const [strategiesState, setStrategiesState] =
    useState<LoadState<StrategyPerformance[]>>(loadingStrategies);
  const [auditState, setAuditState] = useState<LoadState<AuditEvent[]>>(loadingAuditEvents);
  const [riskAlertsState, setRiskAlertsState] =
    useState<LoadState<DashboardRiskAlert[]>>(loadingRiskAlerts);
  const [attentionShowcaseState, setAttentionShowcaseState] =
    useState<LoadState<AttentionShowcaseSummary>>(loadingAttentionShowcase);
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
  const [selectedRegimeWindowEnd, setSelectedRegimeWindowEnd] = useState<string | null>(null);
  const [evidenceSnapshots, setEvidenceSnapshots] = useState<RegimeEvidenceSnapshot[]>([]);
  const [assistantSession, setAssistantSession] = useState<AssistantSession | null>(null);
  const [assistantPrompt, setAssistantPrompt] = useState("What should I inspect next?");

  const loadDashboard = useCallback(async () => {
    // Refresh each panel independently so one failed endpoint does not blank the whole dashboard.
    setSummaryState(loadingSummary);
    setStrategiesState(loadingStrategies);
    setAuditState(loadingAuditEvents);
    setRiskAlertsState(loadingRiskAlerts);
    setAttentionShowcaseState(loadingAttentionShowcase);
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
    const attentionShowcaseLoad = fetchAttentionShowcaseSummary()
      .then((data) => setAttentionShowcaseState({ status: "success", data, error: null }))
      .catch((error: unknown) =>
        setAttentionShowcaseState({ status: "error", data: null, error: errorMessage(error) }),
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
      attentionShowcaseLoad,
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
    attentionShowcaseState.status === "loading" ||
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
      // This is a little repetitive, but it keeps the paper panel honest after replay mutates session state.
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
      // The showcase story is strongest when replay and the latest backtest cover the same dates.
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
        setSelectedRegimeWindowEnd(latestWindow.windowEnd);
        // Pulling diagnostics for the last window gives the evidence panel a concrete point to talk about.
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

  function handleRegimeWindowSelect(point: RegimeRunResponse["points"][number]) {
    if (!selectedStrategy) {
      return;
    }
    void runAction("regime-window", async () => {
      setSelectedRegimeWindowEnd(point.windowEnd);
      const diagnostics = await fetchMarketRegimeDiagnostics(
        selectedStrategy.symbol,
        selectedStrategy.timeframe,
        20,
        point.windowEnd,
      );
      setRegimeDiagnostics(diagnostics);
      setEvidenceSnapshots(await fetchRegimeEvidenceSnapshots(selectedStrategy.symbol, selectedStrategy.timeframe));
      setNotice({ tone: "success", message: `Loaded evidence for ${new Date(point.windowEnd).toLocaleString()}.` });
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
      <AttentionShowcasePanel state={attentionShowcaseState} />
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
        selectedWindowEnd={selectedRegimeWindowEnd}
        selectedStrategy={selectedStrategy}
        statusState={regimeStatusState}
        onReplay={handleRegimeReplay}
        onSelectWindow={handleRegimeWindowSelect}
      />
      <AnomalyPanel anomaly={anomaly} busy={busyAction === "anomaly"} onCheck={handleAnomalyCheck} />
      <AuditTimeline state={auditState} />
    </main>
  );
}

export default App;
