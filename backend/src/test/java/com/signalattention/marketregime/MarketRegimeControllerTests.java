package com.signalattention.marketregime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signalattention.ml.MlMarketRegimeFeatures;
import com.signalattention.ml.MlMarketRegimeDiagnosticsResponse;
import com.signalattention.ml.MlMarketRegimeExperimentDiagnosticsResponse;
import com.signalattention.ml.MlMarketRegimeResponse;
import com.signalattention.ml.MlMarketRegimeStatusResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MarketRegimeControllerTests {

    @Test
    void getMarketRegimeDelegatesToService() {
        MarketRegimeService service = org.mockito.Mockito.mock(MarketRegimeService.class);
        MarketRegimeController controller = new MarketRegimeController(service);
        MlMarketRegimeResponse expected = response();
        when(service.predictMarketRegime("BTC-USD", "1h", 128)).thenReturn(expected);

        MlMarketRegimeResponse actual = controller.getMarketRegime("BTC-USD", "1h", 128);

        assertThat(actual).isSameAs(expected);
        verify(service).predictMarketRegime("BTC-USD", "1h", 128);
    }

    @Test
    void getMarketRegimeStatusDelegatesToService() {
        MarketRegimeService service = org.mockito.Mockito.mock(MarketRegimeService.class);
        MarketRegimeController controller = new MarketRegimeController(service);
        MlMarketRegimeStatusResponse expected = new MlMarketRegimeStatusResponse(
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
                "promoted",
                "run-123",
                "2026-06-18T00:00:00+00:00",
                true,
                List.of(),
                List.of()
        );
        when(service.getModelStatus()).thenReturn(expected);

        MlMarketRegimeStatusResponse actual = controller.getMarketRegimeStatus();

        assertThat(actual).isSameAs(expected);
        verify(service).getModelStatus();
    }

    @Test
    void getMarketRegimeExperimentsDelegatesToService() {
        MarketRegimeService service = org.mockito.Mockito.mock(MarketRegimeService.class);
        MarketRegimeController controller = new MarketRegimeController(service);
        MlMarketRegimeExperimentDiagnosticsResponse expected = new MlMarketRegimeExperimentDiagnosticsResponse(
                Map.of("totalRuns", 1),
                List.of(Map.of("runId", "run-1")),
                List.of(),
                Map.of("status", "promoted"),
                List.of()
        );
        when(service.getExperimentDiagnostics()).thenReturn(expected);

        MlMarketRegimeExperimentDiagnosticsResponse actual = controller.getMarketRegimeExperiments();

        assertThat(actual).isSameAs(expected);
        verify(service).getExperimentDiagnostics();
    }

    @Test
    void diagnoseMarketRegimeDelegatesToService() {
        MarketRegimeService service = org.mockito.Mockito.mock(MarketRegimeService.class);
        MarketRegimeController controller = new MarketRegimeController(service);
        MlMarketRegimeDiagnosticsResponse expected = diagnosticsResponse();
        Instant windowEnd = Instant.parse("2024-01-01T19:00:00Z");
        when(service.diagnoseMarketRegime("BTC-USD", "1h", 20, windowEnd)).thenReturn(expected);

        MlMarketRegimeDiagnosticsResponse actual = controller.diagnoseMarketRegime("BTC-USD", "1h", 20, windowEnd);

        assertThat(actual).isSameAs(expected);
        verify(service).diagnoseMarketRegime("BTC-USD", "1h", 20, windowEnd);
    }

    @Test
    void listEvidenceSnapshotsDelegatesToService() {
        MarketRegimeService service = org.mockito.Mockito.mock(MarketRegimeService.class);
        MarketRegimeController controller = new MarketRegimeController(service);
        when(service.listEvidenceSnapshots("BTC-USD", "1h", 5)).thenReturn(List.of());

        List<RegimeEvidenceSnapshotResponse> actual = controller.listEvidenceSnapshots("BTC-USD", "1h", 5);

        assertThat(actual).isEmpty();
        verify(service).listEvidenceSnapshots("BTC-USD", "1h", 5);
    }

    @Test
    void runRegimeReplayDelegatesToService() {
        MarketRegimeService service = org.mockito.Mockito.mock(MarketRegimeService.class);
        MarketRegimeController controller = new MarketRegimeController(service);
        RegimeRunRequest request = new RegimeRunRequest(
                "BTC-USD",
                "1h",
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-10T00:00:00Z"),
                128,
                8,
                true,
                null
        );
        RegimeRunResponse expected = new RegimeRunResponse(
                1L,
                "BTC-USD",
                "1h",
                request.startDate(),
                request.endDate(),
                128,
                8,
                true,
                "auto",
                "rules",
                "rules",
                null,
                "torch-market-regime-features/v1",
                null,
                RegimeRunStatus.COMPLETED,
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-01T00:00:01Z"),
                0,
                null,
                List.of(),
                List.of(),
                List.of()
        );
        when(service.runRegimeReplay(request)).thenReturn(expected);

        RegimeRunResponse actual = controller.runRegimeReplay(request);

        assertThat(actual).isSameAs(expected);
        verify(service).runRegimeReplay(request);
    }

    @Test
    void compareRegimeRunsDelegatesToService() {
        MarketRegimeService service = org.mockito.Mockito.mock(MarketRegimeService.class);
        MarketRegimeController controller = new MarketRegimeController(service);
        RegimeRunComparisonResponse expected = new RegimeRunComparisonResponse("BTC-USD", "1h", List.of());
        when(service.compareRegimeRuns("BTC-USD", "1h", 10)).thenReturn(expected);

        RegimeRunComparisonResponse actual = controller.compareRegimeRuns("BTC-USD", "1h", 10);

        assertThat(actual).isSameAs(expected);
        verify(service).compareRegimeRuns("BTC-USD", "1h", 10);
    }

    @Test
    void summarizeRobustnessDelegatesToService() {
        MarketRegimeService service = org.mockito.Mockito.mock(MarketRegimeService.class);
        MarketRegimeController controller = new MarketRegimeController(service);
        RegimeRobustnessSummaryResponse expected = new RegimeRobustnessSummaryResponse(
                20L,
                10L,
                "BTC-USD",
                "1h",
                "stable",
                new RegimeRunQualitySummary(BigDecimal.TEN, 0, 0, BigDecimal.ZERO, 0, "TRENDING_UP", Map.of()),
                List.of("Regime windows were high confidence, baseline aligned, and anomaly free."),
                List.of()
        );
        when(service.summarizeRobustness(20L, 10L)).thenReturn(expected);

        RegimeRobustnessSummaryResponse actual = controller.summarizeRobustness(20L, 10L);

        assertThat(actual).isSameAs(expected);
        verify(service).summarizeRobustness(20L, 10L);
    }

    private MlMarketRegimeResponse response() {
        return new MlMarketRegimeResponse(
                "TRENDING_UP",
                new BigDecimal("72.50"),
                List.of("Price is rising and remains above its sequence average."),
                new MlMarketRegimeFeatures(
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ZERO,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ZERO
                ),
                "rules",
                "rules",
                null,
                "torch-market-regime-features/v1",
                128,
                null
        );
    }

    private MlMarketRegimeDiagnosticsResponse diagnosticsResponse() {
        return new MlMarketRegimeDiagnosticsResponse(
                "BTC-USD",
                "1h",
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-01T19:00:00Z"),
                "TRENDING_UP",
                new BigDecimal("80.00"),
                "TRENDING_UP",
                new BigDecimal("75.00"),
                false,
                "attribution",
                List.of("Price is rising."),
                List.of(),
                List.of(),
                "rules",
                "rules",
                null,
                "torch-market-regime-features/v1",
                20,
                null
        );
    }
}
