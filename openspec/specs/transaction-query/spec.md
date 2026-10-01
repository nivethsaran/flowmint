# transaction-query Specification

## Purpose
Lets the authenticated operator read extracted transactions, either the most recent ones or those within a date range.

## Requirements

### Requirement: List transactions
The backend SHALL expose `GET /api/v1/transactions` to authenticated callers, returning a JSON array of transactions with `id`, `merchant`, `amount`, `category`, `date`, and `requiresReview`, ordered by transaction date with the newest first.

#### Scenario: Unauthenticated request
- **WHEN** a caller without a session or device token requests transactions
- **THEN** the response is `401 UNAUTHORIZED`

### Requirement: Default to most recent transactions
When the `from` and `to` query parameters are not both provided, the endpoint SHALL return at most the 20 most recent transactions.

#### Scenario: No date range
- **WHEN** the operator requests `/api/v1/transactions` with no parameters
- **THEN** up to 20 transactions are returned, newest first

#### Scenario: Only one bound supplied
- **WHEN** only `from` is supplied
- **THEN** the parameter is ignored and up to 20 most recent transactions are returned

### Requirement: Filter by date range
When both `from` and `to` are provided as ISO dates (`YYYY-MM-DD`), the endpoint SHALL return all transactions whose transaction date falls within that range, inclusive of both bounds, with no count limit.

#### Scenario: Date range query
- **WHEN** the operator requests `?from=2026-09-01&to=2026-09-30`
- **THEN** every transaction dated 1–30 September 2026 is returned, newest first

### Requirement: Browser proxy for transactions
The frontend SHALL expose `GET /api/transactions`, forwarding the browser's cookies to the backend's transaction list without caching and returning the backend's status and body unchanged. The proxy does not forward query parameters, so the browser MUST only receive the default most-recent list.

#### Scenario: Dashboard loads transactions
- **WHEN** the dashboard requests `/api/transactions` with a valid session cookie
- **THEN** the backend's most recent transactions are returned

#### Scenario: Expired session cookie
- **WHEN** `/api/transactions` is requested with a `JSESSIONID` cookie for a session the backend no longer recognizes
- **THEN** the backend's `401 UNAUTHORIZED` response is returned unchanged
