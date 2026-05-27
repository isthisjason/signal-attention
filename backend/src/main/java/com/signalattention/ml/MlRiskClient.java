package com.signalattention.ml;

import com.signalattention.common.ExternalServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class MlRiskClient {

    private final RestClient restClient;

    @Autowired
    public MlRiskClient(MlServiceProperties properties, RestClient.Builder restClientBuilder) {
        this(restClientBuilder
                .baseUrl(properties.baseUrl())
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build());
    }

    MlRiskClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public MlStrategyRiskResponse scoreStrategyRisk(MlStrategyRiskRequest request) {
        try {
            MlStrategyRiskResponse response = restClient.post()
                    .uri("/predict/strategy-risk")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
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
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
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

    public MlAnomalyResponse predictAnomaly(MlMarketRegimeRequest request) {
        try {
            MlAnomalyResponse response = restClient.post()
                    .uri("/predict/anomaly")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(MlAnomalyResponse.class);
            if (response == null) {
                throw new ExternalServiceException("ML service returned an empty response", null);
            }
            return response;
        } catch (RestClientException exception) {
            throw new ExternalServiceException("ML service is unavailable or returned an invalid response", exception);
        }
    }

    public MlRegimeRunResponse predictRegimeRun(MlRegimeRunRequest request) {
        try {
            MlRegimeRunResponse response = restClient.post()
                    .uri("/predict/regime-run")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(MlRegimeRunResponse.class);
            if (response == null) {
                throw new ExternalServiceException("ML service returned an empty response", null);
            }
            return response;
        } catch (RestClientException exception) {
            throw new ExternalServiceException("ML service is unavailable or returned an invalid response", exception);
        }
    }
}
