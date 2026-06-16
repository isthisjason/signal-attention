package com.signalattention.marketregime;

import com.signalattention.ml.MlMarketRegimeDiagnosticsResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "regime_evidence_snapshots")
public class RegimeEvidenceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String timeframe;

    @Column(nullable = false)
    private Instant windowStart;

    @Column(nullable = false)
    private Instant windowEnd;

    @Column(nullable = false)
    private String regimeLabel;

    @Column(nullable = false, precision = 12, scale = 6)
    private BigDecimal confidence;

    @Column(nullable = false)
    private String baselineRegimeLabel;

    @Column(nullable = false, precision = 12, scale = 6)
    private BigDecimal baselineConfidence;

    @Column(nullable = false)
    private Boolean disagreesWithBaseline;

    @Column(nullable = false)
    private String evidenceSource;

    private String classifierSource;
    private String modelVersion;
    private String featureVersion;
    private String artifactIdentifier;

    @Column(columnDefinition = "TEXT")
    private String reasonsJson;

    @Column(columnDefinition = "TEXT")
    private String topTimestepsJson;

    @Column(columnDefinition = "TEXT")
    private String featureEvidenceJson;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected RegimeEvidenceSnapshot() {
    }

    public RegimeEvidenceSnapshot(
            MlMarketRegimeDiagnosticsResponse response,
            String reasonsJson,
            String topTimestepsJson,
            String featureEvidenceJson
    ) {
        this.symbol = response.symbol();
        this.timeframe = response.timeframe();
        this.windowStart = response.windowStart();
        this.windowEnd = response.windowEnd();
        this.regimeLabel = response.regimeLabel();
        this.confidence = response.confidence();
        this.baselineRegimeLabel = response.baselineRegimeLabel();
        this.baselineConfidence = response.baselineConfidence();
        this.disagreesWithBaseline = response.disagreesWithBaseline();
        this.evidenceSource = response.evidenceSource();
        this.classifierSource = response.classifierSource();
        this.modelVersion = response.modelVersion();
        this.featureVersion = response.featureVersion();
        this.artifactIdentifier = response.artifactIdentifier();
        this.reasonsJson = reasonsJson;
        this.topTimestepsJson = topTimestepsJson;
        this.featureEvidenceJson = featureEvidenceJson;
    }

    public Long getId() { return id; }
    public String getSymbol() { return symbol; }
    public String getTimeframe() { return timeframe; }
    public Instant getWindowStart() { return windowStart; }
    public Instant getWindowEnd() { return windowEnd; }
    public String getRegimeLabel() { return regimeLabel; }
    public BigDecimal getConfidence() { return confidence; }
    public String getBaselineRegimeLabel() { return baselineRegimeLabel; }
    public BigDecimal getBaselineConfidence() { return baselineConfidence; }
    public Boolean getDisagreesWithBaseline() { return disagreesWithBaseline; }
    public String getEvidenceSource() { return evidenceSource; }
    public String getClassifierSource() { return classifierSource; }
    public String getModelVersion() { return modelVersion; }
    public String getFeatureVersion() { return featureVersion; }
    public String getArtifactIdentifier() { return artifactIdentifier; }
    public String getReasonsJson() { return reasonsJson; }
    public String getTopTimestepsJson() { return topTimestepsJson; }
    public String getFeatureEvidenceJson() { return featureEvidenceJson; }
    public Instant getCreatedAt() { return createdAt; }
}
