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
  mode?: string | null;
  modelVersion?: string | null;
  featureVersion?: string | null;
  sequenceLength?: number | null;
  artifactIdentifier?: string | null;
};

export type RegimeRunRequest = {
  symbol: string;
  timeframe: string;
  startDate: string;
  endDate: string;
  windowSize?: number | null;
  stride?: number | null;
  includeAnomalies?: boolean;
  backtestId?: number | null;
};

export type RegimeRunResponse = {
  symbol: string;
  timeframe: string;
  windowSize: number;
  stride: number;
  includeAnomalies: boolean;
  pointCount: number;
  candles: Array<{
    openTime: string;
    openPrice: number;
    high: number;
    low: number;
    close: number;
    volume: number;
  }>;
  points: Array<{
    windowStart: string;
    windowEnd: string;
    regimeLabel: string;
    confidence: number;
    reasons: string[];
    anomalyScore?: number | null;
    anomalyLabel?: string | null;
    anomalyReasons?: string[] | null;
  }>;
  tradeMarkers: Array<{
    tradeId: number;
    side: "BUY" | "SELL";
    entryTime: string;
    entryPrice: number;
    netPnl?: number | null;
  }>;
};

export function fetchMarketRegime(symbol = "BTC-USD", timeframe = "1h", limit = 128) {
  const params = new URLSearchParams({
    symbol,
    timeframe,
    limit: String(limit),
  });
  return getJson<MarketRegimeResponse>(`/api/market-regime?${params.toString()}`);
}

export function runRegimeReplay(request: RegimeRunRequest) {
  return getJson<RegimeRunResponse>("/api/regime-runs", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });
}
