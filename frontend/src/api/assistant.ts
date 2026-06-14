import { postJson, getJson } from "./client";

export type AssistantActionStatus = "PROPOSED" | "CONFIRMED" | "REJECTED" | "EXECUTED" | "FAILED";

export type AssistantActionType =
  | "RUN_BACKTEST"
  | "RUN_REGIME_REPLAY"
  | "START_PAPER_SESSION"
  | "REPLAY_PAPER_SESSION";

export type AssistantMessage = {
  id: number;
  role: "USER" | "ASSISTANT";
  content: string;
  createdAt: string;
};

export type AssistantAction = {
  id: number;
  messageId: number | null;
  actionType: AssistantActionType;
  status: AssistantActionStatus;
  summary: string;
  payloadJson: string;
  executionResultJson: string | null;
  failureMessage: string | null;
  createdAt: string;
  confirmedAt: string | null;
  rejectedAt: string | null;
  executedAt: string | null;
};

export type AssistantSession = {
  id: number;
  title: string;
  createdAt: string;
  updatedAt: string;
  messages: AssistantMessage[];
  actions: AssistantAction[];
};

export type AssistantMessagePayload = {
  prompt: string;
  strategyId: number | null;
  backtestId: number | null;
  paperSessionId: number | null;
  startDate: string;
  endDate: string;
};

export function createAssistantSession() {
  return postJson<AssistantSession>("/api/assistant/sessions", { title: "Research assistant" });
}

export function fetchAssistantSession(sessionId: number) {
  return getJson<AssistantSession>(`/api/assistant/sessions/${sessionId}`);
}

export function sendAssistantMessage(sessionId: number, payload: AssistantMessagePayload) {
  return postJson<AssistantSession>(`/api/assistant/sessions/${sessionId}/messages`, payload);
}

export function confirmAssistantAction(actionId: number) {
  return postJson<AssistantAction>(`/api/assistant/actions/${actionId}/confirm`);
}

export function rejectAssistantAction(actionId: number) {
  return postJson<AssistantAction>(`/api/assistant/actions/${actionId}/reject`);
}
