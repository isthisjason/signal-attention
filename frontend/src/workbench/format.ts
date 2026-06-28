import { RegimeRunComparisonDelta, RegimeRunSummary } from "../api/marketRegime";

export function formatAction(value: string) {
  return value.replaceAll("_", " ").toLowerCase();
}

export function formatDateTime(value: string | null) {
  if (!value) return "N/A";
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

export function formatPercent(value: number | null | undefined) {
  return value === null || value === undefined ? "N/A" : `${Number(value).toFixed(2)}%`;
}

export function formatRatioPercent(value: number | null | undefined) {
  return value === null || value === undefined ? "N/A" : `${(Number(value) * 100).toFixed(2)}%`;
}

export function promotionValue(source: Record<string, unknown> | null | undefined, path: string) {
  if (!source) return null;
  const value = path.split(".").reduce<unknown>((current, key) => {
    if (current && typeof current === "object" && key in current) {
      return (current as Record<string, unknown>)[key];
    }
    return null;
  }, source);
  return typeof value === "string" ? value : null;
}

export function shortModelLabel(run: RegimeRunSummary) {
  if (run.artifactIdentifier) return run.artifactIdentifier.slice(0, 8);
  return run.modelVersion || run.classifierSource || "rules";
}

export function formatComparisonDelta(delta: RegimeRunComparisonDelta | null | undefined) {
  if (!delta) return "baseline";
  const markers = [
    delta.averageConfidenceDelta == null ? null : `conf ${formatSignedPercent(delta.averageConfidenceDelta)}`,
    delta.baselineDisagreementRateDelta == null
      ? null
      : `gap ${formatSignedPercent(delta.baselineDisagreementRateDelta)}`,
    delta.pointCountDelta == null ? null : `windows ${formatSignedNumber(delta.pointCountDelta)}`,
    delta.modeChanged ? "mode changed" : null,
    delta.artifactChanged ? "artifact changed" : null,
  ].filter(Boolean);
  return markers.length ? markers.join(" | ") : "no material change";
}

export function formatNumber(value: number | null | undefined) {
  return value === null || value === undefined ? "N/A" : Number(value).toFixed(2);
}

export function formatCurrency(value: number | null | undefined) {
  if (value === null || value === undefined) return "N/A";
  return new Intl.NumberFormat(undefined, {
    currency: "USD",
    style: "currency",
    maximumFractionDigits: 2,
  }).format(Number(value));
}

function formatSignedPercent(value: number) {
  return `${value >= 0 ? "+" : ""}${value.toFixed(2)}%`;
}

function formatSignedNumber(value: number) {
  return `${value >= 0 ? "+" : ""}${value}`;
}
