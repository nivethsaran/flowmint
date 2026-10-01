# Spec Delta

## MODIFIED Requirements

### Requirement: List transactions
The backend SHALL expose `GET /api/v1/transactions` to authenticated callers, returning a page object `{"items": [...], "page": n, "size": n, "totalItems": n, "totalPages": n}`. Each item MUST contain `id`, `date`, `merchant`, `amount`, `currency`, `type`, `direction`, `category`, `accountId`, `accountName`, `channel`, `requiresReview`, `duplicateOfId`, `extractionMethod`, and `notes`.

#### Scenario: Unauthenticated request
- **WHEN** a caller without a session or device token requests transactions
- **THEN** the response is `401 UNAUTHORIZED`

#### Scenario: Page shape
- **WHEN** 30 transactions exist and the operator requests `?page=1&size=25`
- **THEN** the response has 5 items, `page` 1, `size` 25, `totalItems` 30, and `totalPages` 2

### Requirement: Default to most recent transactions
When no filters are given, the endpoint SHALL return the first page (page `0`, size `25`) of all transactions, ordered by transaction date and then event time, newest first.

#### Scenario: No date range
- **WHEN** the operator requests `/api/v1/transactions` with no parameters
- **THEN** up to 25 transactions are returned, newest first

#### Scenario: Only one bound supplied
- **WHEN** only `from=2026-09-15` is supplied
- **THEN** transactions dated on or after 15 September 2026 are returned

### Requirement: Filter by date range
The `from` and `to` query parameters SHALL each be optional ISO dates (`YYYY-MM-DD`) and, when present, bound the transaction date inclusively. A `from` later than `to` MUST be rejected with `400 VALIDATION_ERROR`.

#### Scenario: Date range query
- **WHEN** the operator requests `?from=2026-09-01&to=2026-09-30`
- **THEN** only transactions dated 1–30 September 2026 are returned, newest first, paginated

#### Scenario: Inverted range
- **WHEN** the operator requests `?from=2026-09-30&to=2026-09-01`
- **THEN** the response is `400 VALIDATION_ERROR`

### Requirement: Browser proxy for transactions
The browser SHALL reach the transaction list at `/api/v1/transactions` through the generic API proxy, which forwards the browser's cookies and query string without caching and returns the backend's status and body unchanged.

#### Scenario: Dashboard loads transactions
- **WHEN** the dashboard requests `/api/v1/transactions?size=6` with a valid session cookie
- **THEN** the backend's six most recent transactions are returned

#### Scenario: Expired session cookie
- **WHEN** `/api/v1/transactions` is requested with a `JSESSIONID` cookie for a session the backend no longer recognizes
- **THEN** the backend's `401 UNAUTHORIZED` response is returned unchanged

## ADDED Requirements

### Requirement: Search and filters
The endpoint SHALL accept these optional filters, combined with AND:
- `q`: case-insensitive text matched against merchant, notes, account display name, and category label; when `q` is a number, transactions with exactly that amount also match
- `type`, `category`, `accountId`, `direction`
- `review=true`: only transactions requiring review
- `includeDuplicates`: defaults to `true`; `false` hides transactions marked as duplicates

Unknown enum values MUST be rejected with `400 VALIDATION_ERROR`.

#### Scenario: Search by merchant
- **WHEN** the operator requests `?q=swig`
- **THEN** transactions whose merchant contains "Swig" (any case) are returned

#### Scenario: Search by amount
- **WHEN** the operator requests `?q=1299`
- **THEN** transactions with amount 1299 are returned along with any text matches

#### Scenario: Needs-review filter
- **WHEN** the operator requests `?review=true`
- **THEN** only transactions with `requiresReview = true` are returned

### Requirement: Pagination limits and sorting
`page` SHALL be zero-based and `size` MUST be between 1 and 100 (default 25); out-of-range values MUST be rejected with `400 VALIDATION_ERROR`. `sort` SHALL accept `date_desc` (default), `date_asc`, `amount_desc`, and `amount_asc`.

#### Scenario: Oversized page
- **WHEN** the operator requests `?size=500`
- **THEN** the response is `400 VALIDATION_ERROR`

#### Scenario: Largest first
- **WHEN** the operator requests `?sort=amount_desc`
- **THEN** transactions are ordered by amount, largest first
