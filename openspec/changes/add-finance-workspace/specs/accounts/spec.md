# Spec Delta

## ADDED Requirements

### Requirement: Accounts API
The backend SHALL expose `GET /api/v1/accounts` returning each account's `id`, `type`, `institution`, `last4`, `displayName`, `lastKnownBalance`, `balanceAsOf`, `transactionCount`, `lastTransactionDate`, and, for the current month and excluding duplicates, `monthDebits` and `monthCredits`. It SHALL expose `PATCH /api/v1/accounts/{id}` with `{"displayName": "..."}` (1–80 characters) to rename an account, returning `404` for an unknown id.

#### Scenario: Rename a card
- **WHEN** the operator renames "HDFC Bank ••5678" to "Regalia card"
- **THEN** the account and every transaction linked to it show "Regalia card"

#### Scenario: Blank name
- **WHEN** the operator patches an empty display name
- **THEN** the response is `400 VALIDATION_ERROR`

### Requirement: Accounts page
The web UI SHALL provide `/accounts` that groups accounts into bank accounts, credit cards, and wallets. It SHALL show:
- the total of stated bank and wallet balances
- per account: display name, institution, masked number, last known balance with its as-of time (bank accounts and wallets), this month's money out and in, transaction count, and last activity
- inline renaming and a link to that account's transactions

#### Scenario: View account transactions
- **WHEN** the operator clicks "View transactions" on an account
- **THEN** `/transactions?accountId=<id>` opens filtered to that account

#### Scenario: No accounts yet
- **WHEN** no account has been discovered
- **THEN** the page explains that accounts appear automatically from bank and card messages
