import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";

const mocks = vi.hoisted(() => ({
  fetchAuditEvents: vi.fn(),
  fetchDashboardRiskAlerts: vi.fn(),
  fetchDashboardSummary: vi.fn(),
  fetchMarketRegime: vi.fn(),
  fetchStrategies: vi.fn(),
  fetchStrategyPaperSessions: vi.fn(),
  fetchStrategyPerformance: vi.fn(),
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
}));

vi.mock("./api/paperTrading", () => ({
  fetchStrategyPaperSessions: mocks.fetchStrategyPaperSessions,
}));

vi.mock("./api/strategies", () => ({
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
  mocks.fetchMarketRegime.mockRejectedValue(new Error("Not enough candles"));
  mocks.fetchStrategies.mockResolvedValue([]);
  mocks.fetchStrategyPaperSessions.mockResolvedValue([]);
});

describe("dashboard render states", () => {
  it("renders empty workflow state without enabling dependent actions", async () => {
    render(<App />);

    expect(await screen.findByText("No strategies have been created yet.")).toBeInTheDocument();
    expect(screen.getByText("No saved strategies yet.")).toBeInTheDocument();
    expect(screen.getByText("No import has run in this browser session.")).toBeInTheDocument();
    expect(screen.getByText("No backtest has run in this browser session.")).toBeInTheDocument();
    expect(screen.getByText("No paper sessions for the selected strategy.")).toBeInTheDocument();
    expect(screen.getByText("No paper orders for the selected session.")).toBeInTheDocument();
    expect(screen.getByText("No paper positions for the selected session.")).toBeInTheDocument();
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
});
