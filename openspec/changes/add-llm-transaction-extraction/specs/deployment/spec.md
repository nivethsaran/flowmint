# Spec Delta

## MODIFIED Requirements

### Requirement: Required configuration
Docker Compose SHALL refuse to start unless `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `APP_DEVICE_TOKEN`, `FLOWMINT_AUTH_USERNAME`, `FLOWMINT_AUTH_PASSWORD`, `FLOWMINT_TOTP_SECRET`, `FLOWMINT_LLM_BASE_URL`, and `FLOWMINT_LLM_API_KEY` are set, so the stack never falls back to a local or default database, credentials, or LLM. `FLOWMINT_LLM_MODEL` is optional.

#### Scenario: Database URL missing
- **WHEN** `docker compose up` runs without `DATABASE_URL` in the environment or `.env`
- **THEN** Compose exits with the error "DATABASE_URL must point to Supabase"

#### Scenario: LLM endpoint missing
- **WHEN** `docker compose up` runs without `FLOWMINT_LLM_BASE_URL`
- **THEN** Compose exits with an error naming `FLOWMINT_LLM_BASE_URL`
