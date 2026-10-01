# Flowmint

Flowmint is a local-first personal finance command center. It accepts sensitive Flowmint events, stores the raw event before processing, extracts a structured transaction asynchronously, and presents deterministic financial data in a mobile-friendly Next.js PWA surface.

## Run locally

```bash
cp .env.example .env
docker compose up --build
```

Open http://localhost:3000. The backend is private to the Docker network; its health endpoint is not published to the host.

### Flowmint Web UI authentication

Set `FLOWMINT_AUTH_USERNAME`, `FLOWMINT_AUTH_PASSWORD`, and a Base32 `FLOWMINT_TOTP_SECRET` in `.env`. The browser signs in at `/login` with all three values. Spring Boot creates the server-side session and the Next.js proxy forwards authenticated requests over the private Docker network. The session cookie is HttpOnly, SameSite=Lax, and expires after eight hours.

The login endpoint is browser-facing through Next.js at `POST /api/auth/login`; the Spring endpoint is internal at `POST /api/v1/auth/login`. Failed logins are rate-limited and return the same generic error regardless of which credential failed.

### Supabase PostgreSQL

Supabase is the only database used by Docker Compose. Copy the supplied host into `.env` as a JDBC URL, add `?sslmode=require`, set `DATABASE_USERNAME=postgres`, and replace `DATABASE_PASSWORD` with the database password from Supabase. The URL must start with `jdbc:postgresql://`, not `postgresql://`:

```dotenv
DATABASE_URL=jdbc:postgresql://db.lumivoszwxqdxbwsegho.supabase.co:5432/postgres?sslmode=require
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=your-real-password
```

Keep `.env` out of Git. Docker Compose refuses to start when the Supabase database variables are missing, preventing an accidental localhost fallback.

Send a device event:

```bash
curl -X POST http://localhost:3000/api/flowmint/events \
  -H 'Authorization: Bearer dev-device-token-change-me' \
  -H 'Content-Type: application/json' \
  -d '{"source":"sms","sender":"HDFC-BANK","body":"INR 1299.00 debited from A/c XX1234 at AMAZON on 01-10-2026. Avl Bal INR 45,000.00","timestamp":"2026-10-01T09:00:00Z","deviceId":"device-001"}'
```

The response is `202 Accepted`; the message is read in the background and appears in the web app within seconds.

### LLM extraction

Every message is classified and extracted by an LLM through any OpenAI-compatible chat completions endpoint that supports `response_format` JSON schema (for example a llama.cpp server). Set these in `.env`; Compose refuses to start without the first two:

| Variable | Purpose |
| --- | --- |
| `FLOWMINT_LLM_BASE_URL` | Endpoint base URL, ending in `/v1` |
| `FLOWMINT_LLM_API_KEY` | API key sent as a bearer token |
| `FLOWMINT_LLM_MODEL` | Model name; single-model servers ignore it (default `default`) |
| `FLOWMINT_TIMEZONE` | Time zone for "today", months, and date ranges (default `Asia/Kolkata`) |

Messages such as OTPs, promotions, failed payments, payment requests, and bill reminders are kept but marked **Ignored**. Low-confidence or incomplete extractions are saved and flagged **Needs review**. If the LLM is unreachable, a message is retried after 1, 5, 15, and 60 minutes. After the last attempt, simple rule-based parsing records it so nothing is lost; messages that can't be parsed are marked **Failed**.

### Reprocessing

From **Inbox** in the web app:

- **Reprocess** on a message reads it again. If you edited the transaction it produced, your edit is kept and the message is not re-read.
- **Re-extract rule-based** re-reads every transaction that came from the fallback rules, once the LLM is available again.
- **Retry failed** re-queues messages that could not be read.

The same actions are available as `POST /api/v1/events/{id}/reprocess` and `POST /api/v1/events/reprocess?scope=RULES|FAILED`.

## Using the web app

- **Overview**: net cash flow, income, spending, and savings rate for a chosen range, compared with the previous period; spending by category, the review queue, budgets, and payments due in the next 14 days.
- **Transactions**: search by merchant, note, account, category, or amount, and filter by type, category, account, and dates. Filters live in the URL, so views can be bookmarked. Click a row to see the original message, edit any field, mark it reviewed, or delete it. When you change a category you can apply it to every transaction from that merchant, including future ones. Use **Add transaction** for cash.
- **Analytics**: income vs spending over time, categories and merchants ranked with change vs the previous period, and totals per account.
- **Budgets**: an overall monthly limit and per-category limits, with on-pace projections and suggestions for unbudgeted spending.
- **Recurring**: subscriptions, EMIs, and bills detected from repeating payments, with the next expected date and monthly total. Dismiss anything that isn't really recurring.
- **Accounts**: banks, cards, and wallets found in your messages, with the latest stated balance and this month's activity. Rename them to something recognisable.
- **Inbox**: every message received and what happened to it.

Press <kbd>⌘K</kbd> (<kbd>Ctrl+K</kbd>) anywhere to search transactions or jump to a page.

Totals follow fixed rules: spending is expenses plus cash withdrawals minus refunds; transfers between your own accounts (including card bill payments) and transactions flagged as duplicates are excluded; investments are shown separately.

## Privacy and operations

- PostgreSQL is hosted by Supabase and is accessed over its TLS-enabled connection string; it is never exposed by this application stack.
- Raw event bodies are not logged, and LLM request and response logging is disabled. Message text is sent only to the configured LLM endpoint.
- Change `APP_DEVICE_TOKEN` and `DATABASE_PASSWORD` in `.env` before using real data.
- Keep the LLM server on a private network or behind its API key; do not expose a keyless server publicly.
- Back up the Supabase database with `pg_dump "$DATABASE_URL" > backup.sql` after removing the `jdbc:` prefix, and restore with `psql` using the same Supabase connection details.
