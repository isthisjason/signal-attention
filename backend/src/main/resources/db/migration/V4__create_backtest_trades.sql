create table backtest_trades (
    id bigserial primary key,
    backtest_run_id bigint not null references backtest_runs (id) on delete cascade,
    side varchar(32) not null,
    entry_time timestamptz not null,
    entry_price numeric(20, 8) not null,
    exit_time timestamptz,
    exit_price numeric(20, 8),
    quantity numeric(20, 8) not null,
    gross_pnl numeric(20, 8),
    fees numeric(20, 8),
    net_pnl numeric(20, 8),
    return_percent numeric(12, 6),
    constraint backtest_trades_side_check check (side in ('LONG'))
);

create index idx_backtest_trades_backtest_run_id_entry_time
    on backtest_trades (backtest_run_id, entry_time);
