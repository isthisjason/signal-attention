import { RegimeRunQualitySummary } from "./marketRegime";
import { getJson } from "./client";

export type AttentionShowcaseSummary = {
  modelReady: boolean;
  requestedMode?: string | null;
  effectiveMode?: string | null;
  classifierSource?: string | null;
  promotionStatus?: string | null;
  promotedRunId?: string | null;
  promotionArtifactMatches?: boolean | null;
  latestRun?: {
    id: number;
    symbol: string;
    timeframe: string;
    startDate: string;
    endDate: string;
    pointCount: number;
    status: string;
    createdAt: string;
    qualitySummary: RegimeRunQualitySummary;
  } | null;
  robustnessLabel: string;
  reviewReasons: string[];
  evidenceSnapshotCount: number;
  disagreementSummary: {
    totalWindows: number;
    disagreementCount: number;
    disagreementRate: number;
    dominantRegimeLabel?: string | null;
    dominantBaselineLabel?: string | null;
    anomalyOverlapCount: number;
    lowestConfidenceWindows: Array<{
      windowEnd: string;
      regimeLabel: string;
      baselineRegimeLabel?: string | null;
      confidence?: number | null;
      anomalyLabel?: string | null;
    }>;
  };
  nextAction: string;
  warnings: string[];
};

export function fetchAttentionShowcaseSummary() {
  return getJson<AttentionShowcaseSummary>("/api/attention-showcase/summary");
}
