package com.signalattention.marketregime;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegimeRunEvidenceSummarizerTests {

    private final RegimeRunEvidenceSummarizer summarizer = new RegimeRunEvidenceSummarizer();

    @Test
    void summarizeBuildsQualityEvidence() {
        RegimeRun run = run();

        RegimeRunQualitySummary summary = summarizer.summarize(List.of(
                prediction(run, "TRENDING_UP", "80.00", false, "NORMAL"),
                prediction(run, "TRENDING_UP", "50.00", true, "ANOMALY")
        ));

        assertThat(summary.averageConfidence()).isEqualByComparingTo("65.000000");
        assertThat(summary.lowConfidenceWindowCount()).isEqualTo(1);
        assertThat(summary.baselineDisagreementRate()).isEqualByComparingTo("50.000000");
        assertThat(summary.anomalyCount()).isEqualTo(1);
        assertThat(summary.dominantRegimeLabel()).isEqualTo("TRENDING_UP");
    }

    @Test
    void robustnessLabelUsesSharedThresholds() {
        assertThat(summarizer.robustnessLabel(summary("0.000000", 0, 0), false)).isEqualTo("stable");
        assertThat(summarizer.robustnessLabel(summary("20.000000", 0, 0), false)).isEqualTo("mixed");
        assertThat(summarizer.robustnessLabel(summary("40.000000", 0, 0), false)).isEqualTo("needs_review");
        assertThat(summarizer.robustnessLabel(summary("0.000000", 1, 0), false)).isEqualTo("needs_review");
        assertThat(summarizer.robustnessLabel(summary("0.000000", 0, 1), false)).isEqualTo("needs_review");
        assertThat(summarizer.robustnessLabel(summary("0.000000", 0, 0), true)).isEqualTo("needs_review");
    }

    private RegimeRunQualitySummary summary(String disagreementRate, int lowConfidenceCount, int anomalyCount) {
        return new RegimeRunQualitySummary(
                new BigDecimal("80.000000"),
                lowConfidenceCount,
                disagreementRate.equals("0.000000") ? 0 : 1,
                new BigDecimal(disagreementRate),
                anomalyCount,
                "TRENDING_UP",
                java.util.Map.of("TRENDING_UP", 1)
        );
    }

    private RegimeRun run() {
        return new RegimeRun(
                "BTC-USD",
                "1h",
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-02T00:00:00Z"),
                20,
                5,
                true
        );
    }

    private RegimePrediction prediction(RegimeRun run, String label, String confidence, boolean disagreement, String anomaly) {
        return new RegimePrediction(
                run,
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-01T20:00:00Z"),
                label,
                new BigDecimal(confidence),
                "[]",
                "{}",
                anomaly == null ? null : BigDecimal.ONE,
                anomaly,
                null,
                "SIDEWAYS",
                new BigDecimal("50.00"),
                disagreement
        );
    }
}
