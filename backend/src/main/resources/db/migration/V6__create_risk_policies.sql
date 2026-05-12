create table risk_policies (
    id bigserial primary key,
    strategy_id bigint not null,
    max_position_size_percent numeric(12, 6) not null,
    stop_loss_percent numeric(12, 6) not null,
    take_profit_percent numeric(12, 6) not null,
    max_daily_loss_percent numeric(12, 6) not null,
    cooldown_minutes integer not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint risk_policies_strategy_fk foreign key (strategy_id) references strategies (id) on delete cascade,
    constraint risk_policies_strategy_key unique (strategy_id),
    constraint risk_policies_max_position_size_check check (max_position_size_percent > 0 and max_position_size_percent <= 100),
    constraint risk_policies_stop_loss_check check (stop_loss_percent > 0),
    constraint risk_policies_take_profit_check check (take_profit_percent > 0),
    constraint risk_policies_max_daily_loss_check check (max_daily_loss_percent > 0),
    constraint risk_policies_cooldown_check check (cooldown_minutes >= 0)
);
