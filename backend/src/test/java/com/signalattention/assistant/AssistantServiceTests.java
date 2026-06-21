package com.signalattention.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.signalattention.backtesting.BacktestRunRepository;
import com.signalattention.marketregime.RegimePrediction;
import com.signalattention.marketregime.RegimePredictionRepository;
import com.signalattention.marketregime.RegimeRun;
import com.signalattention.marketregime.RegimeRunEvidenceSummarizer;
import com.signalattention.marketregime.RegimeRunRepository;
import com.signalattention.ml.MlMarketRegimeExperimentDiagnosticsResponse;
import com.signalattention.ml.MlRiskClient;
import com.signalattention.papertrading.PaperSessionRepository;
import com.signalattention.papertrading.PaperSessionStatus;
import com.signalattention.strategies.StrategyRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AssistantServiceTests {

    @Mock
    private AssistantSessionRepository sessionRepository;
    @Mock
    private AssistantMessageRepository messageRepository;
    @Mock
    private AssistantActionRepository actionRepository;
    @Mock
    private AssistantProvider assistantProvider;
    @Mock
    private AssistantActionExecutor actionExecutor;
    @Mock
    private StrategyRepository strategyRepository;
    @Mock
    private BacktestRunRepository backtestRunRepository;
    @Mock
    private PaperSessionRepository paperSessionRepository;
    @Mock
    private RegimeRunRepository regimeRunRepository;
    @Mock
    private RegimePredictionRepository regimePredictionRepository;
    @Mock
    private MlRiskClient mlRiskClient;

    private AssistantService service;

    @BeforeEach
    void setUp() {
        service = new AssistantService(
                sessionRepository,
                messageRepository,
                actionRepository,
                assistantProvider,
                actionExecutor,
                new ObjectMapper(),
                strategyRepository,
                backtestRunRepository,
                paperSessionRepository,
                regimeRunRepository,
                regimePredictionRepository,
                new RegimeRunEvidenceSummarizer(),
                mlRiskClient
        );
    }

    @Test
    void buildContextSnapshotIncludesLatestRegimeComparisonEvidence() {
        RegimeRun previous = run(1L, "BTC-USD", "1h", "rules", "artifact-a", "2024-01-01T00:00:00Z", 1);
        RegimeRun latest = run(2L, "BTC-USD", "1h", "torch", "artifact-b", "2024-01-02T00:00:00Z", 2);
        when(strategyRepository.count()).thenReturn(1L);
        when(backtestRunRepository.count()).thenReturn(1L);
        when(paperSessionRepository.countByStatus(PaperSessionStatus.RUNNING)).thenReturn(0L);
        when(regimeRunRepository.findAll()).thenReturn(List.of(previous, latest));
        when(regimePredictionRepository.findByRegimeRunIdOrderByWindowStartAsc(2L)).thenReturn(List.of(
                prediction(latest, "SIDEWAYS", "80.00", false),
                prediction(latest, "TRENDING_UP", "60.00", true)
        ));
        when(mlRiskClient.getMarketRegimeExperiments()).thenReturn(new MlMarketRegimeExperimentDiagnosticsResponse(
                Map.of(
                        "totalRuns", 2,
                        "promotionEligibleRuns", 1,
                        "bestRun", Map.of("runId", "run-123")
                ),
                List.of(),
                List.of(),
                Map.of("status", "promoted"),
                List.of("local registry only")
        ));

        AssistantContext context = service.buildContextSnapshot(null);

        assertThat(context.latestRegimeRunId()).isEqualTo(2L);
        assertThat(context.latestRegimeLabel()).isEqualTo("TRENDING_UP");
        assertThat(context.latestRegimeAverageConfidence()).isEqualByComparingTo("70.000000");
        assertThat(context.latestRegimeBaselineDisagreementRate()).isEqualByComparingTo("50.000000");
        assertThat(context.latestRegimeModeChanged()).isTrue();
        assertThat(context.latestRegimeArtifactChanged()).isTrue();
        assertThat(context.latestRegimeRobustnessLabel()).isEqualTo("needs_review");
        assertThat(context.modelLabTotalRuns()).isEqualTo(2);
        assertThat(context.modelLabEligibleRuns()).isEqualTo(1);
        assertThat(context.modelLabBestRunId()).isEqualTo("run-123");
        assertThat(context.modelLabWarningCount()).isEqualTo(1);
    }

    private RegimeRun run(Long id, String symbol, String timeframe, String mode, String artifact, String createdAt, int pointCount) {
        RegimeRun run = new RegimeRun(
                symbol,
                timeframe,
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-02T00:00:00Z"),
                20,
                5,
                true
        );
        ReflectionTestUtils.setField(run, "id", id);
        ReflectionTestUtils.setField(run, "effectiveMode", mode);
        ReflectionTestUtils.setField(run, "artifactIdentifier", artifact);
        ReflectionTestUtils.setField(run, "createdAt", Instant.parse(createdAt));
        ReflectionTestUtils.setField(run, "pointCount", pointCount);
        return run;
    }

    private RegimePrediction prediction(RegimeRun run, String label, String confidence, boolean disagreesWithBaseline) {
        return new RegimePrediction(
                run,
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-01T20:00:00Z"),
                label,
                new BigDecimal(confidence),
                "[]",
                "{}",
                null,
                null,
                null,
                "SIDEWAYS",
                new BigDecimal("50.00"),
                disagreesWithBaseline
        );
    }
}
