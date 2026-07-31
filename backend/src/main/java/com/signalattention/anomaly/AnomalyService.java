package com.signalattention.anomaly;

import com.signalattention.common.BadRequestException;
import com.signalattention.ml.MlAnomalyResponse;
import com.signalattention.ml.MlMarketRegimeRequest;
import com.signalattention.ml.MlMarketRegimeRequestFactory;
import com.signalattention.ml.MlRiskClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnomalyService {

    private final MlMarketRegimeRequestFactory requestFactory;
    private final MlRiskClient mlRiskClient;

    public AnomalyService(MlMarketRegimeRequestFactory requestFactory, MlRiskClient mlRiskClient) {
        this.requestFactory = requestFactory;
        this.mlRiskClient = mlRiskClient;
    }

    @Transactional(readOnly = true)
    public MlAnomalyResponse check(AnomalyCheckRequest request) {
        MlMarketRegimeRequest mlRequest = requestFactory.forLatestCandles(
                request.symbol(),
                request.timeframe(),
                request.limit()
        );
        if (mlRequest.candles().isEmpty()) {
            throw new BadRequestException("No candles found for requested anomaly analysis");
        }
        if (mlRequest.candles().size() < MlMarketRegimeRequestFactory.MIN_CANDLE_LIMIT) {
            throw new BadRequestException(
                    "At least " + MlMarketRegimeRequestFactory.MIN_CANDLE_LIMIT
                            + " candles are required for anomaly analysis"
            );
        }
        // Anomaly scoring uses the same recent candle window as market regime classification.
        return mlRiskClient.predictAnomaly(mlRequest);
    }
}
