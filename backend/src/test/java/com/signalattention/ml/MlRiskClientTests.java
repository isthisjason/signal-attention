package com.signalattention.ml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
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
}
