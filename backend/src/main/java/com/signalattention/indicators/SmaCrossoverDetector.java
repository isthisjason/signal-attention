package com.signalattention.indicators;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SmaCrossoverDetector {

    private final SmaCalculator smaCalculator;

    public SmaCrossoverDetector(SmaCalculator smaCalculator) {
        this.smaCalculator = smaCalculator;
    }

    public List<CrossoverSignal> detect(List<BigDecimal> closePrices, int shortWindow, int longWindow) {
        validate(closePrices, shortWindow, longWindow);

        List<Optional<BigDecimal>> shortSma = smaCalculator.calculate(closePrices, shortWindow);
        List<Optional<BigDecimal>> longSma = smaCalculator.calculate(closePrices, longWindow);
        List<CrossoverSignal> signals = new ArrayList<>();

        for (int index = 1; index < closePrices.size(); index++) {
            Optional<BigDecimal> previousShort = shortSma.get(index - 1);
            Optional<BigDecimal> previousLong = longSma.get(index - 1);
            Optional<BigDecimal> currentShort = shortSma.get(index);
            Optional<BigDecimal> currentLong = longSma.get(index);

            if (previousShort.isEmpty() || previousLong.isEmpty() || currentShort.isEmpty() || currentLong.isEmpty()) {
                continue;
            }

            int previousComparison = previousShort.get().compareTo(previousLong.get());
            int currentComparison = currentShort.get().compareTo(currentLong.get());

            // A crossover only counts when the short SMA moves from one side of the long SMA to the other.
            if (previousComparison <= 0 && currentComparison > 0) {
                signals.add(new CrossoverSignal(index, CrossoverSignalType.BULLISH_CROSSOVER));
            } else if (previousComparison >= 0 && currentComparison < 0) {
                signals.add(new CrossoverSignal(index, CrossoverSignalType.BEARISH_CROSSOVER));
            }
        }

        return signals;
    }

    private void validate(List<BigDecimal> closePrices, int shortWindow, int longWindow) {
        if (shortWindow <= 0) {
            throw new IllegalArgumentException("shortWindow must be positive");
        }
        if (longWindow <= 0) {
            throw new IllegalArgumentException("longWindow must be positive");
        }
        if (shortWindow >= longWindow) {
            throw new IllegalArgumentException("shortWindow must be less than longWindow");
        }
        if (closePrices == null) {
            throw new IllegalArgumentException("closePrices are required");
        }
        if (closePrices.size() < longWindow) {
            return;
        }
        if (closePrices.stream().anyMatch(price -> price == null)) {
            throw new IllegalArgumentException("closePrices cannot contain null values");
        }
    }
}
