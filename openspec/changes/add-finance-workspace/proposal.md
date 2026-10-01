# Proposal

## Why

Apart from the overview, the web UI is a shell. The search box does nothing. Analytics, Budgets, Recurring, and Accounts are anchor links to placeholders. Income, net cash flow, and savings rate show "—". The range selector changes nothing, and the layout leaves a dead gutter on wide screens. With richer transaction data now coming from LLM extraction (types, directions, accounts, categories), Flowmint can become a working personal finance app: find, correct, plan, and understand money end to end.

## What Changes

- **Transactions workspace**: paginated, searchable, filterable list (text, type, category, account, date range, needs review); a detail drawer showing the original message; editing (merchant, amount, type, category, account, date, notes), marking reviewed, "always categorize this merchant as…" rules, manual transactions (e.g. cash), deleting, and unmarking duplicates.
- **Analytics**: income, spending, net cash flow, savings rate, and investments for any range, compared with the previous period; income-vs-spending trend; category breakdown; top merchants; spending by account. Transfers (including card bill payments) and duplicates are excluded, so nothing is double-counted.
- **Budgets**: monthly limits per category plus an optional overall limit, with spent, remaining, pace, and projected month-end spend, and unbudgeted categories that have spending.
- **Recurring**: detection of subscriptions, EMIs, SIPs, and other repeating payments (weekly/monthly/quarterly/yearly), with next expected date, monthly-equivalent cost, lapsed status, and dismiss/restore.
- **Accounts**: discovered bank accounts, credit cards, and wallets with last known balance, this month's money in and out, and renaming.
- **Inbox**: every received message with its classification (OTP, promotional, etc.), status, and reason, plus single and bulk reprocessing.
- **Global search** (⌘K / Ctrl+K command palette): searches transactions and jumps to pages.
- **Overview redesign**: real metrics for the selected range (7D/30D/3M/6M/1Y), budget status, upcoming recurring payments, review queue, recent activity with signed amounts, and a personalized header.
- **Layout fix**: centered fluid content area, consistent grid alignment, proper mobile navigation, and real routes instead of anchors.
- **API plumbing**: a single generic `/api/v1/*` proxy in Next.js replaces per-endpoint proxies, and consistent JSON error bodies (including `400 VALIDATION_ERROR`) replace the protected error page.
- **BREAKING**: `GET /api/v1/transactions` returns a paginated object instead of an array, and the browser path moves from `/api/transactions` to `/api/v1/transactions`. A malformed login TOTP now returns `400` instead of `401`.

## Capabilities

### New Capabilities
- `transaction-management`: viewing a transaction's details and source message, editing, reviewing, merchant category rules, manual entry, deletion, and duplicate handling.
- `spending-analytics`: definitions and API for income, spending, net cash flow, savings rate, trends, and breakdowns, plus the analytics page.
- `budgets`: monthly category and overall budgets with progress and projections, plus the budgets page.
- `recurring-payments`: detection, cadence, next expected date, dismissal, plus the recurring page.
- `message-inbox`: listing and inspecting received messages with classification and status, reprocessing, plus the inbox page.

### Modified Capabilities
- `transaction-query`: pagination, text search, filters, and sorting; the browser accesses it through the generic API proxy.
- `finance-dashboard`: the overview uses real range-based analytics, budgets, and recurring data; adds navigation, global search, and a personalized header; the range selector becomes functional.
- `accounts`: adds the accounts API and page (listing with activity and balances, renaming).
- `api-access-control`: a generic `/api/v1/*` proxy and consistent JSON error responses.
- `web-authentication`: a malformed TOTP returns `400 VALIDATION_ERROR`.

## Impact

- Backend: new packages `analytics`, `budgets`, `recurring`, `rules`, and `common` (errors, paging, meta); expanded `transactions`, `accounts`, and `events` controllers; Flyway `V3` (budgets, merchant rules, recurring dismissals, nullable external id for manual transactions, search indexes).
- Frontend: route group `(app)` with pages `/`, `/transactions`, `/analytics`, `/budgets`, `/recurring`, `/accounts`, `/inbox`; a shared shell, command palette, chart components (SVG, no new dependencies), an API client with CSRF handling; rewritten styles. `app/api/transactions` is removed and replaced by `app/api/v1/[...path]`.
- Depends on `add-llm-transaction-extraction` (types, directions, accounts, categories, reprocess endpoints).
