package com.signalattention.ml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.signalattention.common.BadRequestException;
import com.signalattention.marketdata.MarketCandle;
import com.signalattention.marketdata.MarketCandleRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class MlMarketRegimeRequestFactoryTests {

    @Mock
    private MarketCandleRepository marketCandleRepository;

    private MlMarketRegimeRequestFactory factory;

    @BeforeEach
    void setUp() {
        factory = new MlMarketRegimeRequestFactory(marketCandleRepository);
    }

    @Test
    void latestRequestNormalizesMarketAndRestoresChronologicalOrder() {
        when(marketCandleRepository.findBySymbolAndTimeframeOrderByOpenTimeDesc(
                "BTC-USD",
                "1h",
                PageRequest.of(0, MlMarketRegimeRequestFactory.DEFAULT_CANDLE_LIMIT)
        )).thenReturn(List.of(candle(2), candle(1)));

        MlMarketRegimeRequest request = factory.forLatestCandles(" BTC-USD ", " 1h ", null);

        assertThat(request.symbol()).isEqualTo("BTC-USD");
        assertThat(request.timeframe()).isEqualTo("1h");
        assertThat(request.candles()).extracting(MlMarketRegimeCandle::openTime)
                .containsExactly(candle(1).getOpenTime(), candle(2).getOpenTime());
        assertThat(request.candles().getFirst().close()).isEqualByComparingTo("101");
    }

    @Test
    void historicalRequestKeepsLatestCandlesThroughSelectedEndpoint() {
        Instant windowEnd = Instant.parse("2024-01-02T00:00:00Z");
        List<MarketCandle> candles = java.util.stream.IntStream.range(0, 25)
                .mapToObj(this::candle)
                .toList();
        when(marketCandleRepository.findBySymbolAndTimeframeAndOpenTimeBetweenOrderByOpenTimeAsc(
                "BTC-USD",
                "1h",
                Instant.EPOCH,
                windowEnd
        )).thenReturn(candles);

        MlMarketRegimeRequest request = factory.forCandlesEndingAt("BTC-USD", "1h", 20, windowEnd);

        assertThat(request.candles()).hasSize(20);
        assertThat(request.candles().getFirst().openTime()).isEqualTo(candle(5).getOpenTime());
        assertThat(request.candles().getLast().openTime()).isEqualTo(candle(24).getOpenTime());
    }

    @Test
    void requestRejectsMissingMarketAndOutOfRangeLimits() {
        assertThatThrownBy(() -> factory.forLatestCandles(" ", "1h", 20))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("symbol is required");
        assertThatThrownBy(() -> factory.forLatestCandles("BTC-USD", "1h", 19))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("limit must be at least 20");
        assertThatThrownBy(() -> factory.forLatestCandles("BTC-USD", "1h", 501))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("limit must be less than or equal to 500");
    }

    private MarketCandle candle(int hour) {
        BigDecimal close = new BigDecimal("100").add(BigDecimal.valueOf(hour));
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
}
