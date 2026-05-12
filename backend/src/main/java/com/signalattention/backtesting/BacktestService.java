package com.signalattention.backtesting;

import com.signalattention.common.BadRequestException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BacktestService {

    public BacktestRunResponse runBacktest(Long strategyId, BacktestRequest request) {
        throw new BadRequestException("Backtesting is not implemented yet");
    }

    public BacktestRunResponse getRun(Long id) {
        throw new BadRequestException("Backtesting is not implemented yet");
    }

    public List<BacktestTradeResponse> getTrades(Long id) {
        throw new BadRequestException("Backtesting is not implemented yet");
    }

    public BacktestMetricsResponse getMetrics(Long id) {
        throw new BadRequestException("Backtesting is not implemented yet");
    }
}
