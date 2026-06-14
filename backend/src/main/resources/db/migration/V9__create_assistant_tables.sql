create table assistant_sessions (
    id bigserial primary key,
    title varchar(160) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table assistant_messages (
    id bigserial primary key,
    session_id bigint not null,
    role varchar(32) not null,
    content text not null,
    created_at timestamptz not null,
    constraint assistant_messages_session_fk foreign key (session_id) references assistant_sessions (id) on delete cascade
);

create index assistant_messages_session_created_idx on assistant_messages (session_id, created_at);

create table assistant_actions (
    id bigserial primary key,
    session_id bigint not null,
    message_id bigint,
    action_type varchar(64) not null,
    status varchar(32) not null,
    summary text not null,
    payload_json text not null,
    execution_result_json text,
    failure_message text,
    created_at timestamptz not null,
    confirmed_at timestamptz,
    rejected_at timestamptz,
    executed_at timestamptz,
    constraint assistant_actions_session_fk foreign key (session_id) references assistant_sessions (id) on delete cascade,
    constraint assistant_actions_message_fk foreign key (message_id) references assistant_messages (id) on delete set null
);

create index assistant_actions_session_created_idx on assistant_actions (session_id, created_at);
