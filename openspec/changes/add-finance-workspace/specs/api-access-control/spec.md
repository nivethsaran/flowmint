# Spec Delta

## MODIFIED Requirements

### Requirement: Backend reachable only through the frontend proxy
The backend SHALL NOT publish a port to the host. The browser and devices MUST reach it through Next.js route handlers that forward to `FLOWMINT_API_URL` on the private Docker network:
- the auth routes (`/api/auth/*`)
- the device ingestion route (`/api/flowmint/events`)
- a generic proxy at `/api/v1/*` that forwards method, path, query string, body, `Cookie`, `Content-Type`, and `X-XSRF-TOKEN` without caching, and returns the backend's status, body, `Content-Type`, and every `Set-Cookie` header

Each proxy route MUST return `500 CONFIGURATION_ERROR` when `FLOWMINT_API_URL` is unset and `502 BACKEND_UNAVAILABLE` when the backend cannot be reached.

#### Scenario: Backend down
- **WHEN** a client calls a proxy route while the backend container is not running
- **THEN** the proxy responds with `502` and error code `BACKEND_UNAVAILABLE`

#### Scenario: Mutation through the generic proxy
- **WHEN** the browser sends `PATCH /api/v1/transactions/{id}` with its session cookie and CSRF header
- **THEN** the backend receives the same method, path, body, cookie, and header

## ADDED Requirements

### Requirement: Consistent error responses
Backend errors SHALL be JSON `{"error": CODE, "message": text}`:
- `400 VALIDATION_ERROR` for invalid bodies, parameters, or path values, with a `fields` object mapping field names to messages where applicable
- `404 NOT_FOUND` for unknown resources
- `409` with a specific code for state conflicts
- `500 INTERNAL_ERROR` for unexpected errors, without stack traces or message contents

Validation errors MUST NOT be turned into `401` by the error page being protected.

#### Scenario: Invalid query parameter
- **WHEN** an authenticated caller requests `/api/v1/transactions?type=BOGUS`
- **THEN** the response is `400` with error `VALIDATION_ERROR`

#### Scenario: Unexpected failure
- **WHEN** an unhandled exception occurs
- **THEN** the response is `500 INTERNAL_ERROR` with a generic message
