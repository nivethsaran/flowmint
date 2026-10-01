# Tasks

## 1. Backend foundations

- [x] 1.1 Add `common` package: `ApiExceptionHandler`, `ApiException`/`NotFoundException`/`ConflictException`, `PageResponse`; permit `/error` in `SecurityConfig`; verify a malformed TOTP login returns `400 VALIDATION_ERROR` and an unknown id returns `404 NOT_FOUND`
- [x] 1.2 Write `V3__finance_workspace.sql` (budgets, merchant_rules, recurring_dismissals, nullable external id, indexes); verify Flyway applies it and Hibernate validation passes
- [x] 1.3 Add `MerchantKey` normalization and `app.timezone` clock bean; verify with unit tests

## 2. Transactions

- [x] 2.1 Implement `TransactionSpecifications` and the paginated, filtered, sorted `GET /api/v1/transactions` with parameter validation; verify search by merchant, amount, category label, review filter, and `400` on bad size/type
- [x] 2.2 Implement `GET /api/v1/transactions/{id}` with source message detail; verify via curl
- [x] 2.3 Implement `PATCH` (field edits, direction from type, reviewed, user-edited) and merchant rules (`applyToMerchant`, applied in extraction writer); verify with a unit test of rule application and a curl edit
- [x] 2.4 Implement manual `POST`, `DELETE` (clears duplicate references), and `not-duplicate`; verify via curl

## 3. Analytics, budgets, recurring, accounts, inbox APIs

- [x] 3.1 Implement `Totals` counting rules and `AnalyticsService` (totals, previous period, bucketed series, categories, merchants, accounts, review count) with `GET /api/v1/analytics/summary`; verify with unit tests for transfers, refunds, duplicates, and bucket granularity
- [x] 3.2 Implement budgets entity/service/controller (upsert, delete, month status with projection and unbudgeted); verify with unit tests for status thresholds and projection
- [x] 3.3 Implement `RecurringDetector`, dismissals, and `GET/POST/DELETE /api/v1/recurring`; verify with unit tests for monthly subscription, irregular merchant, autopay pair, lapsed status
- [x] 3.4 Implement `GET /api/v1/accounts` with month activity and `PATCH` rename; verify via curl
- [x] 3.5 Implement `GET /api/v1/events` (filters, paging), `/events/{id}`, `/events/stats`; verify via curl

## 4. Frontend platform

- [x] 4.1 Add generic proxy `app/api/v1/[...path]/route.ts`, delete `app/api/transactions`; verify GET and PATCH pass through with cookies and CSRF
- [x] 4.2 Add `lib/api.ts`, `lib/useApi.ts`, `lib/format.ts`, `lib/ranges.ts`, `lib/categories.ts`, `lib/types.ts`; verify `tsc --noEmit` passes
- [x] 4.3 Rewrite global styles (tokens, layout container, panels, forms, buttons, badges, tables, responsive rules) and fix the content alignment; verify at 2560 px, 1440 px, and 390 px widths
- [x] 4.4 Build the app shell: route group layout, sidebar with active route and inbox indicator, mobile bottom nav with "More" sheet, header with greeting/date/user menu/sign out; verify navigation across all routes

## 5. Shared UI components

- [x] 5.1 Build chart components (`Sparkline`, `TrendChart`, `Donut`, `RankedBars`, `ProgressBar`) per the dataviz guidance; verify they render with empty, single-point, and typical data
- [x] 5.2 Build `Modal`, `Drawer`, `EmptyState`, `ErrorBanner`, `Skeleton`, `Badge`, `SignedAmount`, `TransactionRow`; verify in pages
- [x] 5.3 Build the transaction drawer (detail, source message, edit form, reviewed, apply-to-merchant, not-duplicate, delete) and the add-transaction form; verify edits persist and the opener refreshes
- [x] 5.4 Build the command palette (⌘K/Ctrl+K, debounced search, navigation commands, keyboard control, "See all results"); verify find-and-open and jump-to-page

## 6. Pages

- [x] 6.1 Overview: range selector in URL, metric cards with deltas and sparkline, spending donut, budget highlights, upcoming recurring, review queue, recent activity; verify against the analytics API numbers
- [x] 6.2 Transactions page with URL-synced filters, pagination, drawer, add form; verify deep links `?review=true`, `?q=`, `?accountId=`
- [x] 6.3 Analytics page with presets, metric cards, trend chart, category bars, merchants, accounts; verify range switching changes granularity
- [x] 6.4 Budgets page with month switcher, overall card, category rows, add/edit/delete, unbudgeted suggestions; verify full CRUD
- [x] 6.5 Recurring page with summary, list, relative dates, dismiss/restore, show dismissed; verify dismissal updates the total
- [x] 6.6 Accounts page grouped by type with balances, month activity, rename, and transaction links; verify rename shows on transactions
- [x] 6.7 Inbox page with status tabs and counts, search, expandable rows, reprocess, bulk actions, auto-refresh while pending; verify reprocess flow and 409 message

## 7. Verification

- [x] 7.1 `npm run build` and backend `mvn package` (with unit tests) succeed in Docker
- [ ] 7.2 End-to-end against a temporary local Postgres with the real LLM: ingest a realistic set of messages, then verify every page shows consistent numbers and every control works
- [x] 7.3 Update README (features, LLM config, reprocessing) and verify instructions match the running stack
