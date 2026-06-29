import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { MarketRegimeDiagnostics, MarketRegimeStatus, RegimeRunResponse } from "../api/marketRegime";
import {
  AttentionEvidencePanel,
  AttentionShowcasePanel,
  ModelStatusStrip,
  RegimeWindowTable,
} from "./AttentionReview";

describe("attention review panels", () => {
  it("renders showcase loading and failure states", () => {
    const { rerender } = render(<AttentionShowcasePanel state={{ status: "loading", data: null, error: null }} />);
    expect(screen.getByText("Loading showcase summary")).toBeInTheDocument();

    rerender(<AttentionShowcasePanel state={{ status: "error", data: null, error: "backend unavailable" }} />);
    expect(screen.getByText("backend unavailable")).toBeInTheDocument();
  });

  it("shows promoted model identity without implying deployment approval", () => {
    // Promotion is research status here, so this fixture must never read like production approval.
    render(
      <ModelStatusStrip
        state={{
          status: "success",
          error: null,
          data: {
            mode: "auto",
            effectiveMode: "torch",
            classifierSource: "torch",
            ready: true,
            artifactConfigured: true,
            artifactExists: true,
            artifactIdentifier: "sha256",
            modelVersion: "attention-v2",
            featureVersion: "features-v1",
            sequenceLength: 20,
            runId: "run-42",
            artifactName: "market-regime.pt",
            artifactPath: "/models/market-regime.pt",
            architecture: "attention-transformer-v2",
            labels: [],
            modelConfig: null,
            promotionStatus: "promoted",
            promotedRunId: "run-42",
            promotionGeneratedAt: null,
            promotionArtifactMatches: true,
            promotionWarnings: [],
            warnings: [],
          } as MarketRegimeStatus,
        }}
      />,
    );
    expect(screen.getByText("attention-transformer-v2")).toBeInTheDocument();
    expect(screen.getByText("promoted")).toBeInTheDocument();
  });

  it("selects a replay window for evidence inspection", async () => {
    const onSelect = vi.fn();
    // A disagreement makes the selected row easy to recognize without relying on table position.
    const point = {
      windowEnd: "2024-01-02T00:00:00Z",
      regimeLabel: "TRENDING_UP",
      confidence: 72,
      disagreesWithBaseline: true,
      baselineRegimeLabel: "SIDEWAYS",
      anomalyLabel: null,
    } as RegimeRunResponse["points"][number];
    render(<RegimeWindowTable points={[point]} selectedWindowEnd={point.windowEnd} onSelectWindow={onSelect} />);
    await userEvent.click(screen.getByRole("button", { name: "Inspect" }));
    expect(onSelect).toHaveBeenCalledWith(point);
    expect(screen.getByText("disagrees")).toBeInTheDocument();
  });

  it("keeps empty evidence honest and renders selected diagnostics", () => {
    const { rerender } = render(<AttentionEvidencePanel diagnostics={null} snapshots={[]} />);
    expect(screen.getByText("Run replay to inspect the latest model evidence.")).toBeInTheDocument();

    rerender(
      <AttentionEvidencePanel
        snapshots={[]}
        diagnostics={{
          symbol: "BTC-USD",
          timeframe: "1h",
          windowStart: "2024-01-01T00:00:00Z",
          windowEnd: "2024-01-02T00:00:00Z",
          regimeLabel: "TRENDING_UP",
          baselineRegimeLabel: "SIDEWAYS",
          baselineConfidence: 61,
          confidence: 74,
          disagreesWithBaseline: true,
          evidenceSource: "attention",
          reasons: [],
          modelVersion: "attention-v2",
          classifierSource: "torch",
          topTimesteps: [],
          featureEvidence: [],
        } satisfies MarketRegimeDiagnostics}
      />,
    );
    expect(screen.getByText("trending up vs sideways")).toBeInTheDocument();
    expect(screen.getByText("attention-v2")).toBeInTheDocument();
  });
});
