package com.signalattention.marketregime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signalattention.ml.MlMarketRegimeFeatures;
import com.signalattention.ml.MlMarketRegimeResponse;
import java.math.BigDecimal;
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
                )
        );
    }
}
