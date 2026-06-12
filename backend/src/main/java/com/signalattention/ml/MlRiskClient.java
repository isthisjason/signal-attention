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
        // The client is intentionally small: the backend translates domain data before it crosses this boundary.
        this.restClient = restClient;
    }

    // Backtest metrics are sent to the ML service only after the backend has persisted the run.
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
                // Treat protocol-level emptiness the same as an unavailable service for callers.
                throw new ExternalServiceException("ML service returned an empty response", null);
            }
            return response;
        } catch (RestClientException exception) {
            throw new ExternalServiceException("ML service is unavailable or returned an invalid response", exception);
        }
    }

    // Market regime and anomaly requests reuse the same candle sequence shape.
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

    public MlMarketRegimeStatusResponse getMarketRegimeStatus() {
        try {
            // Status is read-only model provenance, so the backend can expose it without candle context.
            MlMarketRegimeStatusResponse response = restClient.get()
                    .uri("/predict/market-regime/status")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(MlMarketRegimeStatusResponse.class);
            if (response == null) {
                throw new ExternalServiceException("ML service returned an empty response", null);
            }
            return response;
        } catch (RestClientException exception) {
            throw new ExternalServiceException("ML service is unavailable or returned an invalid response", exception);
        }
    }

    // A null body is treated as a service failure because the dashboard needs a complete payload.
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

    // Regime replay asks ML for rolling-window labels while the backend adds candle and trade context.
    public MlRegimeRunResponse predictRegimeRun(MlRegimeRunRequest request) {
        try {
            // The ML service owns rolling-window classification; persistence and chart context remain in the backend.
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
