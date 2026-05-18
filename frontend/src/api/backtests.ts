import { getJson, postJson } from "./client";

export type BacktestRun = {
  id: number;
  strategyId: number;
  startDate: string;
  endDate: string;
  initialBalance: number;
  finalBalance: number;
  totalReturn: number;
  maxDrawdown: number;
  winRate: number;
  profitFactor: number;
  tradeCount: number;
  averageTradeReturn: number;
  feeDrag: number;
  volatility: number;
  mlRiskScore: number | null;
  mlRiskLabel: string | null;
  status: string;
  createdAt: string;
  completedAt: string | null;
};

export type BacktestTrade = {
  id: number;
  backtestRunId: number;
  side: string;
  entryTime: string;
  entryPrice: number;
  exitTime: string | null;
  exitPrice: number | null;
  quantity: number;
  grossPnl: number;
  fees: number;
  netPnl: number;
  returnPercent: number;
};

export type MlRiskScore = {
  riskScore: number;
  riskLabel: string;
  reasons: string[];
};

export type RunBacktestPayload = {
  startDate: string;
  endDate: string;
};

export function runBacktest(strategyId: number, payload: RunBacktestPayload) {
  return postJson<BacktestRun>(`/api/strategies/${strategyId}/backtests`, payload);
}

export function fetchBacktest(backtestId: number) {
  return getJson<BacktestRun>(`/api/backtests/${backtestId}`);
}

export function fetchBacktestTrades(backtestId: number) {
  return getJson<BacktestTrade[]>(`/api/backtests/${backtestId}/trades`);
}

export function scoreBacktestRisk(backtestId: number) {
  return postJson<MlRiskScore>(`/api/backtests/${backtestId}/ml-risk-score`);
}
