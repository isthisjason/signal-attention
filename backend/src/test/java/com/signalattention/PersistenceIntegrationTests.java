package com.signalattention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.signalattention.marketdata.MarketCandle;
import com.signalattention.marketdata.MarketCandleRepository;
import com.signalattention.strategies.Strategy;
import com.signalattention.strategies.StrategyRepository;
import com.signalattention.strategies.StrategyStatus;
import com.signalattention.strategies.StrategyType;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PersistenceIntegrationTests {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("signalattention")
            .withUsername("signalattention")
            .withPassword("signalattention");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private StrategyRepository strategyRepository;

    @Autowired
    private MarketCandleRepository marketCandleRepository;

    @Test
    void flywayMigrationsSupportStrategyPersistence() {
        Strategy strategy = new Strategy(
                "BTC SMA",
                "BTC-USD",
                "1h",
                StrategyType.SMA_CROSSOVER,
                "{\"shortWindow\":20,\"longWindow\":50,\"initialBalance\":10000,\"feePercent\":0.1,\"positionSizePercent\":25}",
                StrategyStatus.ACTIVE
        );

        Strategy saved = strategyRepository.saveAndFlush(strategy);

        assertThat(saved.getId()).isNotNull();
        assertThat(strategyRepository.findById(saved.getId())).isPresent();
    }

    @Test
    void marketCandlesRejectDuplicateSymbolTimeframeAndOpenTime() {
        Instant openTime = Instant.parse("2024-01-01T00:00:00Z");
        marketCandleRepository.saveAndFlush(candle(openTime));

        assertThatThrownBy(() -> marketCandleRepository.saveAndFlush(candle(openTime)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private MarketCandle candle(Instant openTime) {
        return new MarketCandle(
                "BTC-USD",
                "1h",
                openTime,
                new BigDecimal("42000.00"),
                new BigDecimal("42100.00"),
                new BigDecimal("41900.00"),
                new BigDecimal("42050.00"),
                new BigDecimal("12.50")
        );
    }
}
