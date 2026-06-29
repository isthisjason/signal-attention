package com.signalattention.attentionshowcase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.signalattention.marketregime.RegimeEvidenceSnapshotRepository;
import com.signalattention.marketregime.RegimePrediction;
import com.signalattention.marketregime.RegimePredictionRepository;
import com.signalattention.marketregime.RegimeRun;
import com.signalattention.marketregime.RegimeRunEvidenceSummarizer;
import com.signalattention.marketregime.RegimeRunRepository;
import com.signalattention.ml.MlMarketRegimeStatusResponse;
import com.signalattention.ml.MlRiskClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AttentionShowcaseServiceTests {

    @Mock
    private MlRiskClient mlRiskClient;
    @Mock
    private RegimeRunRepository regimeRunRepository;
    @Mock
    private RegimePredictionRepository regimePredictionRepository;
    @Mock
    private RegimeEvidenceSnapshotRepository evidenceSnapshotRepository;

    private AttentionShowcaseService service;

    @BeforeEach
    void setUp() {
        service = new AttentionShowcaseService(
                mlRiskClient,
                regimeRunRepository,
                regimePredictionRepository,
                evidenceSnapshotRepository,
                new RegimeRunEvidenceSummarizer()
        );
    }

    @Test
    void getSummaryReturnsNextReplayActionWhenNoRunsExist() {
        when(mlRiskClient.getMarketRegimeStatus()).thenReturn(status());
        when(regimeRunRepository.findFirstByOrderByCreatedAtDesc()).thenReturn(Optional.empty());

        AttentionShowcaseSummaryResponse response = service.getSummary();

        assertThat(response.modelReady()).isTrue();
        assertThat(response.latestRun()).isNull();
        assertThat(response.robustnessLabel()).isEqualTo("needs_replay");
        assertThat(response.evidenceSnapshotCount()).isZero();
        assertThat(response.disagreementSummary().totalWindows()).isZero();
        assertThat(response.nextAction()).contains("Run an attention regime replay");
    }

    @Test
    void getSummaryCombinesLatestRunQualityAndDisagreementEvidence() {
        RegimeRun run = savedRun();
        when(mlRiskClient.getMarketRegimeStatus()).thenReturn(status());
        when(regimeRunRepository.findFirstByOrderByCreatedAtDesc()).thenReturn(Optional.of(run));
        // One weak disagreement and one anomaly are enough to prove the summary points at the useful review window.
        when(regimePredictionRepository.findByRegimeRunIdOrderByWindowStartAsc(42L)).thenReturn(List.of(
                prediction(run, "TRENDING_UP", "70.00", true, "NORMAL", 20),
                prediction(run, "SIDEWAYS", "55.00", true, "ANOMALY", 21),
                prediction(run, "TRENDING_UP", "80.00", false, null, 22)
        ));
        when(evidenceSnapshotRepository.countBySymbolAndTimeframe("BTC-USD", "1h")).thenReturn(2L);

        AttentionShowcaseSummaryResponse response = service.getSummary();

        assertThat(response.latestRun().id()).isEqualTo(42L);
        assertThat(response.latestRun().qualitySummary().averageConfidence()).isEqualByComparingTo("68.333333");
        assertThat(response.robustnessLabel()).isEqualTo("needs_review");
        assertThat(response.evidenceSnapshotCount()).isEqualTo(2L);
        assertThat(response.disagreementSummary().disagreementCount()).isEqualTo(2);
        assertThat(response.disagreementSummary().disagreementRate()).isEqualByComparingTo("66.666667");
        assertThat(response.disagreementSummary().dominantRegimeLabel()).isEqualTo("TRENDING_UP");
        assertThat(response.disagreementSummary().dominantBaselineLabel()).isEqualTo("SIDEWAYS");
        assertThat(response.disagreementSummary().anomalyOverlapCount()).isEqualTo(1);
        assertThat(response.disagreementSummary().lowestConfidenceWindows())
                .extracting(AttentionShowcaseSummaryResponse.DisagreementWindow::confidence)
                .containsExactly(new BigDecimal("55.00"), new BigDecimal("70.00"));
        assertThat(response.nextAction()).contains("lowest confidence disagreement");
    }

    private RegimeRun savedRun() {
        RegimeRun run = new RegimeRun(
                "BTC-USD",
                "1h",
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-02T00:00:00Z"),
                20,
                5,
                true
        );
        ReflectionTestUtils.setField(run, "id", 42L);
        ReflectionTestUtils.setField(run, "pointCount", 3);
        ReflectionTestUtils.setField(run, "createdAt", Instant.parse("2024-01-02T00:00:00Z"));
        return run;
    }

    private RegimePrediction prediction(
            RegimeRun run,
            String regimeLabel,
            String confidence,
            boolean disagreesWithBaseline,
            String anomalyLabel,
            int windowEndHour
    ) {
        return new RegimePrediction(
                run,
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-01T00:00:00Z").plusSeconds(windowEndHour * 3600L),
                regimeLabel,
                new BigDecimal(confidence),
                "[]",
                "{}",
                anomalyLabel == null ? null : BigDecimal.ONE,
                anomalyLabel,
                null,
                "SIDEWAYS",
                new BigDecimal("50.00"),
                disagreesWithBaseline
        );
    }

    private MlMarketRegimeStatusResponse status() {
        return new MlMarketRegimeStatusResponse(
                "auto",
                "rules",
                "rules",
                true,
                false,
                false,
                null,
                null,
                "torch-market-regime-features/v1",
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                "no_eligible_run",
                null,
                null,
                false,
                List.of(),
                List.of("auto mode fell back to rules")
        );
    }
}
