import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";

const mocks = vi.hoisted(() => ({
  fetchAuditEvents: vi.fn(),
  fetchBacktest: vi.fn(),
  fetchBacktestDrawdownSeries: vi.fn(),
  fetchBacktestEquitySeries: vi.fn(),
  fetchBacktestTrades: vi.fn(),
  fetchDashboardRiskAlerts: vi.fn(),
  fetchDashboardSummary: vi.fn(),
  fetchMarketDataQuality: vi.fn(),
  fetchMarketRegime: vi.fn(),
  importMarketData: vi.fn(),
  runRegimeReplay: vi.fn(),
  createStrategy: vi.fn(),
  createPaperSession: vi.fn(),
  fetchPaperOrders: vi.fn(),
  fetchPaperPositions: vi.fn(),
  fetchPaperSessionSummary: vi.fn(),
  fetchStrategies: vi.fn(),
  fetchStrategyPaperSessions: vi.fn(),
  fetchStrategyPerformance: vi.fn(),
  replayPaperSession: vi.fn(),
  runBacktest: vi.fn(),
  scoreBacktestRisk: vi.fn(),
  startPaperSession: vi.fn(),
  stopPaperSession: vi.fn(),
  submitPaperOrder: vi.fn(),
}));

vi.mock("./api/backtests", () => ({
  fetchBacktest: mocks.fetchBacktest,
  fetchBacktestDrawdownSeries: mocks.fetchBacktestDrawdownSeries,
  fetchBacktestEquitySeries: mocks.fetchBacktestEquitySeries,
  fetchBacktestTrades: mocks.fetchBacktestTrades,
  runBacktest: mocks.runBacktest,
  scoreBacktestRisk: mocks.scoreBacktestRisk,
}));

vi.mock("./api/audit", () => ({
  fetchAuditEvents: mocks.fetchAuditEvents,
}));

vi.mock("./api/dashboard", () => ({
  fetchDashboardRiskAlerts: mocks.fetchDashboardRiskAlerts,
  fetchDashboardSummary: mocks.fetchDashboardSummary,
  fetchStrategyPerformance: mocks.fetchStrategyPerformance,
}));

vi.mock("./api/marketRegime", () => ({
  fetchMarketRegime: mocks.fetchMarketRegime,
  runRegimeReplay: mocks.runRegimeReplay,
}));

vi.mock("./api/marketData", () => ({
  fetchMarketDataQuality: mocks.fetchMarketDataQuality,
  importMarketData: mocks.importMarketData,
}));

vi.mock("./api/paperTrading", () => ({
  createPaperSession: mocks.createPaperSession,
  fetchPaperOrders: mocks.fetchPaperOrders,
  fetchPaperPositions: mocks.fetchPaperPositions,
  fetchPaperSessionSummary: mocks.fetchPaperSessionSummary,
  fetchStrategyPaperSessions: mocks.fetchStrategyPaperSessions,
  replayPaperSession: mocks.replayPaperSession,
  startPaperSession: mocks.startPaperSession,
  stopPaperSession: mocks.stopPaperSession,
  submitPaperOrder: mocks.submitPaperOrder,
}));

vi.mock("./api/strategies", () => ({
  createStrategy: mocks.createStrategy,
  fetchStrategies: mocks.fetchStrategies,
}));

beforeEach(() => {
  mocks.fetchDashboardSummary.mockResolvedValue({
    strategyCount: 0,
    backtestCount: 0,
    activePaperSessionCount: 0,
    latestBacktest: null,
    recentAuditEvents: [],
  });
  mocks.fetchStrategyPerformance.mockResolvedValue([]);
  mocks.fetchDashboardRiskAlerts.mockResolvedValue([]);
  mocks.fetchAuditEvents.mockResolvedValue([]);
  mocks.fetchMarketDataQuality.mockResolvedValue({
    symbol: "BTC-USD",
    timeframe: "1h",
    candleCount: 240,
    firstOpenTime: "2024-01-01T00:00:00Z",
    lastOpenTime: "2024-01-10T23:00:00Z",
    expectedIntervalMinutes: 60,
    duplicateTimestampCount: 0,
    gapCount: 0,
    invalidOhlcCount: 0,
    zeroOrNegativeVolumeCount: 0,
    warnings: ["No market data quality warnings found."],
  });
  mocks.runBacktest.mockResolvedValue({
    id: 12,
    strategyId: 1,
    startDate: "2024-01-01T00:00:00Z",
    endDate: "2024-01-03T00:00:00Z",
    initialBalance: 10000,
    finalBalance: 10300,
    totalReturn: 3,
    maxDrawdown: 1.2,
    winRate: 50,
    profitFactor: 1.5,
    tradeCount: 1,
    averageTradeReturn: 3,
    feeDrag: 10,
    volatility: 0.5,
    mlRiskScore: null,
    mlRiskLabel: null,
    status: "COMPLETED",
    createdAt: "2024-01-03T00:00:00Z",
    completedAt: "2024-01-03T00:00:01Z",
  });
  mocks.fetchBacktestTrades.mockResolvedValue([]);
  mocks.fetchBacktestEquitySeries.mockResolvedValue([
    { timestamp: "2024-01-01T00:00:00Z", equity: 10000 },
    { timestamp: "2024-01-02T00:00:00Z", equity: 9800 },
    { timestamp: "2024-01-03T00:00:00Z", equity: 10300 },
  ]);
  mocks.fetchBacktestDrawdownSeries.mockResolvedValue([
    { timestamp: "2024-01-01T00:00:00Z", drawdownPercent: 0 },
    { timestamp: "2024-01-02T00:00:00Z", drawdownPercent: 2 },
    { timestamp: "2024-01-03T00:00:00Z", drawdownPercent: 0 },
  ]);
  mocks.fetchBacktest.mockResolvedValue(null);
  mocks.scoreBacktestRisk.mockResolvedValue(null);
  mocks.fetchMarketRegime.mockRejectedValue(new Error("Not enough candles"));
  mocks.runRegimeReplay.mockResolvedValue({
    symbol: "BTC-USD",
    timeframe: "1h",
    windowSize: 64,
    stride: 8,
    includeAnomalies: true,
    pointCount: 1,
    candles: [
      {
        openTime: "2024-01-01T00:00:00Z",
        openPrice: 40000,
        high: 41000,
        low: 39500,
        close: 40500,
        volume: 100,
      },
      {
        openTime: "2024-01-01T01:00:00Z",
        openPrice: 40500,
        high: 41500,
        low: 40200,
        close: 39800,
        volume: 120,
      },
    ],
    points: [
      {
        windowStart: "2024-01-01T00:00:00Z",
        windowEnd: "2024-01-01T00:00:00Z",
        regimeLabel: "TRENDING_UP",
        confidence: 75,
        reasons: ["Trend is rising."],
      },
    ],
    tradeMarkers: [
      {
        tradeId: 7,
        side: "BUY",
        entryTime: "2024-01-01T00:00:00Z",
        entryPrice: 40500,
      },
    ],
  });
  mocks.importMarketData.mockResolvedValue({
    totalRows: 3,
    rowsImported: 3,
    rowsRejected: 0,
    errors: [],
  });
  mocks.createStrategy.mockResolvedValue({
    id: 42,
    name: "BTC SMA Crossover",
    symbol: "BTC-USD",
    timeframe: "1h",
    strategyType: "SMA_CROSSOVER",
    status: "ACTIVE",
    rules: {
      shortWindow: 3,
      longWindow: 5,
      initialBalance: 10000,
      feePercent: 0.1,
      positionSizePercent: 50,
    },
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z",
  });
  mocks.fetchStrategies.mockResolvedValue([]);
  mocks.fetchStrategyPaperSessions.mockResolvedValue([]);
  mocks.createPaperSession.mockResolvedValue({
    id: 9,
    strategyId: 1,
    status: "CREATED",
    initialBalance: 100000,
    cashBalance: 100000,
    createdAt: "2024-01-01T00:00:00Z",
    startedAt: null,
    stoppedAt: null,
  });
  mocks.startPaperSession.mockResolvedValue({
    id: 9,
    strategyId: 1,
    status: "RUNNING",
    initialBalance: 100000,
    cashBalance: 100000,
    createdAt: "2024-01-01T00:00:00Z",
    startedAt: "2024-01-01T00:00:00Z",
    stoppedAt: null,
  });
  mocks.stopPaperSession.mockResolvedValue({
    id: 9,
    strategyId: 1,
    status: "STOPPED",
    initialBalance: 100000,
    cashBalance: 100000,
    createdAt: "2024-01-01T00:00:00Z",
    startedAt: "2024-01-01T00:00:00Z",
    stoppedAt: "2024-01-01T01:00:00Z",
  });
  mocks.submitPaperOrder.mockResolvedValue({
    id: 17,
    sessionId: 9,
    side: "BUY",
    symbol: "BTC-USD",
    quantity: 0.25,
    price: 42000,
    notional: 10500,
    status: "FILLED",
    rejectionReason: null,
    createdAt: "2024-01-01T00:00:00Z",
  });
  mocks.replayPaperSession.mockResolvedValue({
    candlesRead: 10,
    signalsProcessed: 2,
    filledOrders: 1,
    rejectedOrders: 0,
  });
  mocks.fetchPaperSessionSummary.mockResolvedValue({
    status: "RUNNING",
    cashBalance: 89500,
    totalEquity: 100500,
    openPositionValue: 11000,
    unrealizedPnl: 500,
    openPositions: 1,
  });
  mocks.fetchPaperOrders.mockResolvedValue([]);
  mocks.fetchPaperPositions.mockResolvedValue([]);
});

describe("dashboard render states", () => {
  it("renders empty workflow state without enabling dependent actions", async () => {
    render(<App />);

    expect(await screen.findByText(/No strategies have been created yet/)).toBeInTheDocument();
    expect(screen.getByText(/No saved strategies yet/)).toBeInTheDocument();
    expect(screen.getByText("No import has run in this browser session.")).toBeInTheDocument();
    expect(screen.getByLabelText("Market data quality")).toHaveTextContent("No market data quality warnings found.");
    expect(screen.getByText(/No backtest has run in this browser session/)).toBeInTheDocument();
    expect(screen.getByText(/No paper sessions for the selected strategy/)).toBeInTheDocument();
    expect(screen.getByText("No paper orders for the selected session.")).toBeInTheDocument();
    expect(screen.getByText("No paper positions for the selected session.")).toBeInTheDocument();
    expect(screen.getByText("No assessment chart yet")).toBeInTheDocument();
    expect(screen.getByText("No anomaly check yet")).toBeInTheDocument();
    expect(screen.getByText("No risk alerts are active.")).toBeInTheDocument();
    expect(screen.getByText("No audit events have been recorded yet.")).toBeInTheDocument();
    expect(screen.getByText(/Import at least 20 BTC-USD 1h candles/)).toBeInTheDocument();

    expect(screen.getByRole("button", { name: "Run backtest" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Score ML risk" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Start" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Stop" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Submit order" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Replay candles" })).toBeDisabled();
  });

  it("renders practical service errors when the backend is unavailable", async () => {
    mocks.fetchDashboardSummary.mockRejectedValue(new Error("Failed to fetch"));
    mocks.fetchStrategyPerformance.mockRejectedValue(new Error("Failed to fetch"));
    mocks.fetchDashboardRiskAlerts.mockRejectedValue(new Error("Failed to fetch"));
    mocks.fetchAuditEvents.mockRejectedValue(new Error("Failed to fetch"));
    mocks.fetchStrategies.mockRejectedValue(new Error("Failed to fetch"));
    mocks.fetchMarketDataQuality.mockRejectedValue(new Error("Failed to fetch"));

    render(<App />);

    expect(await screen.findAllByText(/Check that the backend API is running/)).toHaveLength(6);
  });

  it("renders market regime provenance when it is present", async () => {
    mocks.fetchMarketRegime.mockResolvedValue({
      regimeLabel: "TRENDING_UP",
      confidence: 81.25,
      reasons: ["Torch sequence model selected TRENDING_UP."],
      features: {
        latestReturnPercent: 0.5,
        averageReturnPercent: 0.25,
        volatilityPercent: 0.1,
        trendSlopePercent: 0.2,
        smaDistancePercent: 1.5,
        volumeZScore: 0,
      },
      classifierSource: "torch",
      mode: "torch",
      modelVersion: "local-transformer-v1",
      featureVersion: "torch-market-regime-features/v1",
      sequenceLength: 20,
      artifactIdentifier: "market-regime.pt",
    });

    render(<App />);

    expect(await screen.findByText("local-transformer-v1")).toBeInTheDocument();
    expect(screen.getByText("market-regime.pt")).toBeInTheDocument();
    expect(screen.getByText("torch-market-regime-features/v1")).toBeInTheDocument();
  });

  it("renders dashboard risk alerts when present", async () => {
    mocks.fetchDashboardRiskAlerts.mockResolvedValue([
      {
        severity: "HIGH",
        category: "DRAWDOWN",
        entityType: "BACKTEST",
        entityId: "10",
        message: "Backtest drawdown reached 20%.",
        createdAt: "2024-01-01T00:00:00Z",
      },
    ]);

    render(<App />);

    expect(await screen.findByText("Backtest drawdown reached 20%.")).toBeInTheDocument();
    expect(screen.getByText("high")).toBeInTheDocument();
    expect(screen.getByText(/BACKTEST #10/)).toBeInTheDocument();
  });

  it("renders strategy comparison when multiple backtested strategies exist", async () => {
    mocks.fetchStrategyPerformance.mockResolvedValue([
      {
        strategyId: 1,
        name: "BTC SMA",
        symbol: "BTC-USD",
        timeframe: "1h",
        status: "ACTIVE",
        latestBacktestId: 10,
        latestTotalReturn: 12,
        latestMaxDrawdown: 4,
        latestTradeCount: 5,
        latestMlRiskScore: 40,
        latestMlRiskLabel: "MEDIUM_RISK",
        latestBacktestCreatedAt: "2024-01-01T00:00:00Z",
        paperSessionCount: 1,
      },
      {
        strategyId: 2,
        name: "ETH SMA",
        symbol: "ETH-USD",
        timeframe: "1h",
        status: "ACTIVE",
        latestBacktestId: 11,
        latestTotalReturn: 8,
        latestMaxDrawdown: 2,
        latestTradeCount: 9,
        latestMlRiskScore: 20,
        latestMlRiskLabel: "LOW_RISK",
        latestBacktestCreatedAt: "2024-01-02T00:00:00Z",
        paperSessionCount: 0,
      },
    ]);

    render(<App />);

    expect(await screen.findByText("Strategy comparison")).toBeInTheDocument();
    expect(screen.getByText("Best return")).toBeInTheDocument();
    expect(screen.getByText("Lowest drawdown")).toBeInTheDocument();
    expect(screen.getByText("Most trades")).toBeInTheDocument();
  });

  it("renders market regime without provenance when optional fields are absent", async () => {
    mocks.fetchMarketRegime.mockResolvedValue({
      regimeLabel: "SIDEWAYS",
      confidence: 65,
      reasons: ["Trend and volatility signals are muted."],
      features: {
        latestReturnPercent: 0,
        averageReturnPercent: 0,
        volatilityPercent: 0.1,
        trendSlopePercent: 0,
        smaDistancePercent: 0,
        volumeZScore: 0,
      },
      classifierSource: "rules",
    });

    render(<App />);

    expect(await screen.findByText("sideways")).toBeInTheDocument();
    expect(screen.getByText("rules")).toBeInTheDocument();
    expect(screen.queryByText("Model")).not.toBeInTheDocument();
    expect(screen.queryByText("Artifact")).not.toBeInTheDocument();
  });

  it("renders candlestick replay context and selectable candle details", async () => {
    const user = userEvent.setup();
    mocks.fetchStrategies.mockResolvedValue([
      {
        id: 1,
        name: "BTC SMA",
        symbol: "BTC-USD",
        timeframe: "1h",
        strategyType: "SMA_CROSSOVER",
        status: "ACTIVE",
        rules: {
          shortWindow: 3,
          longWindow: 5,
          initialBalance: 10000,
          feePercent: 0.1,
          positionSizePercent: 50,
        },
        createdAt: "2024-01-01T00:00:00Z",
        updatedAt: "2024-01-01T00:00:00Z",
      },
    ]);

    render(<App />);

    await user.click(await screen.findByRole("button", { name: "Run replay" }));

    expect(await screen.findByRole("img", { name: "Regime replay candlestick chart" })).toBeInTheDocument();
    const legend = screen.getByLabelText("Candlestick chart legend");
    expect(legend).toBeInTheDocument();
    expect(within(legend).getByText("Up candle")).toBeInTheDocument();
    expect(within(legend).getByText("Down candle")).toBeInTheDocument();
    expect(within(legend).getByText("Buy")).toBeInTheDocument();
    expect(screen.getByLabelText("TRENDING_UP regime marker")).toBeInTheDocument();
    expect(screen.getByLabelText("buy trade marker")).toBeInTheDocument();
    expect(screen.getByText("O $40,000.00")).toBeInTheDocument();

    await user.hover(screen.getByLabelText(/open \$40,500.00 high \$41,500.00/));

    expect(screen.getByText("O $40,500.00")).toBeInTheDocument();
    expect(screen.getByText("C $39,800.00")).toBeInTheDocument();
    expect(screen.getByText("no regime window")).toBeInTheDocument();
  });

  it("renders backtest chart summaries after a run", async () => {
    const user = userEvent.setup();
    mocks.fetchStrategies.mockResolvedValue([
      {
        id: 1,
        name: "BTC SMA",
        symbol: "BTC-USD",
        timeframe: "1h",
        strategyType: "SMA_CROSSOVER",
        status: "ACTIVE",
        rules: {
          shortWindow: 3,
          longWindow: 5,
          initialBalance: 10000,
          feePercent: 0.1,
          positionSizePercent: 50,
        },
        createdAt: "2024-01-01T00:00:00Z",
        updatedAt: "2024-01-01T00:00:00Z",
      },
    ]);

    render(<App />);

    await user.click(await screen.findByRole("button", { name: "Run backtest" }));

    expect(await screen.findByRole("img", { name: "Equity chart" })).toBeInTheDocument();
    expect(screen.getByLabelText("Equity chart summary")).toHaveTextContent("Low $9,800.00");
    expect(screen.getByLabelText("Equity chart summary")).toHaveTextContent("High $10,300.00");
    expect(screen.getByLabelText("Drawdown chart summary")).toHaveTextContent("High 2.00%");
  });

  it("runs a backtest and scores ML risk from the completed run", async () => {
    const user = userEvent.setup();
    mocks.fetchStrategies.mockResolvedValue([
      {
        id: 1,
        name: "BTC SMA",
        symbol: "BTC-USD",
        timeframe: "1h",
        strategyType: "SMA_CROSSOVER",
        status: "ACTIVE",
        rules: {
          shortWindow: 3,
          longWindow: 5,
          initialBalance: 10000,
          feePercent: 0.1,
          positionSizePercent: 50,
        },
        createdAt: "2024-01-01T00:00:00Z",
        updatedAt: "2024-01-01T00:00:00Z",
      },
    ]);
    mocks.scoreBacktestRisk.mockResolvedValue({
      riskScore: 72,
      riskLabel: "HIGH_RISK",
      reasons: ["High drawdown increases capital risk."],
    });
    mocks.fetchBacktest.mockResolvedValue({
      id: 12,
      strategyId: 1,
      startDate: "2024-01-01T00:00:00Z",
      endDate: "2024-01-03T00:00:00Z",
      initialBalance: 10000,
      finalBalance: 10300,
      totalReturn: 3,
      maxDrawdown: 1.2,
      winRate: 50,
      profitFactor: 1.5,
      tradeCount: 1,
      averageTradeReturn: 3,
      feeDrag: 10,
      volatility: 0.5,
      mlRiskScore: 72,
      mlRiskLabel: "HIGH_RISK",
      status: "COMPLETED",
      createdAt: "2024-01-03T00:00:00Z",
      completedAt: "2024-01-03T00:00:01Z",
    });

    render(<App />);

    expect(await screen.findByRole("button", { name: "Run backtest" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "Score ML risk" })).toBeDisabled();

    await user.click(screen.getByRole("button", { name: "Run backtest" }));

    expect(await screen.findByText("Backtest #12 completed.")).toBeInTheDocument();
    expect(mocks.runBacktest).toHaveBeenCalledWith(1, expect.objectContaining({
      startDate: expect.stringMatching(/2024-01-01T\d{2}:00:00.000Z/),
      endDate: expect.stringMatching(/2024-01-10T\d{2}:00:00.000Z/),
    }));
    expect(mocks.fetchBacktestEquitySeries).toHaveBeenCalledWith(12);
    expect(mocks.fetchBacktestDrawdownSeries).toHaveBeenCalledWith(12);

    await user.click(screen.getByRole("button", { name: "Score ML risk" }));

    expect(await screen.findByText("Risk score saved as HIGH_RISK.")).toBeInTheDocument();
    expect(mocks.scoreBacktestRisk).toHaveBeenCalledWith(12);
    expect(mocks.fetchBacktest).toHaveBeenCalledWith(12);
    expect(screen.getByText("High drawdown increases capital risk.")).toBeInTheDocument();
  });

  it("renders CSV import success state", async () => {
    const user = userEvent.setup();
    render(<App />);

    const input = await screen.findByLabelText("Candle CSV");
    await user.upload(input, new File(["openTime,open"], "candles.csv", { type: "text/csv" }));
    await user.click(screen.getByRole("button", { name: "Import CSV" }));

    expect(await screen.findByText("Imported 3 candle rows.")).toBeInTheDocument();
    expect(screen.getByText("Rows read")).toBeInTheDocument();
    expect(screen.getByText("Imported")).toBeInTheDocument();
    expect(mocks.importMarketData).toHaveBeenCalledWith(expect.objectContaining({ name: "candles.csv" }));
    expect(mocks.fetchMarketDataQuality).toHaveBeenCalled();
  });

  it("renders CSV import validation and API errors", async () => {
    const user = userEvent.setup();
    render(<App />);

    await user.click(await screen.findByRole("button", { name: "Import CSV" }));
    expect(screen.getByText("Choose a CSV file before importing.")).toBeInTheDocument();

    mocks.importMarketData.mockRejectedValueOnce(new Error("CSV parse failed"));
    await user.upload(screen.getByLabelText("Candle CSV"), new File(["bad"], "bad.csv", { type: "text/csv" }));
    await user.click(screen.getByRole("button", { name: "Import CSV" }));

    expect(await screen.findByText("CSV parse failed")).toBeInTheDocument();
  });

  it("creates the default SMA strategy and refreshes selected strategy state", async () => {
    const user = userEvent.setup();
    render(<App />);

    const dashboardCallsBeforeCreate = mocks.fetchDashboardSummary.mock.calls.length;
    const strategyCallsBeforeCreate = mocks.fetchStrategies.mock.calls.length;
    await user.click(await screen.findByRole("button", { name: "Create SMA strategy" }));

    expect(await screen.findByText("Created strategy #42.")).toBeInTheDocument();
    expect(mocks.createStrategy).toHaveBeenCalledWith({
      name: "BTC SMA Crossover",
      symbol: "BTC-USD",
      timeframe: "1h",
      strategyType: "SMA_CROSSOVER",
      rules: {
        shortWindow: 3,
        longWindow: 5,
        initialBalance: 10000,
        feePercent: 0.1,
        positionSizePercent: 50,
      },
    });
    expect(mocks.fetchDashboardSummary.mock.calls.length).toBeGreaterThan(dashboardCallsBeforeCreate);
    expect(mocks.fetchStrategies.mock.calls.length).toBeGreaterThan(strategyCallsBeforeCreate);
  });

  it("runs the paper trading workflow actions for a selected strategy", async () => {
    const user = userEvent.setup();
    const session = {
      id: 9,
      strategyId: 1,
      status: "CREATED",
      initialBalance: 100000,
      cashBalance: 100000,
      createdAt: "2024-01-01T00:00:00Z",
      startedAt: null,
      stoppedAt: null,
    };
    mocks.fetchStrategies.mockResolvedValue([
      {
        id: 1,
        name: "BTC SMA",
        symbol: "BTC-USD",
        timeframe: "1h",
        strategyType: "SMA_CROSSOVER",
        status: "ACTIVE",
        rules: {
          shortWindow: 3,
          longWindow: 5,
          initialBalance: 10000,
          feePercent: 0.1,
          positionSizePercent: 50,
        },
        createdAt: "2024-01-01T00:00:00Z",
        updatedAt: "2024-01-01T00:00:00Z",
      },
    ]);
    mocks.fetchStrategyPaperSessions.mockResolvedValue([session]);

    render(<App />);

    await waitFor(() => expect(screen.getByRole("button", { name: "Start" })).toBeEnabled());
    expect(screen.getByRole("button", { name: "Stop" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "Submit order" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "Replay candles" })).toBeEnabled();

    await user.click(screen.getByRole("button", { name: "Create session" }));
    expect(await screen.findByText("Created paper session #9.")).toBeInTheDocument();
    expect(mocks.createPaperSession).toHaveBeenCalledWith(1, 100000);

    await user.click(screen.getByRole("button", { name: "Start" }));
    expect(await screen.findByText("Started session #9.")).toBeInTheDocument();
    expect(mocks.startPaperSession).toHaveBeenCalledWith(9);

    await user.click(screen.getByRole("button", { name: "Submit order" }));
    expect(await screen.findByText("Order #17 filled.")).toBeInTheDocument();
    expect(mocks.submitPaperOrder).toHaveBeenCalledWith(9, {
      side: "BUY",
      symbol: "BTC-USD",
      quantity: 0.25,
      price: 42000,
    });

    await user.click(screen.getByRole("button", { name: "Replay candles" }));
    expect(await screen.findByText("Replay filled 1 orders.")).toBeInTheDocument();
    expect(mocks.replayPaperSession).toHaveBeenCalledWith(9, expect.objectContaining({ maxCandles: 250 }));

    await user.click(screen.getByRole("button", { name: "Stop" }));
    expect(await screen.findByText("Stopped session #9.")).toBeInTheDocument();
    expect(mocks.stopPaperSession).toHaveBeenCalledWith(9);
  });
});
