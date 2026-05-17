import { getJson } from "./client";

export type DashboardBacktestSummary = {
  id: number;
  strategyId: number;
  status: string;
  totalReturn: number | null;
  maxDrawdown: number | null;
  tradeCount: number | null;
  mlRiskScore: number | null;
  mlRiskLabel: string | null;
  createdAt: string;
};

export type DashboardAuditPreview = {
  id: number;
  entityType: string;
  entityId: string;
  action: string;
  message: string;
  createdAt: string;
};

export type DashboardSummary = {
  strategyCount: number;
  backtestCount: number;
  activePaperSessionCount: number;
  latestBacktest: DashboardBacktestSummary | null;
  recentAuditEvents: DashboardAuditPreview[];
};

export type StrategyPerformance = {
  strategyId: number;
  name: string;
  symbol: string;
  timeframe: string;
  status: string;
  latestBacktestId: number | null;
  latestTotalReturn: number | null;
  latestMaxDrawdown: number | null;
  latestTradeCount: number | null;
  latestMlRiskScore: number | null;
  latestMlRiskLabel: string | null;
  latestBacktestCreatedAt: string | null;
  paperSessionCount: number;
};

export function fetchDashboardSummary() {
  return getJson<DashboardSummary>("/api/dashboard/summary");
}

export function fetchStrategyPerformance() {
  return getJson<StrategyPerformance[]>("/api/dashboard/strategy-performance");
}
