package com.signalattention.ml;

import com.signalattention.common.ExternalServiceException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class MlRiskClient {

    private final RestClient restClient;

    public MlRiskClient(MlServiceProperties properties, RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .build();
    }

    public MlStrategyRiskResponse scoreStrategyRisk(MlStrategyRiskRequest request) {
        try {
            MlStrategyRiskResponse response = restClient.post()
                    .uri("/predict/strategy-risk")
                    .body(request)
                    .retrieve()
                    .body(MlStrategyRiskResponse.class);
            if (response == null) {
                throw new ExternalServiceException("ML service returned an empty response", null);
            }
            return response;
        } catch (RestClientException exception) {
            throw new ExternalServiceException("ML service is unavailable or returned an invalid response", exception);
        }
    }

    public MlMarketRegimeResponse predictMarketRegime(MlMarketRegimeRequest request) {
        try {
            MlMarketRegimeResponse response = restClient.post()
                    .uri("/predict/market-regime")
                    .body(request)
                    .retrieve()
                    .body(MlMarketRegimeResponse.class);
            if (response == null) {
                throw new ExternalServiceException("ML service returned an empty response", null);
            }
            return response;
        } catch (RestClientException exception) {
            throw new ExternalServiceException("ML service is unavailable or returned an invalid response", exception);
        }
    }
}
