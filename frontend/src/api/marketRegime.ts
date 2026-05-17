import { getJson } from "./client";

export type MarketRegimeFeatures = {
  latestReturnPercent: number;
  averageReturnPercent: number;
  volatilityPercent: number;
  trendSlopePercent: number;
  smaDistancePercent: number;
  volumeZScore: number;
};

export type MarketRegimeResponse = {
  regimeLabel: string;
  confidence: number;
  reasons: string[];
  features: MarketRegimeFeatures;
  classifierSource: string;
};

export function fetchMarketRegime(symbol = "BTC-USD", timeframe = "1h", limit = 128) {
  const params = new URLSearchParams({
    symbol,
    timeframe,
    limit: String(limit),
  });
  return getJson<MarketRegimeResponse>(`/api/market-regime?${params.toString()}`);
}
