-- LLM-based extraction: message classification, retry scheduling, accounts, richer transactions.

alter table raw_events
    add column message_kind varchar(30),
    add column classification_reason varchar(300),
    add column next_attempt_at timestamptz,
    add column locked_until timestamptz;
create index idx_raw_events_due on raw_events(processing_status, next_attempt_at);

create table accounts (
    id uuid primary key,
    type varchar(20) not null,
    institution varchar(120),
    last4 varchar(4) not null,
    display_name varchar(80) not null,
    last_known_balance numeric(19,4),
    balance_as_of timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uq_accounts_type_last4 unique (type, last4)
);

alter table transactions
    add column direction varchar(10),
    add column channel varchar(20),
    add column account_id uuid references accounts(id),
    add column occurred_at timestamptz,
    add column extraction_method varchar(10),
    add column user_edited boolean not null default false,
    add column notes varchar(1000),
    add column review_reasons varchar(200);

-- Fold retired types into the new set.
update transactions set type = 'EXPENSE' where type in ('BILL_PAYMENT', 'EMI', 'FEE');
update transactions set type = 'INCOME' where type = 'INTEREST';

update transactions set direction = case when type in ('INCOME', 'REFUND') then 'CREDIT' else 'DEBIT' end;

-- Categories are stored as stable keys from now on.
update transactions set category = case category
    when 'Food' then 'FOOD_DINING'
    when 'Shopping' then 'SHOPPING'
    when 'Transport' then 'TRANSPORT'
    when 'Subscriptions' then 'SUBSCRIPTIONS'
    when 'Bills & Utilities' then 'BILLS_UTILITIES'
    else 'OTHER'
end;

update transactions t set occurred_at = coalesce(
    (select r.event_timestamp from raw_events r where r.external_event_id = t.external_event_id),
    t.transaction_date::timestamp at time zone 'UTC');

update transactions set review_reasons = 'RULES_FALLBACK' where requires_review;

update transactions set
    channel = 'OTHER',
    extraction_method = 'RULES',
    merchant_normalized = lower(regexp_replace(coalesce(merchant, ''), '[^A-Za-z0-9]', '', 'g'));

update raw_events set message_kind = 'TRANSACTION'
where processing_status = 'PROCESSED' and external_event_id in (select external_event_id from transactions);

alter table transactions
    alter column direction set not null,
    alter column channel set not null,
    alter column occurred_at set not null,
    alter column extraction_method set not null,
    alter column category set not null;

create index idx_transactions_occurred on transactions(occurred_at);
