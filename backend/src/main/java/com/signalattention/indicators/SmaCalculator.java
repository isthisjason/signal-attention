package com.signalattention.indicators;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SmaCalculator {

    public static final int SCALE = 8;

    public List<Optional<BigDecimal>> calculate(List<BigDecimal> prices, int window) {
        validate(prices, window);

        List<Optional<BigDecimal>> values = new ArrayList<>(prices.size());
        BigDecimal rollingSum = BigDecimal.ZERO;

        for (int index = 0; index < prices.size(); index++) {
            // Keep a rolling sum so each SMA point is O(1) instead of re-summing the window.
            rollingSum = rollingSum.add(prices.get(index));
            if (index >= window) {
                rollingSum = rollingSum.subtract(prices.get(index - window));
            }

            if (index + 1 < window) {
                values.add(Optional.empty());
            } else {
                values.add(Optional.of(rollingSum.divide(BigDecimal.valueOf(window), SCALE, RoundingMode.HALF_UP)));
            }
        }

        return values;
    }

    private void validate(List<BigDecimal> prices, int window) {
        if (window <= 0) {
            throw new IllegalArgumentException("window must be positive");
        }
        if (prices == null) {
            throw new IllegalArgumentException("prices are required");
        }
        if (prices.stream().anyMatch(price -> price == null)) {
            throw new IllegalArgumentException("prices cannot contain null values");
        }
    }
}
