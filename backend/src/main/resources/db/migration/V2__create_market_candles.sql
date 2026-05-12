create table market_candles (
    id bigserial primary key,
    symbol varchar(64) not null,
    timeframe varchar(32) not null,
    open_time timestamptz not null,
    open numeric(20, 8) not null,
    high numeric(20, 8) not null,
    low numeric(20, 8) not null,
    close numeric(20, 8) not null,
    volume numeric(30, 8) not null,
    constraint market_candles_symbol_timeframe_open_time_key unique (symbol, timeframe, open_time)
);

create index idx_market_candles_symbol_timeframe_open_time
    on market_candles (symbol, timeframe, open_time);
