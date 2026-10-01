-- Finance workspace: budgets, merchant category rules, recurring dismissals, manual transactions.

create table budgets (
    category varchar(40) primary key,
    monthly_limit numeric(19,4) not null check (monthly_limit > 0),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table merchant_rules (
    merchant_key varchar(200) primary key,
    category varchar(40) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table recurring_dismissals (
    merchant_key varchar(200) primary key,
    dismissed_at timestamptz not null
);

-- Manual transactions have no source message; the unique constraint still permits many nulls.
alter table transactions alter column external_event_id drop not null;

create index idx_transactions_account on transactions(account_id);
create index idx_transactions_review on transactions(requires_review) where requires_review;
create index idx_raw_events_received on raw_events(received_at desc);
