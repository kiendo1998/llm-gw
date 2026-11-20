create table llm_task (
    id uuid primary key,
    client_request_id varchar(255) unique,
    status varchar(32) not null,
    input_json jsonb not null,
    output_json jsonb,
    error_message text,
    slot_id integer,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0
);

create index idx_llm_task_status_created on llm_task(status, created_at);

create table llm_slot (
    id integer primary key,
    task_id uuid unique references llm_task(id),
    acquired_at timestamptz
);

insert into llm_slot(id) values (1), (2), (3), (4), (5);

create table outbox_event (
    id uuid primary key,
    topic varchar(255) not null,
    event_key varchar(255) not null,
    payload jsonb not null,
    created_at timestamptz not null,
    published_at timestamptz
);

create index idx_outbox_unpublished on outbox_event(created_at) where published_at is null;
