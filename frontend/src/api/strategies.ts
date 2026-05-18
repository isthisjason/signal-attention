import { getJson, postJson } from "./client";

export type SmaCrossoverRules = {
  shortWindow: number;
  longWindow: number;
  initialBalance: number;
  feePercent: number;
  positionSizePercent: number;
};

export type Strategy = {
  id: number;
  name: string;
  symbol: string;
  timeframe: string;
  strategyType: "SMA_CROSSOVER";
  rules: SmaCrossoverRules;
  status: string;
  createdAt: string;
  updatedAt: string;
};

export type StrategyCreatePayload = {
  name: string;
  symbol: string;
  timeframe: string;
  strategyType: "SMA_CROSSOVER";
  rules: SmaCrossoverRules;
};

export function fetchStrategies() {
  return getJson<Strategy[]>("/api/strategies");
}

export function createStrategy(payload: StrategyCreatePayload) {
  return postJson<Strategy>("/api/strategies", payload);
}
