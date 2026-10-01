create table raw_events (
    id uuid primary key,
    external_event_id varchar(120) not null,
    source varchar(30) not null,
    sender varchar(160),
    package_name varchar(200),
    title varchar(300),
    body text not null,
    event_timestamp timestamptz not null,
    device_id varchar(120) not null,
    received_at timestamptz not null,
    processing_status varchar(30) not null,
    processing_attempts integer not null default 0,
    last_processing_error varchar(1000),
    constraint uq_raw_events_external_id unique (external_event_id)
);
create index idx_raw_events_status on raw_events(processing_status);

create table transactions (
    id uuid primary key,
    external_event_id varchar(120) not null unique,
    type varchar(40) not null,
    amount numeric(19,4) not null,
    currency varchar(3) not null,
    merchant varchar(200),
    merchant_normalized varchar(200),
    category varchar(100),
    subcategory varchar(100),
    account_hint varchar(80),
    transaction_date date not null,
    description varchar(500),
    confidence numeric(5,4),
    requires_review boolean not null default false,
    source varchar(30) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    duplicate_of_transaction_id uuid references transactions(id)
);
create index idx_transactions_date on transactions(transaction_date);
create index idx_transactions_category on transactions(category);
create index idx_transactions_merchant on transactions(merchant_normalized);
