create table paper_sessions (
    id bigserial primary key,
    strategy_id bigint not null,
    status varchar(32) not null,
    initial_balance numeric(20, 8) not null,
    cash_balance numeric(20, 8) not null,
    created_at timestamptz not null,
    started_at timestamptz,
    stopped_at timestamptz,
    constraint paper_sessions_strategy_fk foreign key (strategy_id) references strategies (id) on delete cascade,
    constraint paper_sessions_initial_balance_check check (initial_balance > 0),
    constraint paper_sessions_cash_balance_check check (cash_balance >= 0)
);

create index paper_sessions_strategy_idx on paper_sessions (strategy_id);

create table paper_orders (
    id bigserial primary key,
    paper_session_id bigint not null,
    side varchar(16) not null,
    status varchar(16) not null,
    symbol varchar(64) not null,
    quantity numeric(20, 8) not null,
    price numeric(20, 8) not null,
    notional numeric(20, 8) not null,
    rejection_reason text,
    created_at timestamptz not null,
    constraint paper_orders_session_fk foreign key (paper_session_id) references paper_sessions (id) on delete cascade,
    constraint paper_orders_quantity_check check (quantity > 0),
    constraint paper_orders_price_check check (price > 0),
    constraint paper_orders_notional_check check (notional > 0)
);

create index paper_orders_session_created_idx on paper_orders (paper_session_id, created_at);

create table paper_positions (
    id bigserial primary key,
    paper_session_id bigint not null,
    status varchar(16) not null,
    symbol varchar(64) not null,
    quantity numeric(20, 8) not null,
    entry_price numeric(20, 8) not null,
    exit_price numeric(20, 8),
    opened_at timestamptz not null,
    closed_at timestamptz,
    constraint paper_positions_session_fk foreign key (paper_session_id) references paper_sessions (id) on delete cascade,
    constraint paper_positions_quantity_check check (quantity > 0),
    constraint paper_positions_entry_price_check check (entry_price > 0),
    constraint paper_positions_exit_price_check check (exit_price is null or exit_price > 0)
);

create index paper_positions_session_status_idx on paper_positions (paper_session_id, status);
