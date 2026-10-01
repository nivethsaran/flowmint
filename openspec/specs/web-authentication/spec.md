# web-authentication Specification

## Purpose
Protects the Flowmint web UI with a single configured operator account that signs in using a username, password, and time-based one-time password (TOTP), and holds a server-side session for subsequent requests.

## Requirements

### Requirement: Single configured operator account
The system SHALL authenticate exactly one operator whose username, password, and Base32 TOTP secret are supplied through the `FLOWMINT_AUTH_USERNAME`, `FLOWMINT_AUTH_PASSWORD`, and `FLOWMINT_TOTP_SECRET` environment variables. The backend MUST refuse to start if any of these values is blank.

#### Scenario: Missing credential configuration
- **WHEN** the backend starts without `FLOWMINT_TOTP_SECRET` set
- **THEN** startup fails with a configuration validation error

### Requirement: Three-factor login
The system SHALL expose `POST /api/v1/auth/login` accepting a JSON body with `username`, `password`, and a six-digit numeric `totp`. A login MUST succeed only when the username and password match the configured values (compared in constant time) and the TOTP code is valid for the current 30-second time step of the configured secret.

#### Scenario: Valid credentials
- **WHEN** the operator submits the configured username, the configured password, and the current TOTP code
- **THEN** the response is `200` with body `{"authenticated": true, "username": "<username>"}`
- **AND** an authenticated server-side session is established

#### Scenario: Any credential is wrong
- **WHEN** the username, password, or TOTP code does not match
- **THEN** the response is `401` with body `{"error": "INVALID_CREDENTIALS", "message": "Invalid credentials"}`
- **AND** the response does not reveal which credential failed

#### Scenario: Malformed TOTP code
- **WHEN** the `totp` field is not exactly six digits
- **THEN** the request is rejected before any credential check, with `401 UNAUTHORIZED` (validation errors are dispatched to a protected error path)

### Requirement: Session fixation protection
On successful login the system SHALL discard any session that existed before authentication and issue a new session to hold the authenticated security context, whether or not a prior session existed.

#### Scenario: Login without a prior session
- **WHEN** a login request with valid credentials arrives with no existing HTTP session
- **THEN** a new session is created and the login succeeds

#### Scenario: Login with a prior session
- **WHEN** a login request with valid credentials arrives carrying an existing session cookie
- **THEN** the prior session is invalidated and a different session identifier is issued

### Requirement: Session cookie properties
The session cookie SHALL be `HttpOnly`, `SameSite=Lax`, and expire after eight hours of inactivity. The `Secure` attribute MUST be controlled by `SESSION_COOKIE_SECURE` (default `false`).

#### Scenario: Idle session expiry
- **WHEN** an authenticated session has been idle for more than eight hours
- **THEN** subsequent requests with that session are treated as unauthenticated

### Requirement: Login rate limiting
The system SHALL track failed logins per client address and username. After five failures within a 15-minute window, further login attempts for that key MUST be rejected for five minutes with the same generic `401 INVALID_CREDENTIALS` response, even if the credentials are correct. A successful login MUST clear the failure history for that key.

#### Scenario: Lockout after repeated failures
- **WHEN** five failed logins occur for the same client address and username within 15 minutes
- **THEN** the next login attempt with correct credentials within five minutes returns `401 INVALID_CREDENTIALS`

#### Scenario: Failure window elapses
- **WHEN** more than 15 minutes pass after the first failure without a lockout being reached
- **THEN** the failure count starts over from the next failure

### Requirement: CSRF protection for browser sessions
The system SHALL require a CSRF token on state-changing requests authenticated by session cookie. The token MUST be issued as a JavaScript-readable `XSRF-TOKEN` cookie via `GET /api/v1/auth/csrf` and echoed in the `X-XSRF-TOKEN` header.

#### Scenario: Login without CSRF token
- **WHEN** a login request is sent without a matching `X-XSRF-TOKEN` header
- **THEN** the request is rejected with `403 FORBIDDEN`

### Requirement: Session introspection and logout
The system SHALL expose `GET /api/v1/auth/me`, returning `{"authenticated": true, "username": "<name>"}` for an authenticated caller, and `POST /api/v1/auth/logout`, which invalidates the session and returns `204`.

#### Scenario: Logout
- **WHEN** an authenticated operator posts to the logout endpoint with a valid CSRF token
- **THEN** the session is invalidated and later requests with it return `401`

### Requirement: Browser login flow
The web UI SHALL provide a `/login` page that fetches a CSRF token via `/api/auth/csrf`, submits credentials to `/api/auth/login`, and navigates to `/` on success. Any failure MUST display the message "Invalid credentials."

#### Scenario: Successful sign-in
- **WHEN** the operator submits valid credentials on `/login`
- **THEN** the browser is redirected to the dashboard at `/`

### Requirement: Unauthenticated access redirects to login
The frontend middleware SHALL respond with a `307` redirect to `/login` for any request that lacks a `JSESSIONID` cookie, except for `/login`, `/api/auth/*`, Next.js assets, the web manifest, and the favicon. This applies to API proxy routes (`/api/transactions`, `/api/flowmint/events`) as well as pages. The middleware checks only that the cookie is present; the backend validates the session. A dashboard data request that returns `401` MUST also redirect the browser to `/login`.

#### Scenario: Visiting the dashboard without a session
- **WHEN** a browser without a `JSESSIONID` cookie requests `/`
- **THEN** it is redirected to `/login`

#### Scenario: Calling a proxy route without a session cookie
- **WHEN** a client without a `JSESSIONID` cookie calls `/api/transactions` or `/api/flowmint/events`
- **THEN** the response is a `307` redirect to `/login` and the backend is not contacted

#### Scenario: Session expired while on the dashboard
- **WHEN** the dashboard's transaction request returns `401`
- **THEN** the browser navigates to `/login`
