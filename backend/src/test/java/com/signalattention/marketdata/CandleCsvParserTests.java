package com.signalattention.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CandleCsvParserTests {

    private final CandleCsvParser parser = new CandleCsvParser();

    @Test
    void parsesValidRows() throws Exception {
        CandleCsvParseResult result = parser.parse(csv("""
                symbol,timeframe,openTime,open,high,low,close,volume
                BTC-USD,1h,2024-01-01T00:00:00Z,42000,42100,41900,42050,12.5
                BTC-USD,1h,2024-01-01T01:00:00Z,42050,42200,42000,42150,11.2
                """));

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.candles()).hasSize(2);
        assertThat(result.errors()).isEmpty();
        assertThat(result.candles().getFirst().symbol()).isEqualTo("BTC-USD");
    }

    @Test
    void reportsMissingRequiredFields() throws Exception {
        CandleCsvParseResult result = parser.parse(csv("""
                symbol,timeframe,openTime,open,high,low,close,volume
                ,1h,2024-01-01T00:00:00Z,42000,42100,41900,42050,12.5
                """));

        assertThat(result.candles()).isEmpty();
        assertThat(result.errors()).extracting(MarketDataImportError::message)
                .contains("symbol, timeframe, and openTime are required");
    }

    @Test
    void reportsBadTimestamp() throws Exception {
        CandleCsvParseResult result = parser.parse(csv("""
                symbol,timeframe,openTime,open,high,low,close,volume
                BTC-USD,1h,not-a-time,42000,42100,41900,42050,12.5
                """));

        assertThat(result.candles()).isEmpty();
        assertThat(result.errors()).extracting(MarketDataImportError::message)
                .contains("openTime must be an ISO-8601 instant");
    }

    @Test
    void reportsBadDecimal() throws Exception {
        CandleCsvParseResult result = parser.parse(csv("""
                symbol,timeframe,openTime,open,high,low,close,volume
                BTC-USD,1h,2024-01-01T00:00:00Z,not-a-number,42100,41900,42050,12.5
                """));

        assertThat(result.candles()).isEmpty();
        assertThat(result.errors()).extracting(MarketDataImportError::message)
                .contains("open must be a valid decimal");
    }

    @Test
    void reportsNonPositiveNumericValues() throws Exception {
        CandleCsvParseResult result = parser.parse(csv("""
                symbol,timeframe,openTime,open,high,low,close,volume
                BTC-USD,1h,2024-01-01T00:00:00Z,0,42100,41900,42050,12.5
                """));

        assertThat(result.candles()).isEmpty();
        assertThat(result.errors()).extracting(MarketDataImportError::message)
                .contains("open must be greater than zero");
    }

    private ByteArrayInputStream csv(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
