# Spec Delta

## MODIFIED Requirements

### Requirement: Unauthenticated access redirects to login
The frontend middleware SHALL check requests that lack a `JSESSIONID` cookie, except for `/login`, `/api/auth/*`, the device ingestion route `/api/flowmint/events`, Next.js assets, the web manifest, and the favicon:
- Requests to `/api/*` MUST receive `401` with JSON body `{"error": "UNAUTHORIZED", "message": "Authentication required"}`.
- All other requests MUST receive a `307` redirect to `/login`.

The middleware checks only that the cookie is present; the backend validates the session. A dashboard data request that returns `401` MUST also redirect the browser to `/login`.

#### Scenario: Visiting the dashboard without a session
- **WHEN** a browser without a `JSESSIONID` cookie requests `/`
- **THEN** it is redirected to `/login`

#### Scenario: Calling a proxy route without a session cookie
- **WHEN** a client without a `JSESSIONID` cookie calls an `/api/*` route other than the exempt ones
- **THEN** the response is `401 UNAUTHORIZED` JSON and the backend is not contacted

#### Scenario: Device ingestion without a session cookie
- **WHEN** a device without cookies posts to `/api/flowmint/events`
- **THEN** the middleware lets the request through to the proxy

#### Scenario: Session expired while on the dashboard
- **WHEN** a dashboard data request returns `401`
- **THEN** the browser navigates to `/login`
