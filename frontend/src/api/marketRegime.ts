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
  promotionStatus?: string | null;
  promotedRunId?: string | null;
  promotionGeneratedAt?: string | null;
  promotionArtifactMatches?: boolean | null;
  promotionWarnings: string[];
  warnings: string[];
};

export type MarketRegimeExperimentRun = {
  name?: string | null;
  runId?: string | null;
  hasTraining?: boolean;
  hasEvaluation?: boolean;
  validationAccuracy?: number | null;
  accuracy?: number | null;
  baselineAccuracy?: number | null;
  liftOverBaseline?: number | null;
  confidence?: Record<string, unknown> | null;
  labelDistribution?: Record<string, number> | null;
  windowRanges?: Record<string, unknown> | null;
  promotionGate?: {
    eligible?: boolean;
    failures?: string[];
    gates?: Record<string, unknown>;
  } | null;
  weakestLabels?: Array<{
    label?: string | null;
    f1?: number | null;
    recall?: number | null;
    precision?: number | null;
    support?: number | null;
  }>;
  confusionPairs?: Array<{
    expected?: string | null;
    predicted?: string | null;
    count?: number | null;
  }>;
  reportPath?: string | null;
};

export type MarketRegimeExperimentDiagnostics = {
  summary: {
    totalRuns: number;
    trainedRuns: number;
    evaluatedRuns: number;
    promotionEligibleRuns: number;
    bestRun?: MarketRegimeExperimentRun | null;
  };
  runs: MarketRegimeExperimentRun[];
  incompleteRuns: MarketRegimeExperimentRun[];
  promotion?: Record<string, unknown> | null;
  warnings: string[];
};

export type RegimeRunQualitySummary = {
  averageConfidence?: number | null;
  lowConfidenceWindowCount: number;
  baselineDisagreementCount: number;
  baselineDisagreementRate: number;
  anomalyCount: number;
  dominantRegimeLabel?: string | null;
  regimeCounts: Record<string, number>;
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
  qualitySummary?: RegimeRunQualitySummary | null;
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

export type RegimeRunComparisonDelta = {
  averageConfidenceDelta?: number | null;
  baselineDisagreementRateDelta?: number | null;
  pointCountDelta?: number | null;
  modeChanged: boolean;
  modelChanged: boolean;
  artifactChanged: boolean;
};

export type RegimeRunComparison = {
  symbol: string;
  timeframe: string;
  runs: Array<{
    run: RegimeRunSummary;
    deltaFromPrevious?: RegimeRunComparisonDelta | null;
  }>;
};

export type AttentionTimestepEvidence = {
  openTime: string;
  attentionScore: number;
  close: number;
  returnPercent: number;
};

export type FeatureEvidence = {
  name: string;
  value: number;
  importance: number;
};

export type MarketRegimeDiagnostics = {
  symbol: string;
  timeframe: string;
  windowStart: string;
  windowEnd: string;
  regimeLabel: string;
  confidence: number;
  baselineRegimeLabel: string;
  baselineConfidence: number;
  disagreesWithBaseline: boolean;
  evidenceSource: string;
  reasons: string[];
  topTimesteps: AttentionTimestepEvidence[];
  featureEvidence: FeatureEvidence[];
  classifierSource?: string | null;
  mode?: string | null;
  modelVersion?: string | null;
  featureVersion?: string | null;
  sequenceLength?: number | null;
  artifactIdentifier?: string | null;
};

export type RegimeEvidenceSnapshot = Omit<MarketRegimeDiagnostics, "topTimesteps" | "featureEvidence" | "reasons"> & {
  id: number;
  reasonsJson?: string | null;
  topTimestepsJson?: string | null;
  featureEvidenceJson?: string | null;
  createdAt: string;
};

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

export function fetchMarketRegimeExperiments() {
  return getJson<MarketRegimeExperimentDiagnostics>("/api/market-regime/experiments");
}

export function fetchRegimeRuns(symbol = "BTC-USD", timeframe = "1h", limit = 10) {
  const params = new URLSearchParams({ symbol, timeframe, limit: String(limit) });
  return getJson<RegimeRunSummary[]>(`/api/regime-runs?${params.toString()}`);
}

export function fetchRegimeRunComparison(symbol = "BTC-USD", timeframe = "1h", limit = 10) {
  const params = new URLSearchParams({ symbol, timeframe, limit: String(limit) });
  return getJson<RegimeRunComparison>(`/api/regime-runs/comparison?${params.toString()}`);
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

export function fetchMarketRegimeDiagnostics(symbol = "BTC-USD", timeframe = "1h", limit = 128, windowEnd?: string | null) {
  const params = new URLSearchParams({ symbol, timeframe, limit: String(limit) });
  if (windowEnd) {
    params.set("windowEnd", windowEnd);
  }
  return getJson<MarketRegimeDiagnostics>(`/api/market-regime/diagnostics?${params.toString()}`);
}

export function fetchRegimeEvidenceSnapshots(symbol = "BTC-USD", timeframe = "1h", limit = 5) {
  const params = new URLSearchParams({ symbol, timeframe, limit: String(limit) });
  return getJson<RegimeEvidenceSnapshot[]>(`/api/market-regime/evidence-snapshots?${params.toString()}`);
}
