create table backtest_runs (
    id bigserial primary key,
    strategy_id bigint not null references strategies (id),
    start_date timestamptz not null,
    end_date timestamptz not null,
    initial_balance numeric(20, 8) not null,
    final_balance numeric(20, 8),
    total_return numeric(12, 6),
    max_drawdown numeric(12, 6),
    win_rate numeric(12, 6),
    profit_factor numeric(12, 6),
    trade_count integer,
    average_trade_return numeric(12, 6),
    fee_drag numeric(20, 8),
    volatility numeric(12, 6),
    ml_risk_score numeric(10, 4),
    ml_risk_label varchar(64),
    status varchar(32) not null,
    created_at timestamptz not null,
    completed_at timestamptz,
    constraint backtest_runs_status_check check (status in ('COMPLETED', 'FAILED'))
);

create index idx_backtest_runs_strategy_id
    on backtest_runs (strategy_id);
