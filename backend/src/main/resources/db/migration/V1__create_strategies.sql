create table strategies (
    id bigserial primary key,
    name varchar(255) not null,
    symbol varchar(64) not null,
    timeframe varchar(32) not null,
    strategy_type varchar(64) not null,
    rules_json text not null,
    status varchar(32) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint strategies_strategy_type_check check (strategy_type in ('SMA_CROSSOVER')),
    constraint strategies_status_check check (status in ('ACTIVE', 'DISABLED'))
);
