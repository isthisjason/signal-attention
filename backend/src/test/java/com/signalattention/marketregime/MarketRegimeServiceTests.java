package com.signalattention.marketregime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.signalattention.common.BadRequestException;
import com.signalattention.common.ExternalServiceException;
import com.signalattention.backtesting.BacktestRunRepository;
import com.signalattention.backtesting.BacktestTradeRepository;
import com.signalattention.marketdata.MarketCandle;
import com.signalattention.marketdata.MarketCandleRepository;
import com.signalattention.ml.MlMarketRegimeFeatures;
import com.signalattention.ml.MlMarketRegimeRequest;
import com.signalattention.ml.MlMarketRegimeResponse;
import com.signalattention.ml.MlMarketRegimeStatusResponse;
import com.signalattention.ml.MlRiskClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class MarketRegimeServiceTests {

    @Mock
    private MarketCandleRepository marketCandleRepository;

    @Mock
    private MlRiskClient mlRiskClient;
    @Mock
    private BacktestRunRepository backtestRunRepository;
    @Mock
    private BacktestTradeRepository backtestTradeRepository;
    @Mock
    private RegimeRunRepository regimeRunRepository;
    @Mock
    private RegimePredictionRepository regimePredictionRepository;

    private MarketRegimeService service;

    @BeforeEach
    void setUp() {
        service = new MarketRegimeService(
                marketCandleRepository,
                mlRiskClient,
                backtestRunRepository,
                backtestTradeRepository,
                regimeRunRepository,
                regimePredictionRepository,
                new ObjectMapper()
        );
    }

    @Test
    void predictMarketRegimeSendsLatestCandlesAscendingToMlService() {
        List<MarketCandle> descendingCandles = candlesDescending(20);
        MlMarketRegimeResponse mlResponse = response();
        when(marketCandleRepository.findBySymbolAndTimeframeOrderByOpenTimeDesc("BTC-USD", "1h", PageRequest.of(0, 20)))
                .thenReturn(descendingCandles);
        when(mlRiskClient.predictMarketRegime(any(MlMarketRegimeRequest.class))).thenReturn(mlResponse);

        MlMarketRegimeResponse response = service.predictMarketRegime(" BTC-USD ", " 1h ", 20);

        assertThat(response.regimeLabel()).isEqualTo("TRENDING_UP");
        assertThat(response.mode()).isEqualTo("rules");
        assertThat(response.featureVersion()).isEqualTo("torch-market-regime-features/v1");
        assertThat(response.sequenceLength()).isEqualTo(20);
        assertThat(response.artifactIdentifier()).isNull();
        ArgumentCaptor<MlMarketRegimeRequest> requestCaptor = ArgumentCaptor.forClass(MlMarketRegimeRequest.class);
        org.mockito.Mockito.verify(mlRiskClient).predictMarketRegime(requestCaptor.capture());
        MlMarketRegimeRequest request = requestCaptor.getValue();
        assertThat(request.symbol()).isEqualTo("BTC-USD");
        assertThat(request.timeframe()).isEqualTo("1h");
        assertThat(request.candles()).hasSize(20);
        assertThat(request.candles().get(0).openTime()).isBefore(request.candles().get(19).openTime());
    }

    @Test
    void predictMarketRegimeRejectsMissingCandles() {
        when(marketCandleRepository.findBySymbolAndTimeframeOrderByOpenTimeDesc("BTC-USD", "1h", PageRequest.of(0, 20)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.predictMarketRegime("BTC-USD", "1h", 20))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("No candles found for requested market regime analysis");
    }

    @Test
    void predictMarketRegimeRejectsInsufficientCandles() {
        when(marketCandleRepository.findBySymbolAndTimeframeOrderByOpenTimeDesc("BTC-USD", "1h", PageRequest.of(0, 20)))
                .thenReturn(candlesDescending(19));

        assertThatThrownBy(() -> service.predictMarketRegime("BTC-USD", "1h", 20))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("At least 20 candles are required for market regime analysis");
    }

    @Test
    void predictMarketRegimeRejectsInvalidLimit() {
        assertThatThrownBy(() -> service.predictMarketRegime("BTC-USD", "1h", 19))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("limit must be at least 20");
    }

    @Test
    void predictMarketRegimePropagatesMlFailure() {
        when(marketCandleRepository.findBySymbolAndTimeframeOrderByOpenTimeDesc("BTC-USD", "1h", PageRequest.of(0, 20)))
                .thenReturn(candlesDescending(20));
        when(mlRiskClient.predictMarketRegime(any(MlMarketRegimeRequest.class)))
                .thenThrow(new ExternalServiceException("ML service is unavailable or returned an invalid response", null));

        assertThatThrownBy(() -> service.predictMarketRegime("BTC-USD", "1h", 20))
                .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void runRegimeReplayPersistsRunAndPredictions() {
        RegimeRunRequest request = new RegimeRunRequest(
                "BTC-USD",
                "1h",
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-02T06:00:00Z"),
                20,
                5,
                false,
                null
        );
        List<MarketCandle> candles = java.util.stream.IntStream.range(0, 30)
                .mapToObj(this::candle)
                .toList();
        when(marketCandleRepository.findBySymbolAndTimeframeAndOpenTimeBetweenOrderByOpenTimeAsc(
                "BTC-USD", "1h", request.startDate(), request.endDate()
        )).thenReturn(candles);
        when(mlRiskClient.getMarketRegimeStatus()).thenReturn(status());
        when(regimeRunRepository.save(any(RegimeRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mlRiskClient.predictRegimeRun(any())).thenReturn(new com.signalattention.ml.MlRegimeRunResponse(
                "BTC-USD",
                "1h",
                20,
                5,
                false,
                1,
                List.of(new com.signalattention.ml.MlRegimeRunPoint(
                        candles.get(0).getOpenTime(),
                        candles.get(19).getOpenTime(),
                        "TRENDING_UP",
                        new BigDecimal("80.00"),
                        List.of("Price is rising."),
                        response().features(),
                        null,
                        null,
                        null
                ))
        ));
        when(regimePredictionRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RegimeRunResponse response = service.runRegimeReplay(request);

        assertThat(response.symbol()).isEqualTo("BTC-USD");
        assertThat(response.effectiveMode()).isEqualTo("rules");
        assertThat(response.pointCount()).isEqualTo(1);
        assertThat(response.points()).hasSize(1);
        assertThat(response.points().getFirst().regimeLabel()).isEqualTo("TRENDING_UP");
    }

    private List<MarketCandle> candlesDescending(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> candle(count - index))
                .toList();
    }

    private MarketCandle candle(int hour) {
        BigDecimal close = new BigDecimal("100").add(new BigDecimal(hour));
        return new MarketCandle(
                "BTC-USD",
                "1h",
                Instant.parse("2024-01-01T00:00:00Z").plusSeconds(hour * 3600L),
                close,
                close.add(BigDecimal.ONE),
                close.subtract(BigDecimal.ONE),
                close,
                new BigDecimal("1000")
        );
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
                20,
                null
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
                List.of()
        );
    }
}
