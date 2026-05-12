create table audit_events (
    id bigserial primary key,
    entity_type varchar(128) not null,
    entity_id varchar(128),
    action varchar(128) not null,
    message varchar(1000) not null,
    metadata_json text,
    created_at timestamptz not null
);

create index idx_audit_events_entity
    on audit_events (entity_type, entity_id);

create index idx_audit_events_created_at
    on audit_events (created_at);
