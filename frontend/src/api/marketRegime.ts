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

export type MarketRegimeStatus = {
  mode: string;
  effectiveMode: string;
  classifierSource: string;
  ready: boolean;
  artifactConfigured: boolean;
  artifactExists: boolean;
  artifactIdentifier?: string | null;
  modelVersion?: string | null;
  featureVersion?: string | null;
  sequenceLength?: number | null;
  runId?: string | null;
  artifactName?: string | null;
  artifactPath?: string | null;
  architecture?: string | null;
  labels: string[];
  modelConfig?: Record<string, unknown> | null;
  warnings: string[];
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
  id: number;
  symbol: string;
  timeframe: string;
  startDate: string;
  endDate: string;
  windowSize: number;
  stride: number;
  includeAnomalies: boolean;
  requestedMode?: string | null;
  effectiveMode?: string | null;
  classifierSource?: string | null;
  modelVersion?: string | null;
  featureVersion?: string | null;
  artifactIdentifier?: string | null;
  status: string;
  createdAt: string;
  completedAt?: string | null;
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
    baselineRegimeLabel?: string | null;
    baselineConfidence?: number | null;
    disagreesWithBaseline?: boolean | null;
  }>;
  tradeMarkers: Array<{
    tradeId: number;
    side: "BUY" | "SELL";
    entryTime: string;
    entryPrice: number;
    netPnl?: number | null;
  }>;
};

export type RegimeRunSummary = Omit<RegimeRunResponse, "candles" | "points" | "tradeMarkers">;

export function fetchMarketRegime(symbol = "BTC-USD", timeframe = "1h", limit = 128) {
  const params = new URLSearchParams({
    symbol,
    timeframe,
    limit: String(limit),
  });
  return getJson<MarketRegimeResponse>(`/api/market-regime?${params.toString()}`);
}

export function fetchMarketRegimeStatus() {
  return getJson<MarketRegimeStatus>("/api/market-regime/status");
}

export function fetchRegimeRuns(symbol = "BTC-USD", timeframe = "1h", limit = 10) {
  const params = new URLSearchParams({ symbol, timeframe, limit: String(limit) });
  return getJson<RegimeRunSummary[]>(`/api/regime-runs?${params.toString()}`);
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
