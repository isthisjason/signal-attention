import { getJson, patchJson, postJson } from "./client";

export type PaperSession = {
  id: number;
  strategyId: number;
  status: string;
  initialBalance: number;
  cashBalance: number;
  createdAt: string;
  startedAt: string | null;
  stoppedAt: string | null;
};

export type PaperSessionSummary = {
  paperSessionId: number;
  strategyId: number;
  status: string;
  initialBalance: number;
  cashBalance: number;
  openPositionValue: number;
  realizedPnl: number;
  unrealizedPnl: number;
  totalEquity: number;
  hasUnpricedPositions: boolean;
  openPositions: Array<{
    id: number;
    symbol: string;
    quantity: number;
    entryPrice: number;
    markPrice: number | null;
    unrealizedPnl: number | null;
  }>;
};

export type PaperReplayResult = {
  paperSessionId: number;
  candlesRead: number;
  signalsProcessed: number;
  filledOrders: number;
  rejectedOrders: number;
  startDate: string;
  endDate: string;
};

export function createPaperSession(strategyId: number, initialBalance: number) {
  return postJson<PaperSession>(`/api/strategies/${strategyId}/paper-sessions`, {
    initialBalance,
  });
}

export function fetchStrategyPaperSessions(strategyId: number) {
  return getJson<PaperSession[]>(`/api/strategies/${strategyId}/paper-sessions`);
}

export function startPaperSession(sessionId: number) {
  return patchJson<PaperSession>(`/api/paper-sessions/${sessionId}/start`);
}

export function stopPaperSession(sessionId: number) {
  return patchJson<PaperSession>(`/api/paper-sessions/${sessionId}/stop`);
}

export function fetchPaperSessionSummary(sessionId: number) {
  return getJson<PaperSessionSummary>(`/api/paper-sessions/${sessionId}/summary`);
}

export function replayPaperSession(
  sessionId: number,
  payload: { startDate: string; endDate: string; maxCandles: number },
) {
  return postJson<PaperReplayResult>(`/api/paper-sessions/${sessionId}/replay`, payload);
}
