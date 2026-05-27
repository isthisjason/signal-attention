import { postJson } from "./client";
import { MarketRegimeFeatures } from "./marketRegime";

export type AnomalyResponse = {
  anomalyScore: number;
  anomalyLabel: string;
  reasons: string[];
  features: MarketRegimeFeatures;
  classifierSource: string;
};

export function checkAnomaly(symbol = "BTC-USD", timeframe = "1h", limit = 128) {
  return postJson<AnomalyResponse>("/api/anomaly-check", { symbol, timeframe, limit });
}
