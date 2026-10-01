# deployment Specification

## Purpose
Defines how Flowmint is configured and run: a Docker Compose stack of a private Spring Boot backend and a localhost-bound Next.js frontend, backed by a hosted Supabase PostgreSQL database with versioned migrations.

## Requirements

### Requirement: Required configuration
Docker Compose SHALL refuse to start unless `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `APP_DEVICE_TOKEN`, `FLOWMINT_AUTH_USERNAME`, `FLOWMINT_AUTH_PASSWORD`, and `FLOWMINT_TOTP_SECRET` are set, so the stack never falls back to a local or default database or credentials.

#### Scenario: Database URL missing
- **WHEN** `docker compose up` runs without `DATABASE_URL` in the environment or `.env`
- **THEN** Compose exits with the error "DATABASE_URL must point to Supabase"

### Requirement: Network exposure
The frontend SHALL be published only on `127.0.0.1:3000`. The backend MUST listen on port 8080 inside the Compose network only, with no port published to the host.

#### Scenario: Access from another machine on the LAN
- **WHEN** another host on the network connects to port 3000 or 8080 of the machine running Flowmint
- **THEN** the connection is refused

### Requirement: Hosted PostgreSQL over TLS
The backend SHALL connect to Supabase PostgreSQL using a JDBC URL (`jdbc:postgresql://…`) that requires TLS (`sslmode=require`).

#### Scenario: Non-JDBC URL
- **WHEN** `DATABASE_URL` is given as `postgresql://…` instead of `jdbc:postgresql://…`
- **THEN** the backend fails to start

### Requirement: Versioned schema migrations
The database schema SHALL be managed by Flyway migrations under `db/migration`, with baseline-on-migrate enabled for existing databases. Hibernate MUST validate the schema against the entities at startup and MUST NOT modify it.

#### Scenario: Schema drift
- **WHEN** an entity field has no matching column in the database
- **THEN** the backend fails to start with a schema validation error

### Requirement: Minimal operational endpoints
The backend SHALL expose only the `health` and `info` actuator endpoints.

#### Scenario: Requesting other actuator endpoints
- **WHEN** a client requests `/actuator/env`
- **THEN** the endpoint is not available
