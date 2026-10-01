# Spec Delta

## MODIFIED Requirements

### Requirement: Event submission endpoint
The backend SHALL expose `POST /api/v1/events`, and the frontend SHALL expose `POST /api/flowmint/events` as a proxy to it. The proxy route MUST NOT require a browser session cookie, since devices authenticate with the bearer token. The proxy MUST reject requests without an `Authorization: Bearer` header with `401 UNAUTHORIZED` before contacting the backend, and MUST forward the header and body unchanged otherwise.

#### Scenario: Device submits an event through the proxy
- **WHEN** a device posts a valid event to `/api/flowmint/events` with the correct bearer token
- **THEN** the backend's response status and body are returned to the device

#### Scenario: Device submits without a session cookie
- **WHEN** a device posts a valid event to `/api/flowmint/events` with a bearer token but no `JSESSIONID` cookie
- **THEN** the event is forwarded to the backend and accepted with `202`

#### Scenario: Missing bearer header
- **WHEN** a request to `/api/flowmint/events` has no `Authorization: Bearer` header
- **THEN** the proxy responds `401` without calling the backend

#### Scenario: Wrong device token
- **WHEN** a request reaches the backend with a bearer token that does not match `APP_DEVICE_TOKEN`
- **THEN** the backend responds `401 UNAUTHORIZED` and nothing is stored

## ADDED Requirements

### Requirement: Message bodies are shared only with the configured LLM
The system SHALL send raw event content only to the LLM endpoint configured by `FLOWMINT_LLM_BASE_URL`, and MUST NOT log LLM requests or responses.

#### Scenario: Extraction request
- **WHEN** an event is extracted
- **THEN** its content is sent to the configured LLM endpoint and does not appear in application logs
