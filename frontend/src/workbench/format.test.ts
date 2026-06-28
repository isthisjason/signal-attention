import { describe, expect, it } from "vitest";
import { RegimeRunSummary } from "../api/marketRegime";
import { formatComparisonDelta, formatPercent, promotionValue, shortModelLabel } from "./format";

describe("workbench formatting", () => {
  it("keeps missing metrics explicit", () => {
    expect(formatPercent(null)).toBe("N/A");
    expect(formatPercent(12.345)).toBe("12.35%");
  });

  it("summarizes regime comparison changes", () => {
    expect(
      formatComparisonDelta({
        averageConfidenceDelta: 2.5,
        baselineDisagreementRateDelta: -1.25,
        pointCountDelta: 4,
        modeChanged: true,
        modelChanged: false,
        artifactChanged: false,
      }),
    ).toBe("conf +2.50% | gap -1.25% | windows +4 | mode changed");
  });

  it("reads nested promotion values without unsafe casts at call sites", () => {
    expect(promotionValue({ selectedRun: { runId: "run-42" } }, "selectedRun.runId")).toBe("run-42");
    expect(promotionValue({}, "selectedRun.runId")).toBeNull();
  });

  it("prefers the artifact identifier in compact model labels", () => {
    expect(shortModelLabel({ artifactIdentifier: "abcdef123456" } as RegimeRunSummary)).toBe("abcdef12");
  });
});
