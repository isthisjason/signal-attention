package com.signalattention.marketregime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signalattention.ml.MlMarketRegimeFeatures;
import com.signalattention.ml.MlMarketRegimeDiagnosticsResponse;
import com.signalattention.ml.MlMarketRegimeResponse;
import com.signalattention.ml.MlMarketRegimeStatusResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
                List.of(),
                List.of(),
                List.of()
        );
        when(service.runRegimeReplay(request)).thenReturn(expected);

        RegimeRunResponse actual = controller.runRegimeReplay(request);

        assertThat(actual).isSameAs(expected);
        verify(service).runRegimeReplay(request);
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
