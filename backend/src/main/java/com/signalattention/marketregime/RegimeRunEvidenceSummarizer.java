package com.signalattention.marketregime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class RegimeRunEvidenceSummarizer {

    private static final BigDecimal LOW_CONFIDENCE_THRESHOLD = new BigDecimal("60.00");
    private static final BigDecimal NEEDS_REVIEW_DISAGREEMENT_RATE = new BigDecimal("35.000000");
    private static final BigDecimal MIXED_DISAGREEMENT_RATE = new BigDecimal("10.000000");

    public RegimeRunQualitySummary summarize(List<RegimePrediction> predictions) {
        if (predictions.isEmpty()) {
            return new RegimeRunQualitySummary(null, 0, 0, BigDecimal.ZERO.setScale(6), 0, null, Map.of());
        }
        BigDecimal totalConfidence = predictions.stream()
                .map(RegimePrediction::getConfidence)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long confidenceCount = predictions.stream().map(RegimePrediction::getConfidence).filter(Objects::nonNull).count();
        BigDecimal averageConfidence = confidenceCount == 0
                ? null
                : totalConfidence.divide(BigDecimal.valueOf(confidenceCount), 6, RoundingMode.HALF_UP);
        int lowConfidenceCount = (int) predictions.stream()
                .filter(prediction -> prediction.getConfidence() != null && prediction.getConfidence().compareTo(LOW_CONFIDENCE_THRESHOLD) < 0)
                .count();
        int disagreementCount = (int) predictions.stream()
                .filter(prediction -> Boolean.TRUE.equals(prediction.getDisagreesWithBaseline()))
                .count();
        BigDecimal disagreementRate = BigDecimal.valueOf(disagreementCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(predictions.size()), 6, RoundingMode.HALF_UP);
        int anomalyCount = (int) predictions.stream()
                .filter(prediction -> prediction.getAnomalyLabel() != null && !"NORMAL".equalsIgnoreCase(prediction.getAnomalyLabel()))
                .count();
        Map<String, Integer> regimeCounts = predictions.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        RegimePrediction::getRegimeLabel,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.counting(), Long::intValue)
                ));
        String dominantRegime = regimeCounts.entrySet().stream()
                .max(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .orElse(null);
        // Quality summaries are descriptive evidence only; the UI must avoid treating them as trading signals.
        return new RegimeRunQualitySummary(
                averageConfidence,
                lowConfidenceCount,
                disagreementCount,
                disagreementRate,
                anomalyCount,
                dominantRegime,
                regimeCounts
        );
    }

    public String robustnessLabel(RegimeRunQualitySummary quality, boolean noPredictions) {
        if (noPredictions || quality.lowConfidenceWindowCount() > 0 || quality.anomalyCount() > 0) {
            return "needs_review";
        }
        if (quality.baselineDisagreementRate().compareTo(NEEDS_REVIEW_DISAGREEMENT_RATE) > 0) {
            return "needs_review";
        }
        if (quality.baselineDisagreementRate().compareTo(MIXED_DISAGREEMENT_RATE) > 0) {
            return "mixed";
        }
        return "stable";
    }

    public List<String> robustnessReasons(
            RegimeRunQualitySummary quality,
            List<RegimeBacktestAnalysisResponse.RegimeBacktestBucket> buckets,
            boolean noPredictions
    ) {
        List<String> reasons = new ArrayList<>();
        if (noPredictions) {
            reasons.add("No persisted regime windows are available for this run.");
            return reasons;
        }
        if (quality.lowConfidenceWindowCount() > 0) {
            reasons.add(quality.lowConfidenceWindowCount() + " windows fell below the confidence review threshold.");
        }
        if (quality.baselineDisagreementCount() > 0) {
            reasons.add("Attention labels disagreed with the rule baseline in " + quality.baselineDisagreementCount() + " windows.");
        }
        if (quality.anomalyCount() > 0) {
            reasons.add(quality.anomalyCount() + " windows overlapped anomaly warnings.");
        }
        if (!buckets.isEmpty()) {
            reasons.add("Backtest trades were grouped across " + buckets.size() + " inferred regimes.");
        }
        if (reasons.isEmpty()) {
            reasons.add("Regime windows were high confidence, baseline aligned, and anomaly free.");
        }
        // These reasons are review prompts only; they deliberately avoid buy/sell language.
        return reasons;
    }
}
