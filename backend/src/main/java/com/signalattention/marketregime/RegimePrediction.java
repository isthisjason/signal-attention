package com.signalattention.marketregime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "regime_predictions")
public class RegimePrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "regime_run_id", nullable = false)
    private RegimeRun regimeRun;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    @Column(name = "regime_label", nullable = false)
    private String regimeLabel;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal confidence;

    @Column(name = "reasons_json", columnDefinition = "text")
    private String reasonsJson;

    @Column(name = "features_json", columnDefinition = "text")
    private String featuresJson;

    @Column(name = "anomaly_score", precision = 10, scale = 4)
    private BigDecimal anomalyScore;

    @Column(name = "anomaly_label")
    private String anomalyLabel;

    @Column(name = "anomaly_reasons_json", columnDefinition = "text")
    private String anomalyReasonsJson;

    @Column(name = "baseline_regime_label")
    private String baselineRegimeLabel;

    @Column(name = "baseline_confidence", precision = 10, scale = 4)
    private BigDecimal baselineConfidence;

    @Column(name = "disagrees_with_baseline")
    private Boolean disagreesWithBaseline;

    protected RegimePrediction() {
    }

    public RegimePrediction(RegimeRun regimeRun, Instant windowStart, Instant windowEnd, String regimeLabel,
                            BigDecimal confidence, String reasonsJson, String featuresJson,
                            BigDecimal anomalyScore, String anomalyLabel, String anomalyReasonsJson,
                            String baselineRegimeLabel, BigDecimal baselineConfidence, Boolean disagreesWithBaseline) {
        this.regimeRun = regimeRun;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.regimeLabel = regimeLabel;
        this.confidence = confidence;
        this.reasonsJson = reasonsJson;
        this.featuresJson = featuresJson;
        this.anomalyScore = anomalyScore;
        this.anomalyLabel = anomalyLabel;
        this.anomalyReasonsJson = anomalyReasonsJson;
        this.baselineRegimeLabel = baselineRegimeLabel;
        this.baselineConfidence = baselineConfidence;
        this.disagreesWithBaseline = disagreesWithBaseline;
    }

    public Long getId() {
        return id;
    }

    public Instant getWindowStart() {
        return windowStart;
    }

    public Instant getWindowEnd() {
        return windowEnd;
    }

    public String getRegimeLabel() {
        return regimeLabel;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public String getReasonsJson() {
        return reasonsJson;
    }

    public String getFeaturesJson() {
        return featuresJson;
    }

    public BigDecimal getAnomalyScore() {
        return anomalyScore;
    }

    public String getAnomalyLabel() {
        return anomalyLabel;
    }

    public String getAnomalyReasonsJson() {
        return anomalyReasonsJson;
    }

    public String getBaselineRegimeLabel() {
        return baselineRegimeLabel;
    }

    public BigDecimal getBaselineConfidence() {
        return baselineConfidence;
    }

    public Boolean getDisagreesWithBaseline() {
        return disagreesWithBaseline;
    }
}
