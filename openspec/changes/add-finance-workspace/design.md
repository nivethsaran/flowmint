# Design

## Context

- Builds on `add-llm-transaction-extraction`: transactions have `type`, `direction`, `category` (enum key), `account_id`, `channel`, `occurred_at`, `extraction_method`, `user_edited`, and `notes`, plus the reprocess endpoints.
- Scale is a single person: hundreds of transactions per month, thousands per year.
- Frontend: Next.js 15 App Router and React 19 with no UI or chart libraries. CSP allows only `self` scripts and Google Fonts. Today there is a single page with anchor links, and a fixed sidebar with a left-pinned `max-width: 1320px` content column, which is the "misaligned" gutter.
- Errors: Bean Validation failures currently dispatch to `/error`, which Spring Security protects, so clients see `401`.

## Goals / Non-Goals

**Goals:**
- Every UI control works end to end; there are no placeholders.
- One definition of income, spending, and the other totals (spending-analytics spec), shared by the overview, analytics, and budgets.
- No new runtime dependencies on either side.

**Non-Goals:**
- Multi-user support, sharing, exports, notifications/alerts, multi-currency conversion.
- Server-side rendering of data (pages are client-rendered behind the session).

## Decisions

### Transaction search with JPA Specifications
`TransactionSpecifications` builds predicates for `q`, `type`, `category`, `accountId`, `direction`, `review`, `includeDuplicates`, `from`, and `to`, and the repository uses `JpaSpecificationExecutor` with `PageRequest`. For `q`:
- a `lower(merchant) like`, `lower(notes) like`, or `lower(account.displayName) like` match
- `category in (keys whose label contains q)`, resolved in Java from the `Category` enum
- `amount = q` when `q` parses as a number

The account is a left join. An index on `lower(merchant_normalized)` is not worth it at this scale.
- *Alternative*: Postgres full-text search. Overkill for a few thousand short rows.

### Analytics aggregated in Java over a projection
`AnalyticsService` loads `(date, type, direction, amount, category, merchant, accountId, requiresReview)` for `[previousFrom, to]` with duplicates excluded, then folds the rows into totals, buckets, categories, merchants, and accounts. A year with its comparison period is about 7k small rows. That's simpler and easier to test than a set of `GROUP BY` queries, and the counting rules live in one function (`Totals.accumulate`), which `BudgetService` reuses.
- *Alternative*: SQL aggregation per section. Faster at scale, but it would repeat the counting rules in several queries.

### Time zone
`app.timezone` (default `Asia/Kolkata`) determines "today" and "current month" on the server (budgets, recurring status). The browser computes range presets from its local date and always sends explicit `from`/`to`, so the analytics API has no notion of "now".

### Recurring detection on demand
`RecurringDetector` is a pure function over the last 400 days of candidate rows and `today`; the bands and thresholds are in the spec. No table stores series. Only dismissals persist (`recurring_dismissals(merchant_key pk, dismissed_at)`), so detection always reflects edits and new data.

### Shared merchant key
`MerchantKey.of(name)` lowercases and strips non-alphanumerics, and `merchant_normalized` stores that key. Merchant rules (`merchant_rules(merchant_key pk, category, updated_at)`) and recurring grouping both use it. The extraction writer applies a rule after validation, and when it does, a category-only review flag is not raised.

### Error handling
`ApiExceptionHandler` (`@RestControllerAdvice`) maps:
- `MethodArgumentNotValidException`, `ConstraintViolationException`, `MethodArgumentTypeMismatchException`, `HttpMessageNotReadableException`, and `ApiException(VALIDATION_ERROR)` → 400
- `NotFoundException` → 404
- `ConflictException` → 409 with its code
- anything else → 500 with a generic message, logged without request bodies

`/error` is permitted in `SecurityConfig` so container-level errors aren't masked as `401`.

### Generic Next.js proxy
`app/api/v1/[...path]/route.ts` exports GET/POST/PUT/PATCH/DELETE handlers that rebuild `${FLOWMINT_API_URL}/api/v1/<path>?<query>`, forward `cookie`, `content-type`, and `x-xsrf-token`, stream the body as text, and copy back status, content type, and all `Set-Cookie` values (`headers.getSetCookie()`). The old `app/api/transactions` route is deleted. The auth and device routes stay dedicated because they have special cookie and bearer handling.

### Frontend structure
- `app/(app)/layout.tsx` holds the client shell: sidebar, bottom nav, header, command palette, and the transaction drawer host. Pages live under `app/(app)/…`; `/login` stays outside the group.
- `lib/api.ts` is a typed `api.get/post/patch/put/delete`. For mutations it reads `XSRF-TOKEN` and fetches `/api/auth/csrf` first if the cookie is missing. On `401` it redirects to `/login`; other failures throw `ApiError(code, message, fields)`.
- `lib/useApi.ts` is a small data hook (`data`, `error`, `loading`, `reload`) keyed by URL, with abort on change. No SWR dependency.
- `lib/categories.ts` mirrors the backend `Category` enum (key, label, color, kind). The backend is the source of truth for validation; the mirror only supplies labels and colors.
- The transaction drawer is a context (`useTransactionDrawer().open(id)`), so the overview, search palette, and transactions page all open the same drawer.
- Charts are hand-written SVG components (`TrendChart`, `Donut`, `Sparkline`, `RankedBars`, `ProgressBar`) built to the dataviz skill's guidance. They are CSP-safe and have no dependencies.

### Layout fix
`.content` becomes `margin-left: var(--sidebar-w)` with an inner `.container { max-width: 1440px; margin-inline: auto; padding-inline: clamp(16px, 4vw, 48px) }`. Panels use CSS grid with `align-items: stretch` so cards in a row share a height. Below 900 px the sidebar becomes a fixed bottom bar with SVG icons and a "More" sheet; the content gets bottom padding for it.

### Data model (Flyway `V3__finance_workspace.sql`)
- `budgets(category varchar(40) pk, monthly_limit numeric(19,4) not null check > 0, created_at, updated_at)`
- `merchant_rules(merchant_key varchar(200) pk, category varchar(40) not null, created_at, updated_at)`
- `recurring_dismissals(merchant_key varchar(200) pk, dismissed_at timestamptz not null)`
- `transactions.external_event_id` becomes nullable (manual transactions; the unique constraint still allows many nulls)
- indexes `idx_transactions_account(account_id)`, `idx_transactions_review(requires_review) where requires_review`, `idx_raw_events_received(received_at desc)`

## Risks / Trade-offs

- [Java-side aggregation grows linearly with history] → bounded by the 1,100-day span limit, and the projection keeps rows small.
- [The category mirror in TS drifts from the Java enum] → unknown keys render with their raw key and the "Other" color, so nothing breaks; both lists sit next to the spec's category list.
- [Heuristic recurring detection misses or over-detects] → thresholds come from the spec and are unit-tested; dismissal handles false positives.
- [`BREAKING` list response shape] → only the in-repo dashboard consumes it, and it's updated in the same change.

## Migration Plan

Deploy after `add-llm-transaction-extraction`; Flyway applies `V3` (additive). Rollback means redeploying the previous images. The V3 tables are additive and can be dropped.
