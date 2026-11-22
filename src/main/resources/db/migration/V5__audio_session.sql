alter table audio_upload add column session_id varchar(255);
create unique index uq_audio_upload_session_id on audio_upload(session_id) where session_id is not null;
