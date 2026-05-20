import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";

const mocks = vi.hoisted(() => ({
  fetchAuditEvents: vi.fn(),
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
  mocks.fetchAuditEvents.mockResolvedValue([]);
  mocks.fetchMarketRegime.mockRejectedValue(new Error("Not enough candles"));
  mocks.fetchStrategies.mockResolvedValue([]);
  mocks.fetchStrategyPaperSessions.mockResolvedValue([]);
});

describe("dashboard render states", () => {
  it("renders empty workflow state without enabling dependent actions", async () => {
    render(<App />);

    expect(await screen.findByText("No strategies have been created yet.")).toBeInTheDocument();
    expect(screen.getByText("No audit events have been recorded yet.")).toBeInTheDocument();
    expect(screen.getByText(/Import at least 20 BTC-USD 1h candles/)).toBeInTheDocument();

    expect(screen.getByRole("button", { name: "Run backtest" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Score ML risk" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Start" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Stop" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Submit order" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Replay candles" })).toBeDisabled();
  });
});
