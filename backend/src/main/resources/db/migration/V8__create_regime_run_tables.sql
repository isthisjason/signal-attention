create table regime_runs (
    id bigserial primary key,
    symbol varchar(64) not null,
    timeframe varchar(32) not null,
    start_date timestamptz not null,
    end_date timestamptz not null,
    window_size integer not null,
    stride integer not null,
    include_anomalies boolean not null,
    requested_mode varchar(32),
    effective_mode varchar(32),
    classifier_source varchar(64),
    model_version varchar(128),
    feature_version varchar(128),
    artifact_identifier varchar(256),
    point_count integer not null,
    status varchar(32) not null,
    error_message text,
    created_at timestamptz not null,
    completed_at timestamptz,
    constraint regime_runs_window_size_check check (window_size >= 20),
    constraint regime_runs_stride_check check (stride >= 1),
    constraint regime_runs_point_count_check check (point_count >= 0)
);

create index regime_runs_market_created_idx on regime_runs (symbol, timeframe, created_at desc);

create table regime_predictions (
    id bigserial primary key,
    regime_run_id bigint not null,
    window_start timestamptz not null,
    window_end timestamptz not null,
    regime_label varchar(64) not null,
    confidence numeric(10, 4) not null,
    reasons_json text,
    features_json text,
    anomaly_score numeric(10, 4),
    anomaly_label varchar(64),
    anomaly_reasons_json text,
    baseline_regime_label varchar(64),
    baseline_confidence numeric(10, 4),
    disagrees_with_baseline boolean,
    constraint regime_predictions_run_fk foreign key (regime_run_id) references regime_runs (id) on delete cascade
);

create index regime_predictions_run_window_idx on regime_predictions (regime_run_id, window_start, window_end);
