# api-access-control Specification

## Purpose
Defines how backend API requests are authorized: which endpoints are public, how trusted devices authenticate with a bearer token, and how the backend stays private behind the Next.js proxy.

## Requirements

### Requirement: Authenticated by default
The backend SHALL require authentication for every endpoint except `/actuator/health`, `/actuator/info`, `/api/v1/auth/csrf`, and `/api/v1/auth/login`. A request MAY authenticate with either an operator session or a device bearer token.

#### Scenario: Unauthenticated read request
- **WHEN** a `GET` request without a session or valid bearer token reaches a protected endpoint
- **THEN** the response is `401` with JSON body `{"error": "UNAUTHORIZED", "message": "Authentication required"}`

#### Scenario: State-changing request without credentials or CSRF token
- **WHEN** a `POST` request with no `Authorization: Bearer` header and no valid CSRF token reaches the backend
- **THEN** the CSRF check rejects it first and the response is `403` with JSON body `{"error": "FORBIDDEN", "message": "Access denied"}`

#### Scenario: Health check
- **WHEN** an unauthenticated client requests `/actuator/health`
- **THEN** the health status is returned

### Requirement: Device bearer token authentication
The backend SHALL authenticate a request as the principal `flowmint-device` when its `Authorization` header is `Bearer <token>` and `<token>` equals `APP_DEVICE_TOKEN`, using a constant-time comparison. Bearer-authenticated requests MUST be exempt from CSRF checks.

#### Scenario: Correct device token
- **WHEN** a device posts to a protected endpoint with `Authorization: Bearer <APP_DEVICE_TOKEN>`
- **THEN** the request is authenticated as `flowmint-device` without needing a CSRF token

#### Scenario: Wrong device token
- **WHEN** a device sends a bearer token that does not match `APP_DEVICE_TOKEN`
- **THEN** the request is treated as unauthenticated and receives `401`

### Requirement: Cross-origin policy
The backend SHALL allow cross-origin requests with credentials only from the configured frontend origin (`app.frontend-origin`, default `http://localhost:3000`), permitting the `Authorization`, `Content-Type`, and `X-XSRF-TOKEN` headers.

#### Scenario: Request from an unknown origin
- **WHEN** a browser on a different origin issues a cross-origin request to the backend
- **THEN** the CORS check fails and the browser blocks the response

### Requirement: Backend reachable only through the frontend proxy
The backend SHALL NOT publish a port to the host. The browser and devices MUST reach it through Next.js route handlers that forward to `FLOWMINT_API_URL` on the private Docker network. Each proxy route MUST return `500 CONFIGURATION_ERROR` when `FLOWMINT_API_URL` is unset and `502 BACKEND_UNAVAILABLE` when the backend cannot be reached.

#### Scenario: Backend down
- **WHEN** a client calls a proxy route while the backend container is not running
- **THEN** the proxy responds with `502` and error code `BACKEND_UNAVAILABLE`

### Requirement: Frontend security headers
The frontend SHALL send a Content-Security-Policy restricting sources to `self` (plus Google Fonts for styles and fonts), `frame-ancestors 'none'`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, and a `Permissions-Policy` that disables camera, microphone, and geolocation on all responses.

#### Scenario: Embedding the app in a frame
- **WHEN** another site attempts to load Flowmint in an iframe
- **THEN** the browser refuses because of `frame-ancestors 'none'`
