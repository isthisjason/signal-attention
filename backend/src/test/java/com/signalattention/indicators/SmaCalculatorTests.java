package com.signalattention.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SmaCalculatorTests {

    private final SmaCalculator smaCalculator = new SmaCalculator();

    @Test
    void calculatesNormalSmaValues() {
        List<Optional<BigDecimal>> values = smaCalculator.calculate(prices("10", "20", "30", "40"), 2);

        assertThat(values).containsExactly(
                Optional.empty(),
                Optional.of(new BigDecimal("15.00000000")),
                Optional.of(new BigDecimal("25.00000000")),
                Optional.of(new BigDecimal("35.00000000"))
        );
    }

    @Test
    void returnsNoValuesForInsufficientHistory() {
        List<Optional<BigDecimal>> values = smaCalculator.calculate(prices("10", "20"), 3);

        assertThat(values).containsExactly(Optional.empty(), Optional.empty());
    }

    @Test
    void firstAvailableSmaIsAtWindowMinusOneIndex() {
        List<Optional<BigDecimal>> values = smaCalculator.calculate(prices("10", "20", "30"), 3);

        assertThat(values.get(0)).isEmpty();
        assertThat(values.get(1)).isEmpty();
        assertThat(values.get(2)).contains(new BigDecimal("20.00000000"));
    }

    @Test
    void rejectsZeroWindow() {
        assertThatThrownBy(() -> smaCalculator.calculate(prices("10"), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("window must be positive");
    }

    @Test
    void rejectsNegativeWindow() {
        assertThatThrownBy(() -> smaCalculator.calculate(prices("10"), -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("window must be positive");
    }

    @Test
    void rejectsNullPrices() {
        assertThatThrownBy(() -> smaCalculator.calculate(null, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("prices are required");
    }

    @Test
    void rejectsNullPriceValues() {
        assertThatThrownBy(() -> smaCalculator.calculate(Arrays.asList(new BigDecimal("10"), null), 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("prices cannot contain null values");
    }

    @Test
    void producesDeterministicRoundedOutput() {
        List<Optional<BigDecimal>> values = smaCalculator.calculate(prices("10", "10", "11"), 3);

        assertThat(values.get(2)).contains(new BigDecimal("10.33333333"));
    }

    private List<BigDecimal> prices(String... values) {
        return Arrays.stream(values)
                .map(BigDecimal::new)
                .toList();
    }
}
