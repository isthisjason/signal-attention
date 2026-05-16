package com.signalattention.marketregime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.signalattention.common.BadRequestException;
import com.signalattention.common.ExternalServiceException;
import com.signalattention.marketdata.MarketCandle;
import com.signalattention.marketdata.MarketCandleRepository;
import com.signalattention.ml.MlMarketRegimeFeatures;
import com.signalattention.ml.MlMarketRegimeRequest;
import com.signalattention.ml.MlMarketRegimeResponse;
import com.signalattention.ml.MlRiskClient;
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

    private MarketRegimeService service;

    @BeforeEach
    void setUp() {
        service = new MarketRegimeService(marketCandleRepository, mlRiskClient);
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
                "rules"
        );
    }
}
