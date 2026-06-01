package com.signalattention.backtesting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.signalattention.audit.AuditService;
import com.signalattention.common.BadRequestException;
import com.signalattention.common.ResourceNotFoundException;
import com.signalattention.indicators.CrossoverSignal;
import com.signalattention.indicators.CrossoverSignalType;
import com.signalattention.indicators.SmaCrossoverDetector;
import com.signalattention.marketdata.MarketCandle;
import com.signalattention.marketdata.MarketCandleRepository;
import com.signalattention.ml.MlRiskClient;
import com.signalattention.ml.MlStrategyRiskRequest;
import com.signalattention.ml.MlStrategyRiskResponse;
import com.signalattention.strategies.SmaCrossoverRulesRequest;
import com.signalattention.strategies.Strategy;
import com.signalattention.strategies.StrategyRepository;
import com.signalattention.strategies.StrategyType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BacktestService {

    private static final String ENTITY_TYPE = "BACKTEST";
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int MONEY_SCALE = 8;
    private static final int METRIC_SCALE = 6;

    private final StrategyRepository strategyRepository;
    private final MarketCandleRepository marketCandleRepository;
    private final BacktestRunRepository backtestRunRepository;
    private final BacktestTradeRepository backtestTradeRepository;
    private final SmaCrossoverDetector smaCrossoverDetector;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final MlRiskClient mlRiskClient;

    public BacktestService(
            StrategyRepository strategyRepository,
            MarketCandleRepository marketCandleRepository,
            BacktestRunRepository backtestRunRepository,
            BacktestTradeRepository backtestTradeRepository,
            SmaCrossoverDetector smaCrossoverDetector,
            AuditService auditService,
            ObjectMapper objectMapper,
            MlRiskClient mlRiskClient
    ) {
        this.strategyRepository = strategyRepository;
        this.marketCandleRepository = marketCandleRepository;
        this.backtestRunRepository = backtestRunRepository;
        this.backtestTradeRepository = backtestTradeRepository;
        this.smaCrossoverDetector = smaCrossoverDetector;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.mlRiskClient = mlRiskClient;
    }

    @Transactional
    public BacktestRunResponse runBacktest(Long strategyId, BacktestRequest request) {
        validateDateRange(request);
        Strategy strategy = strategyRepository.findById(strategyId)
                .orElseThrow(() -> new ResourceNotFoundException("Strategy not found: " + strategyId));

        try {
            BacktestRun run = execute(strategy, request);
            auditService.record(ENTITY_TYPE, run.getId().toString(), "BACKTEST_COMPLETED", "Backtest completed", metadataJson(run));
            return BacktestRunResponse.from(run);
        } catch (RuntimeException exception) {
            auditService.record(
                    ENTITY_TYPE,
                    strategyId.toString(),
                    "BACKTEST_FAILED",
                    exception.getMessage(),
                    "{\"strategyId\":" + strategyId + "}"
            );
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public BacktestRunResponse getRun(Long id) {
        return BacktestRunResponse.from(findRun(id));
    }

    @Transactional(readOnly = true)
    public List<BacktestTradeResponse> getTrades(Long id) {
        findRun(id);
        return backtestTradeRepository.findByBacktestRunIdOrderByEntryTimeAsc(id)
                .stream()
                .map(BacktestTradeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BacktestMetricsResponse getMetrics(Long id) {
        return BacktestMetricsResponse.from(findRun(id));
    }

    @Transactional(readOnly = true)
    public List<BacktestEquityPointResponse> getEquitySeries(Long id) {
        BacktestRun run = findRun(id);
        List<BacktestTrade> trades = backtestTradeRepository.findByBacktestRunIdOrderByEntryTimeAsc(id)
                .stream()
                .filter(trade -> trade.getExitTime() != null && trade.getNetPnl() != null)
                .sorted(Comparator.comparing(BacktestTrade::getExitTime))
                .toList();

        List<BacktestEquityPointResponse> points = new ArrayList<>();
        BigDecimal equity = run.getInitialBalance();
        points.add(new BacktestEquityPointResponse(run.getStartDate(), scaleMoney(equity)));
        for (BacktestTrade trade : trades) {
            equity = equity.add(trade.getNetPnl());
            points.add(new BacktestEquityPointResponse(trade.getExitTime(), scaleMoney(equity)));
        }
        if (run.getEndDate() != null && !points.get(points.size() - 1).timestamp().equals(run.getEndDate())) {
            BigDecimal finalEquity = run.getFinalBalance() == null ? equity : run.getFinalBalance();
            points.add(new BacktestEquityPointResponse(run.getEndDate(), scaleMoney(finalEquity)));
        }
        return points;
    }

    @Transactional(readOnly = true)
    public List<BacktestDrawdownPointResponse> getDrawdownSeries(Long id) {
        List<BacktestEquityPointResponse> equitySeries = getEquitySeries(id);
        BigDecimal peak = BigDecimal.ZERO;
        List<BacktestDrawdownPointResponse> points = new ArrayList<>();
        for (BacktestEquityPointResponse point : equitySeries) {
            if (point.equity().compareTo(peak) > 0) {
                peak = point.equity();
            }
            BigDecimal drawdown = BigDecimal.ZERO;
            if (peak.signum() > 0) {
                drawdown = peak.subtract(point.equity())
                        .divide(peak, 12, RoundingMode.HALF_UP)
                        .multiply(ONE_HUNDRED);
            }
            points.add(new BacktestDrawdownPointResponse(point.timestamp(), scaleMetric(drawdown)));
        }
        return points;
    }

    @Transactional
    public MlStrategyRiskResponse scoreMlRisk(Long id) {
        BacktestRun run = findRun(id);
        try {
            MlStrategyRiskResponse response = mlRiskClient.scoreStrategyRisk(new MlStrategyRiskRequest(
                    run.getTotalReturn(),
                    run.getMaxDrawdown(),
                    run.getWinRate(),
                    run.getProfitFactor(),
                    run.getTradeCount(),
                    run.getAverageTradeReturn(),
                    run.getFeeDrag(),
                    run.getVolatility()
            ));
            run.setMlRiskScore(response.riskScore());
            run.setMlRiskLabel(response.riskLabel());
            backtestRunRepository.save(run);
            auditService.record(ENTITY_TYPE, id.toString(), "ML_RISK_SCORE_COMPLETED", "ML risk score completed", mlMetadataJson(run, response));
            return response;
        } catch (RuntimeException exception) {
            auditService.record(ENTITY_TYPE, id.toString(), "ML_RISK_SCORE_FAILED", exception.getMessage(), "{\"backtestRunId\":" + id + "}");
            throw exception;
        }
    }

    private BacktestRun execute(Strategy strategy, BacktestRequest request) {
        if (strategy.getStrategyType() != StrategyType.SMA_CROSSOVER) {
            throw new BadRequestException("Unsupported strategy type: " + strategy.getStrategyType());
        }

        // Request values can override saved strategy rules for one-off backtest runs.
        SmaCrossoverRulesRequest rules = parseRules(strategy);
        BigDecimal initialBalance = override(request.initialBalance(), rules.initialBalance());
        BigDecimal feePercent = override(request.feePercent(), rules.feePercent());
        BigDecimal positionSizePercent = override(request.positionSizePercent(), rules.positionSizePercent());

        List<MarketCandle> candles = marketCandleRepository.findBySymbolAndTimeframeAndOpenTimeBetweenOrderByOpenTimeAsc(
                strategy.getSymbol(),
                strategy.getTimeframe(),
                request.startDate(),
                request.endDate()
        );
        if (candles.isEmpty()) {
            throw new BadRequestException("No candles found for requested backtest range");
        }
        if (candles.size() < rules.longWindow()) {
            throw new BadRequestException("Not enough candles for long SMA window");
        }

        // Save the run first so generated trades can reference a real backtest id.
        BacktestRun run = backtestRunRepository.save(new BacktestRun(
                strategy,
                request.startDate(),
                request.endDate(),
                scaleMoney(initialBalance),
                BacktestStatus.COMPLETED
        ));

        SimulationResult result = simulate(run, candles, rules, initialBalance, feePercent, positionSizePercent);
        backtestTradeRepository.saveAll(result.trades());

        run.setFinalBalance(scaleMoney(result.finalBalance()));
        run.setTotalReturn(percentChange(result.finalBalance(), initialBalance));
        run.setMaxDrawdown(scaleMetric(result.maxDrawdown()));
        run.setWinRate(winRate(result.trades()));
        run.setProfitFactor(profitFactor(result.trades()));
        run.setTradeCount(result.trades().size());
        run.setAverageTradeReturn(averageTradeReturn(result.trades()));
        run.setFeeDrag(scaleMoney(result.totalFees()));
        run.setVolatility(scaleMetric(result.volatility()));
        run.setCompletedAt(Instant.now());
        return backtestRunRepository.save(run);
    }

    private SimulationResult simulate(
            BacktestRun run,
            List<MarketCandle> candles,
            SmaCrossoverRulesRequest rules,
            BigDecimal initialBalance,
            BigDecimal feePercent,
            BigDecimal positionSizePercent
    ) {
        Map<Integer, CrossoverSignalType> signalsByIndex = smaCrossoverDetector.detect(
                        candles.stream().map(MarketCandle::getClose).toList(),
                        rules.shortWindow(),
                        rules.longWindow()
                )
                .stream()
                .collect(Collectors.toMap(CrossoverSignal::index, CrossoverSignal::type));

        BigDecimal cash = initialBalance;
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal entryPrice = BigDecimal.ZERO;
        BigDecimal entryFee = BigDecimal.ZERO;
        Instant entryTime = null;
        List<BacktestTrade> trades = new ArrayList<>();
        List<BigDecimal> equityCurve = new ArrayList<>();
        BigDecimal totalFees = BigDecimal.ZERO;

        for (int index = 0; index < candles.size(); index++) {
            MarketCandle candle = candles.get(index);
            CrossoverSignalType signal = signalsByIndex.get(index);

            if (signal == CrossoverSignalType.BULLISH_CROSSOVER && quantity.signum() == 0) {
                // Long-only simulation: buy once on a bullish cross using the configured cash percent.
                BigDecimal allocation = cash.multiply(positionSizePercent).divide(ONE_HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);
                entryFee = percentOf(allocation, feePercent);
                BigDecimal entryNotional = allocation.subtract(entryFee);
                if (entryNotional.signum() > 0) {
                    quantity = entryNotional.divide(candle.getClose(), MONEY_SCALE, RoundingMode.HALF_UP);
                    entryPrice = candle.getClose();
                    entryTime = candle.getOpenTime();
                    cash = cash.subtract(allocation);
                    totalFees = totalFees.add(entryFee);
                }
            } else if (signal == CrossoverSignalType.BEARISH_CROSSOVER && quantity.signum() > 0) {
                // A bearish cross exits the current long position and realizes P&L.
                TradeClose close = closeTrade(run, entryTime, entryPrice, quantity, entryFee, candle, feePercent);
                cash = cash.add(close.cashReturned());
                totalFees = totalFees.add(close.exitFee());
                trades.add(close.trade());
                quantity = BigDecimal.ZERO;
                entryPrice = BigDecimal.ZERO;
                entryFee = BigDecimal.ZERO;
                entryTime = null;
            }

            equityCurve.add(markEquity(cash, quantity, candle.getClose(), feePercent));
        }

        if (quantity.signum() > 0) {
            // Close any open position on the final candle so metrics use a completed equity curve.
            MarketCandle lastCandle = candles.get(candles.size() - 1);
            TradeClose close = closeTrade(run, entryTime, entryPrice, quantity, entryFee, lastCandle, feePercent);
            cash = cash.add(close.cashReturned());
            totalFees = totalFees.add(close.exitFee());
            trades.add(close.trade());
            equityCurve.add(cash);
        }

        return new SimulationResult(cash, trades, totalFees, maxDrawdown(equityCurve), volatility(equityCurve));
    }

    // Converts an open position into a persisted trade and returns the cash after exit fees.
    private TradeClose closeTrade(
            BacktestRun run,
            Instant entryTime,
            BigDecimal entryPrice,
            BigDecimal quantity,
            BigDecimal entryFee,
            MarketCandle exitCandle,
            BigDecimal feePercent
    ) {
        BigDecimal exitValue = quantity.multiply(exitCandle.getClose()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal exitFee = percentOf(exitValue, feePercent);
        BigDecimal entryValue = quantity.multiply(entryPrice).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal grossPnl = exitValue.subtract(entryValue);
        BigDecimal fees = entryFee.add(exitFee);
        BigDecimal netPnl = grossPnl.subtract(fees);

        BacktestTrade trade = new BacktestTrade(run, TradeSide.LONG, entryTime, scaleMoney(entryPrice), quantity);
        trade.setExitTime(exitCandle.getOpenTime());
        trade.setExitPrice(scaleMoney(exitCandle.getClose()));
        trade.setGrossPnl(scaleMoney(grossPnl));
        trade.setFees(scaleMoney(fees));
        trade.setNetPnl(scaleMoney(netPnl));
        trade.setReturnPercent(scaleMetric(netPnl.divide(entryValue.add(entryFee), 12, RoundingMode.HALF_UP).multiply(ONE_HUNDRED)));
        return new TradeClose(trade, exitValue.subtract(exitFee), exitFee);
    }

    // Mark-to-market equity lets drawdown and volatility include open positions.
    private BigDecimal markEquity(BigDecimal cash, BigDecimal quantity, BigDecimal closePrice, BigDecimal feePercent) {
        if (quantity.signum() == 0) {
            return cash;
        }
        BigDecimal exitValue = quantity.multiply(closePrice).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return cash.add(exitValue).subtract(percentOf(exitValue, feePercent));
    }

    // Drawdown is measured from the highest prior equity point.
    private BigDecimal maxDrawdown(List<BigDecimal> equityCurve) {
        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        for (BigDecimal equity : equityCurve) {
            if (equity.compareTo(peak) > 0) {
                peak = equity;
            }
            if (peak.signum() > 0) {
                BigDecimal drawdown = peak.subtract(equity).divide(peak, 12, RoundingMode.HALF_UP).multiply(ONE_HUNDRED);
                if (drawdown.compareTo(maxDrawdown) > 0) {
                    maxDrawdown = drawdown;
                }
            }
        }
        return maxDrawdown;
    }

    // Uses equity returns, not candle returns, because fees and position sizing affect the account curve.
    private BigDecimal volatility(List<BigDecimal> equityCurve) {
        if (equityCurve.size() < 2) {
            return BigDecimal.ZERO;
        }
        List<BigDecimal> returns = new ArrayList<>();
        for (int index = 1; index < equityCurve.size(); index++) {
            BigDecimal previous = equityCurve.get(index - 1);
            BigDecimal current = equityCurve.get(index);
            if (previous.signum() > 0) {
                returns.add(current.subtract(previous).divide(previous, 12, RoundingMode.HALF_UP).multiply(ONE_HUNDRED));
            }
        }
        if (returns.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal mean = returns.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(returns.size()), 12, RoundingMode.HALF_UP);
        BigDecimal variance = returns.stream()
                .map(value -> value.subtract(mean).multiply(value.subtract(mean)))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(returns.size()), 12, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
    }

    private BigDecimal winRate(List<BacktestTrade> trades) {
        if (trades.isEmpty()) {
            return BigDecimal.ZERO.setScale(METRIC_SCALE, RoundingMode.HALF_UP);
        }
        long wins = trades.stream().filter(trade -> trade.getNetPnl().signum() > 0).count();
        return BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(trades.size()), 12, RoundingMode.HALF_UP).multiply(ONE_HUNDRED)
                .setScale(METRIC_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal profitFactor(List<BacktestTrade> trades) {
        BigDecimal grossProfit = trades.stream()
                .map(BacktestTrade::getNetPnl)
                .filter(value -> value.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grossLoss = trades.stream()
                .map(BacktestTrade::getNetPnl)
                .filter(value -> value.signum() < 0)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (grossLoss.signum() == 0) {
            return grossProfit.signum() == 0
                    ? BigDecimal.ZERO.setScale(METRIC_SCALE, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(999).setScale(METRIC_SCALE, RoundingMode.HALF_UP);
        }
        return grossProfit.divide(grossLoss, METRIC_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal averageTradeReturn(List<BacktestTrade> trades) {
        if (trades.isEmpty()) {
            return BigDecimal.ZERO.setScale(METRIC_SCALE, RoundingMode.HALF_UP);
        }
        return trades.stream()
                .map(BacktestTrade::getReturnPercent)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(trades.size()), METRIC_SCALE, RoundingMode.HALF_UP);
    }

    private BacktestRun findRun(Long id) {
        return backtestRunRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Backtest run not found: " + id));
    }

    private void validateDateRange(BacktestRequest request) {
        if (request.startDate().isAfter(request.endDate())) {
            throw new BadRequestException("startDate must be before or equal to endDate");
        }
    }

    private SmaCrossoverRulesRequest parseRules(Strategy strategy) {
        try {
            return objectMapper.readValue(strategy.getRulesJson(), SmaCrossoverRulesRequest.class);
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Invalid strategy rules", exception);
        }
    }

    private BigDecimal override(BigDecimal requested, BigDecimal fallback) {
        return requested == null ? fallback : requested;
    }

    private BigDecimal percentOf(BigDecimal value, BigDecimal percent) {
        return value.multiply(percent).divide(ONE_HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal percentChange(BigDecimal finalBalance, BigDecimal initialBalance) {
        return finalBalance.subtract(initialBalance)
                .divide(initialBalance, 12, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED)
                .setScale(METRIC_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleMoney(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleMetric(BigDecimal value) {
        return value.setScale(METRIC_SCALE, RoundingMode.HALF_UP);
    }

    private String metadataJson(BacktestRun run) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "backtestRunId", run.getId(),
                    "strategyId", run.getStrategy().getId(),
                    "tradeCount", run.getTradeCount(),
                    "totalReturn", run.getTotalReturn()
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize audit metadata", exception);
        }
    }

    private String mlMetadataJson(BacktestRun run, MlStrategyRiskResponse response) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "backtestRunId", run.getId(),
                    "riskScore", response.riskScore(),
                    "riskLabel", response.riskLabel()
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize audit metadata", exception);
        }
    }

    private record TradeClose(BacktestTrade trade, BigDecimal cashReturned, BigDecimal exitFee) {
    }

    private record SimulationResult(
            BigDecimal finalBalance,
            List<BacktestTrade> trades,
            BigDecimal totalFees,
            BigDecimal maxDrawdown,
            BigDecimal volatility
    ) {
    }
}
