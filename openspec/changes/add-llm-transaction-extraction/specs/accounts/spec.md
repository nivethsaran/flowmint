# Spec Delta

## Purpose

Discovers the user's bank accounts, credit cards, and wallets from transaction messages so transactions can be attributed to an account and stated bank balances can be tracked.

## ADDED Requirements

### Requirement: Account discovery
When a transaction is extracted with a known account type (`BANK_ACCOUNT`, `CREDIT_CARD`, or `WALLET`) and last four digits, the system SHALL link it to the account with that type and last four digits, creating the account if it does not exist. A new account MUST record the institution if known and get a default display name of the form "<institution> ••<last4>" (or "Bank account ••<last4>", "Credit card ••<last4>", "Wallet ••<last4>" when the institution is unknown). Transactions without both values MUST be saved without an account.

#### Scenario: First transaction on a new card
- **WHEN** the first transaction for HDFC Bank credit card XX5678 is extracted
- **THEN** a `CREDIT_CARD` account with last four `5678` and display name "HDFC Bank ••5678" is created and linked

#### Scenario: Subsequent transaction on the same account
- **WHEN** another transaction for bank account XX1234 is extracted
- **THEN** it is linked to the existing account and no new account is created

#### Scenario: No account digits
- **WHEN** a transaction message does not show account digits
- **THEN** the transaction is saved without an account

### Requirement: Stated balance tracking
When a validated transaction on a bank account or wallet states an available balance, the system SHALL store it as the account's last known balance with the event time, unless the account already has a balance from a later event.

#### Scenario: Balance in a debit SMS
- **WHEN** a debit message for A/c XX1234 ends with "Avl Bal INR 12,000.00"
- **THEN** account ••1234's last known balance becomes 12000.00 as of the event time

#### Scenario: Out-of-order message
- **WHEN** an older message with a balance is processed after a newer one
- **THEN** the account keeps the newer balance
