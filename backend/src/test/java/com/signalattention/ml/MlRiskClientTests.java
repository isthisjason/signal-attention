package com.signalattention.ml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MlRiskClientTests {

    @Test
    void scoreStrategyRiskPostsJsonMetrics() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ml-service:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MlRiskClient client = new MlRiskClient(builder.build());

        server.expect(requestTo("http://ml-service:8000/predict/strategy-risk"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "totalReturn": -0.937989,
                          "maxDrawdown": 1.161439,
                          "winRate": 16.666667,
                          "profitFactor": 0.035966,
                          "tradeCount": 6,
                          "averageTradeReturn": -0.313687,
                          "feeDrag": 59.74179543,
                          "volatility": 0.113734
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "riskScore": "60.00",
                          "riskLabel": "MEDIUM_RISK",
                          "reasons": ["Limited trade count weakens confidence."]
                        }
                        """, MediaType.APPLICATION_JSON));

        MlStrategyRiskResponse response = client.scoreStrategyRisk(new MlStrategyRiskRequest(
                new BigDecimal("-0.937989"),
                new BigDecimal("1.161439"),
                new BigDecimal("16.666667"),
                new BigDecimal("0.035966"),
                6,
                new BigDecimal("-0.313687"),
                new BigDecimal("59.74179543"),
                new BigDecimal("0.113734")
        ));

        assertThat(response.riskLabel()).isEqualTo("MEDIUM_RISK");
        assertThat(response.riskScore()).isEqualByComparingTo("60.00");
        server.verify();
    }

    @Test
    void predictMarketRegimeMapsProvenanceFields() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ml-service:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MlRiskClient client = new MlRiskClient(builder.build());

        server.expect(requestTo("http://ml-service:8000/predict/market-regime"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("""
                        {
                          "regimeLabel": "TRENDING_UP",
                          "confidence": "81.25",
                          "reasons": ["Torch sequence model selected TRENDING_UP."],
                          "features": {
                            "latestReturnPercent": "0.50",
                            "averageReturnPercent": "0.25",
                            "volatilityPercent": "0.10",
                            "trendSlopePercent": "0.20",
                            "smaDistancePercent": "1.50",
                            "volumeZScore": "0.00"
                          },
                          "classifierSource": "torch",
                          "mode": "torch",
                          "modelVersion": "local-transformer-v1",
                          "featureVersion": "torch-market-regime-features/v1",
                          "sequenceLength": 20,
                          "artifactIdentifier": "market-regime.pt"
                        }
                        """, MediaType.APPLICATION_JSON));

        MlMarketRegimeResponse response = client.predictMarketRegime(new MlMarketRegimeRequest(
                "BTC-USD",
                "1h",
                List.of(new MlMarketRegimeCandle(
                        Instant.parse("2024-01-01T00:00:00Z"),
                        BigDecimal.TEN,
                        BigDecimal.TEN,
                        BigDecimal.TEN,
                        BigDecimal.TEN,
                        BigDecimal.ONE
                ))
        ));

        assertThat(response.mode()).isEqualTo("torch");
        assertThat(response.modelVersion()).isEqualTo("local-transformer-v1");
        assertThat(response.featureVersion()).isEqualTo("torch-market-regime-features/v1");
        assertThat(response.sequenceLength()).isEqualTo(20);
        assertThat(response.artifactIdentifier()).isEqualTo("market-regime.pt");
        server.verify();
    }

    @Test
    void getMarketRegimeStatusMapsModelReadiness() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ml-service:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MlRiskClient client = new MlRiskClient(builder.build());

        server.expect(requestTo("http://ml-service:8000/predict/market-regime/status"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess("""
                        {
                          "mode": "auto",
                          "effectiveMode": "rules",
                          "classifierSource": "rules",
                          "ready": true,
                          "artifactConfigured": false,
                          "artifactExists": false,
                          "artifactIdentifier": null,
                          "modelVersion": null,
                          "featureVersion": "torch-market-regime-features/v1",
                          "sequenceLength": null,
                          "warnings": ["auto mode fell back to rules"]
                        }
                        """, MediaType.APPLICATION_JSON));

        MlMarketRegimeStatusResponse response = client.getMarketRegimeStatus();

        assertThat(response.mode()).isEqualTo("auto");
        assertThat(response.effectiveMode()).isEqualTo("rules");
        assertThat(response.ready()).isTrue();
        assertThat(response.warnings()).hasSize(1);
        server.verify();
    }

    @Test
    void predictAnomalyPostsRecentCandles() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ml-service:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MlRiskClient client = new MlRiskClient(builder.build());

        server.expect(requestTo("http://ml-service:8000/predict/anomaly"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("""
                        {
                          "anomalyScore": "42.00",
                          "anomalyLabel": "WATCH",
                          "reasons": ["Recent volatility is rising."],
                          "features": {
                            "latestReturnPercent": "0.50",
                            "averageReturnPercent": "0.25",
                            "volatilityPercent": "2.10",
                            "trendSlopePercent": "0.20",
                            "smaDistancePercent": "1.50",
                            "volumeZScore": "0.00"
                          },
                          "classifierSource": "rules"
                        }
                        """, MediaType.APPLICATION_JSON));

        MlAnomalyResponse response = client.predictAnomaly(new MlMarketRegimeRequest(
                "BTC-USD",
                "1h",
                List.of(new MlMarketRegimeCandle(
                        Instant.parse("2024-01-01T00:00:00Z"),
                        BigDecimal.TEN,
                        BigDecimal.TEN,
                        BigDecimal.TEN,
                        BigDecimal.TEN,
                        BigDecimal.ONE
                ))
        ));

        assertThat(response.anomalyLabel()).isEqualTo("WATCH");
        assertThat(response.anomalyScore()).isEqualByComparingTo("42.00");
        server.verify();
    }

    @Test
    void predictRegimeRunPostsReplayPayload() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ml-service:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MlRiskClient client = new MlRiskClient(builder.build());

        server.expect(requestTo("http://ml-service:8000/predict/regime-run"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("""
                        {
                          "pointCount": 1,
                          "points": [{
                            "windowStart": "2024-01-01T00:00:00Z",
                            "windowEnd": "2024-01-01T19:00:00Z",
                            "regimeLabel": "TRENDING_UP",
                            "confidence": "80.00",
                            "reasons": ["Torch sequence model selected TRENDING_UP."],
                            "features": {
                              "latestReturnPercent": "0.50",
                              "averageReturnPercent": "0.25",
                              "volatilityPercent": "0.10",
                              "trendSlopePercent": "0.20",
                              "smaDistancePercent": "1.50",
                              "volumeZScore": "0.00"
                            },
                            "anomalyScore": null,
                            "anomalyLabel": null,
                            "anomalyReasons": null,
                            "baselineRegimeLabel": "SIDEWAYS",
                            "baselineConfidence": "65.00",
                            "disagreesWithBaseline": true
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        MlRegimeRunResponse response = client.predictRegimeRun(new MlRegimeRunRequest(
                "BTC-USD",
                "1h",
                List.of(new MlMarketRegimeCandle(
                        Instant.parse("2024-01-01T00:00:00Z"),
                        BigDecimal.TEN,
                        BigDecimal.TEN,
                        BigDecimal.TEN,
                        BigDecimal.TEN,
                        BigDecimal.ONE
                )),
                20,
                4,
                true
        ));

        assertThat(response.pointCount()).isEqualTo(1);
        assertThat(response.points().getFirst().baselineRegimeLabel()).isEqualTo("SIDEWAYS");
        assertThat(response.points().getFirst().disagreesWithBaseline()).isTrue();
        server.verify();
    }
}
