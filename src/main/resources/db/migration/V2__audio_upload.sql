create table audio_upload (
    id uuid primary key,
    original_filename varchar(512) not null,
    content_type varchar(255) not null,
    status varchar(32) not null,
    total_bytes bigint not null default 0,
    chunk_count integer not null default 0,
    last_sequence integer,
    storage_path text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    completed_at timestamptz,
    version bigint not null default 0
);

create table audio_chunk (
    id uuid primary key,
    upload_id uuid not null references audio_upload(id) on delete cascade,
    sequence_number integer not null,
    size_bytes bigint not null,
    sha256 varchar(64) not null,
    storage_path text not null,
    created_at timestamptz not null,
    unique(upload_id, sequence_number)
);

create index idx_audio_chunk_upload_sequence on audio_chunk(upload_id, sequence_number);
