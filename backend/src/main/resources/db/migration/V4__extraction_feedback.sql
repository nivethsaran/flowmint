alter table raw_events
    add column user_feedback varchar(30),
    add column feedback_at timestamptz;

create index idx_raw_events_feedback on raw_events(feedback_at desc) where user_feedback is not null;
