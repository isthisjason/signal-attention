CREATE TABLE regime_evidence_snapshots (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL,
    timeframe VARCHAR(16) NOT NULL,
    window_start TIMESTAMPTZ NOT NULL,
    window_end TIMESTAMPTZ NOT NULL,
    regime_label VARCHAR(64) NOT NULL,
    confidence NUMERIC(12, 6) NOT NULL,
    baseline_regime_label VARCHAR(64) NOT NULL,
    baseline_confidence NUMERIC(12, 6) NOT NULL,
    disagrees_with_baseline BOOLEAN NOT NULL,
    evidence_source VARCHAR(64) NOT NULL,
    classifier_source VARCHAR(64),
    model_version VARCHAR(128),
    feature_version VARCHAR(128),
    artifact_identifier VARCHAR(256),
    reasons_json TEXT,
    top_timesteps_json TEXT,
    feature_evidence_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_regime_evidence_symbol_timeframe_created
    ON regime_evidence_snapshots (symbol, timeframe, created_at DESC);
