package com.signalattention.marketregime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "regime_runs")
public class RegimeRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String timeframe;

    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    @Column(name = "end_date", nullable = false)
    private Instant endDate;

    @Column(name = "window_size", nullable = false)
    private Integer windowSize;

    @Column(nullable = false)
    private Integer stride;

    @Column(name = "include_anomalies", nullable = false)
    private Boolean includeAnomalies;

    @Column(name = "requested_mode")
    private String requestedMode;

    @Column(name = "effective_mode")
    private String effectiveMode;

    @Column(name = "classifier_source")
    private String classifierSource;

    @Column(name = "model_version")
    private String modelVersion;

    @Column(name = "feature_version")
    private String featureVersion;

    @Column(name = "artifact_identifier")
    private String artifactIdentifier;

    @Column(name = "point_count", nullable = false)
    private Integer pointCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RegimeRunStatus status;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected RegimeRun() {
    }

    public RegimeRun(String symbol, String timeframe, Instant startDate, Instant endDate, Integer windowSize,
                     Integer stride, Boolean includeAnomalies) {
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.startDate = startDate;
        this.endDate = endDate;
        this.windowSize = windowSize;
        this.stride = stride;
        this.includeAnomalies = includeAnomalies;
        this.pointCount = 0;
        this.status = RegimeRunStatus.COMPLETED;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getTimeframe() {
        return timeframe;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public Integer getWindowSize() {
        return windowSize;
    }

    public Integer getStride() {
        return stride;
    }

    public Boolean getIncludeAnomalies() {
        return includeAnomalies;
    }

    public String getRequestedMode() {
        return requestedMode;
    }

    public String getEffectiveMode() {
        return effectiveMode;
    }

    public String getClassifierSource() {
        return classifierSource;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public String getFeatureVersion() {
        return featureVersion;
    }

    public String getArtifactIdentifier() {
        return artifactIdentifier;
    }

    public Integer getPointCount() {
        return pointCount;
    }

    public RegimeRunStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void completeFromStatus(com.signalattention.ml.MlMarketRegimeStatusResponse status, int pointCount) {
        this.requestedMode = status.mode();
        this.effectiveMode = status.effectiveMode();
        this.classifierSource = status.classifierSource();
        this.modelVersion = status.modelVersion();
        this.featureVersion = status.featureVersion();
        this.artifactIdentifier = status.artifactIdentifier();
        this.pointCount = pointCount;
        this.status = RegimeRunStatus.COMPLETED;
        this.completedAt = Instant.now();
    }
}
