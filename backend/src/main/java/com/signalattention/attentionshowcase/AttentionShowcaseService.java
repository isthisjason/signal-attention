package com.signalattention.attentionshowcase;

import com.signalattention.marketregime.RegimeEvidenceSnapshotRepository;
import com.signalattention.marketregime.RegimePrediction;
import com.signalattention.marketregime.RegimePredictionRepository;
import com.signalattention.marketregime.RegimeRun;
import com.signalattention.marketregime.RegimeRunEvidenceSummarizer;
import com.signalattention.marketregime.RegimeRunQualitySummary;
import com.signalattention.marketregime.RegimeRunRepository;
import com.signalattention.ml.MlMarketRegimeStatusResponse;
import com.signalattention.ml.MlRiskClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttentionShowcaseService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final MlRiskClient mlRiskClient;
    private final RegimeRunRepository regimeRunRepository;
    private final RegimePredictionRepository regimePredictionRepository;
    private final RegimeEvidenceSnapshotRepository evidenceSnapshotRepository;
    private final RegimeRunEvidenceSummarizer evidenceSummarizer;

    public AttentionShowcaseService(
            MlRiskClient mlRiskClient,
            RegimeRunRepository regimeRunRepository,
            RegimePredictionRepository regimePredictionRepository,
            RegimeEvidenceSnapshotRepository evidenceSnapshotRepository,
            RegimeRunEvidenceSummarizer evidenceSummarizer
    ) {
        this.mlRiskClient = mlRiskClient;
        this.regimeRunRepository = regimeRunRepository;
        this.regimePredictionRepository = regimePredictionRepository;
        this.evidenceSnapshotRepository = evidenceSnapshotRepository;
        this.evidenceSummarizer = evidenceSummarizer;
    }

    @Transactional(readOnly = true)
    public AttentionShowcaseSummaryResponse getSummary() {
        MlMarketRegimeStatusResponse status = mlRiskClient.getMarketRegimeStatus();
        RegimeRun latestRun = regimeRunRepository.findFirstByOrderByCreatedAtDesc().orElse(null);
        if (latestRun == null) {
            return emptyReplaySummary(status);
        }

        List<RegimePrediction> predictions = regimePredictionRepository.findByRegimeRunIdOrderByWindowStartAsc(latestRun.getId());
        RegimeRunQualitySummary quality = evidenceSummarizer.summarize(predictions);
        List<String> reviewReasons = evidenceSummarizer.robustnessReasons(quality, List.of(), predictions.isEmpty());
        long snapshotCount = evidenceSnapshotRepository.countBySymbolAndTimeframe(latestRun.getSymbol(), latestRun.getTimeframe());

        return new AttentionShowcaseSummaryResponse(
                status.ready(),
                status.mode(),
                status.effectiveMode(),
                status.classifierSource(),
                status.promotionStatus(),
                status.promotedRunId(),
                status.promotionArtifactMatches(),
                new AttentionShowcaseSummaryResponse.LatestRun(
                        latestRun.getId(),
                        latestRun.getSymbol(),
                        latestRun.getTimeframe(),
                        latestRun.getStartDate(),
                        latestRun.getEndDate(),
                        latestRun.getPointCount(),
                        latestRun.getStatus(),
                        latestRun.getCreatedAt(),
                        quality
                ),
                evidenceSummarizer.robustnessLabel(quality, predictions.isEmpty()),
                reviewReasons,
                snapshotCount,
                summarizeDisagreements(predictions),
                nextAction(status, latestRun, predictions, snapshotCount),
                safeWarnings(status)
        );
    }

    private AttentionShowcaseSummaryResponse emptyReplaySummary(MlMarketRegimeStatusResponse status) {
        return new AttentionShowcaseSummaryResponse(
                status.ready(),
                status.mode(),
                status.effectiveMode(),
                status.classifierSource(),
                status.promotionStatus(),
                status.promotedRunId(),
                status.promotionArtifactMatches(),
                null,
                "needs_replay",
                List.of("No saved regime replay exists yet."),
                0L,
                new AttentionShowcaseSummaryResponse.DisagreementSummary(
                        0,
                        0,
                        BigDecimal.ZERO.setScale(6),
                        null,
                        null,
                        0,
                        List.of()
                ),
                "Run an attention regime replay after importing candles and creating the SMA baseline.",
                safeWarnings(status)
        );
    }

    private AttentionShowcaseSummaryResponse.DisagreementSummary summarizeDisagreements(List<RegimePrediction> predictions) {
        int disagreementCount = (int) predictions.stream()
                .filter(prediction -> Boolean.TRUE.equals(prediction.getDisagreesWithBaseline()))
                .count();
        BigDecimal disagreementRate = predictions.isEmpty()
                ? BigDecimal.ZERO.setScale(6)
                : BigDecimal.valueOf(disagreementCount)
                        .multiply(ONE_HUNDRED)
                        .divide(BigDecimal.valueOf(predictions.size()), 6, RoundingMode.HALF_UP);

        List<AttentionShowcaseSummaryResponse.DisagreementWindow> lowestConfidenceWindows = predictions.stream()
                .filter(prediction -> Boolean.TRUE.equals(prediction.getDisagreesWithBaseline()))
                .sorted(Comparator.comparing(RegimePrediction::getConfidence, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(5)
                .map(prediction -> new AttentionShowcaseSummaryResponse.DisagreementWindow(
                        prediction.getWindowEnd(),
                        prediction.getRegimeLabel(),
                        prediction.getBaselineRegimeLabel(),
                        prediction.getConfidence(),
                        prediction.getAnomalyLabel()
                ))
                .toList();

        return new AttentionShowcaseSummaryResponse.DisagreementSummary(
                predictions.size(),
                disagreementCount,
                disagreementRate,
                dominantLabel(predictions, true),
                dominantLabel(predictions, false),
                anomalyOverlapCount(predictions),
                lowestConfidenceWindows
        );
    }

    private String nextAction(MlMarketRegimeStatusResponse status, RegimeRun latestRun, List<RegimePrediction> predictions, long snapshotCount) {
        if (!Boolean.TRUE.equals(status.ready())) {
            return "Check ML service status before running the attention showcase.";
        }
        if (latestRun == null || predictions.isEmpty()) {
            return "Run an attention regime replay for the current sample window.";
        }
        if (snapshotCount == 0) {
            return "Open attention diagnostics for the latest replay window to save evidence.";
        }
        if (predictions.stream().anyMatch(prediction -> Boolean.TRUE.equals(prediction.getDisagreesWithBaseline()))) {
            return "Inspect the lowest confidence disagreement windows.";
        }
        return "Review the latest robustness label and model lab state.";
    }

    private String dominantLabel(List<RegimePrediction> predictions, boolean primary) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (RegimePrediction prediction : predictions) {
            String label = primary ? prediction.getRegimeLabel() : prediction.getBaselineRegimeLabel();
            if (label != null) {
                counts.merge(label, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .max(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private int anomalyOverlapCount(List<RegimePrediction> predictions) {
        return (int) predictions.stream()
                .filter(prediction -> Boolean.TRUE.equals(prediction.getDisagreesWithBaseline()))
                .filter(prediction -> prediction.getAnomalyLabel() != null && !"NORMAL".equalsIgnoreCase(prediction.getAnomalyLabel()))
                .count();
    }

    private List<String> safeWarnings(MlMarketRegimeStatusResponse status) {
        return Objects.requireNonNullElse(status.warnings(), List.of());
    }
}
