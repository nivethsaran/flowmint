# Flowmint

Flowmint is a local-first personal finance command center. It accepts sensitive Iris events, stores the raw event before processing, extracts a structured transaction asynchronously, and presents deterministic financial data in a mobile-friendly Next.js PWA surface.

## Run locally

```bash
cp .env.example .env
docker compose up --build
```

Open http://localhost:3000. The backend is private to the Docker network; its health endpoint is not published to the host.

### Iris Web UI authentication

Set `IRIS_AUTH_USERNAME`, `IRIS_AUTH_PASSWORD`, and a Base32 `IRIS_TOTP_SECRET` in `.env`. The browser signs in at `/login` with all three values. Spring Boot creates the server-side session and the Next.js proxy forwards authenticated requests over the private Docker network. The session cookie is HttpOnly, SameSite=Lax, and expires after eight hours.

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
curl -X POST http://localhost:3000/api/iris/events \
  -H 'Authorization: Bearer dev-device-token-change-me' \
  -H 'Content-Type: application/json' \
  -d '{"source":"sms","sender":"HDFC-BANK","body":"INR 1299.00 debited from A/c XX1234 at AMAZON on 01-10-2026. Avl Bal INR 45,000.00","timestamp":"2026-10-01T09:00:00Z","deviceId":"device-001"}'
```

The current extraction adapter is deterministic and local so the system remains functional without an LLM. Replace `LocalFinanceExtractor` with an `LlmClient` adapter for llama.cpp without changing ingestion or persistence.

## Privacy and operations

- PostgreSQL is hosted by Supabase and is accessed over its TLS-enabled connection string; it is never exposed by this application stack.
- Raw event bodies are not logged.
- Change `APP_DEVICE_TOKEN` and `DATABASE_PASSWORD` in `.env` before using real data.
- Keep llama.cpp bound to `127.0.0.1` or a private network; do not expose it directly.
- Back up the Supabase database with `pg_dump "$DATABASE_URL" > backup.sql` after removing the `jdbc:` prefix, and restore with `psql` using the same Supabase connection details.
