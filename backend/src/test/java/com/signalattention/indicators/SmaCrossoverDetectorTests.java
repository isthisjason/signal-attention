package com.signalattention.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SmaCrossoverDetectorTests {

    private final SmaCrossoverDetector detector = new SmaCrossoverDetector(new SmaCalculator());

    @Test
    void detectsBullishCrossover() {
        List<CrossoverSignal> signals = detector.detect(prices("5", "4", "3", "4", "5", "6"), 2, 3);

        assertThat(signals).containsExactly(new CrossoverSignal(4, CrossoverSignalType.BULLISH_CROSSOVER));
    }

    @Test
    void detectsBearishCrossover() {
        List<CrossoverSignal> signals = detector.detect(prices("1", "2", "3", "2", "1", "0.5"), 2, 3);

        assertThat(signals).containsExactly(new CrossoverSignal(4, CrossoverSignalType.BEARISH_CROSSOVER));
    }

    @Test
    void emitsNoSignalsWithInsufficientHistory() {
        List<CrossoverSignal> signals = detector.detect(prices("10", "11"), 2, 3);

        assertThat(signals).isEmpty();
    }

    @Test
    void emitsNoSignalWhileAveragesRemainEqual() {
        List<CrossoverSignal> signals = detector.detect(prices("10", "10", "10", "10", "10"), 2, 3);

        assertThat(signals).isEmpty();
    }

    @Test
    void rejectsInvalidWindowOrder() {
        assertThatThrownBy(() -> detector.detect(prices("10", "11", "12"), 3, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("shortWindow must be less than longWindow");
    }

    @Test
    void rejectsZeroShortWindow() {
        assertThatThrownBy(() -> detector.detect(prices("10", "11", "12"), 0, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("shortWindow must be positive");
    }

    @Test
    void rejectsZeroLongWindow() {
        assertThatThrownBy(() -> detector.detect(prices("10", "11", "12"), 2, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("longWindow must be positive");
    }

    @Test
    void reportsDeterministicSignalIndexOnSampleData() {
        List<CrossoverSignal> signals = detector.detect(prices("10", "9", "8", "9", "10", "11", "12"), 2, 4);

        assertThat(signals).containsExactly(new CrossoverSignal(4, CrossoverSignalType.BULLISH_CROSSOVER));
    }

    private List<BigDecimal> prices(String... values) {
        return Arrays.stream(values)
                .map(BigDecimal::new)
                .toList();
    }
}
