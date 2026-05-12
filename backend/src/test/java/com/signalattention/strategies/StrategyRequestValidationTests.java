package com.signalattention.strategies;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StrategyRequestValidationTests {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validCreateRequestHasNoViolations() {
        StrategyCreateRequest request = new StrategyCreateRequest(
                "BTC SMA",
                "BTC-USD",
                "1h",
                StrategyType.SMA_CROSSOVER,
                validRules()
        );

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void shortWindowMustBeLessThanLongWindow() {
        SmaCrossoverRulesRequest rules = new SmaCrossoverRulesRequest(
                50,
                20,
                new BigDecimal("10000"),
                new BigDecimal("0.1"),
                new BigDecimal("25")
        );

        assertThat(validator.validate(rules))
                .anyMatch(violation -> violation.getMessage().equals("shortWindow must be less than longWindow"));
    }

    @Test
    void positionSizeMustNotExceedOneHundredPercent() {
        SmaCrossoverRulesRequest rules = new SmaCrossoverRulesRequest(
                20,
                50,
                new BigDecimal("10000"),
                new BigDecimal("0.1"),
                new BigDecimal("125")
        );

        assertThat(validator.validate(rules))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("positionSizePercent"));
    }

    private SmaCrossoverRulesRequest validRules() {
        return new SmaCrossoverRulesRequest(
                20,
                50,
                new BigDecimal("10000"),
                new BigDecimal("0.1"),
                new BigDecimal("25")
        );
    }
}
