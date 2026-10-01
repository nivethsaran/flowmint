# event-ingestion Specification

## Purpose
Accepts raw financial notifications (such as bank SMS or app notifications) from trusted devices and stores them durably and idempotently before any processing happens.

## Requirements

### Requirement: Event submission endpoint
The backend SHALL expose `POST /api/v1/events`, and the frontend SHALL expose `POST /api/flowmint/events` as a proxy to it. The proxy MUST reject requests without an `Authorization: Bearer` header with `401 UNAUTHORIZED` before contacting the backend, and MUST forward the header and body unchanged otherwise. The proxy route is behind the frontend's session-cookie middleware (see web-authentication), so a request reaches the proxy handler only if it carries a `JSESSIONID` cookie.

#### Scenario: Device submits an event through the proxy
- **WHEN** a device posts a valid event to `/api/flowmint/events` with the correct bearer token and a `JSESSIONID` cookie
- **THEN** the backend's response status and body are returned to the device

#### Scenario: Device submits without a session cookie
- **WHEN** a device posts to `/api/flowmint/events` with a bearer token but no `JSESSIONID` cookie
- **THEN** the middleware responds with a `307` redirect to `/login` and the event is not ingested

#### Scenario: Missing bearer header
- **WHEN** a request to `/api/flowmint/events` that passes the middleware has no `Authorization: Bearer` header
- **THEN** the proxy responds `401` without calling the backend

#### Scenario: Wrong device token
- **WHEN** a request reaches the backend with a bearer token that does not match `APP_DEVICE_TOKEN`
- **THEN** the backend responds `401 UNAUTHORIZED` and nothing is stored

### Requirement: Event payload validation
An event SHALL contain `source` (required, max 30 chars), `body` (required, max 10,000 chars), `timestamp` (required ISO-8601 instant), and `deviceId` (required, max 120 chars). It MAY contain `id` (UUID), `sender` (max 160), `packageName` (max 200), and `title` (max 300). Payloads violating these constraints MUST be rejected with `400` and not stored.

#### Scenario: Missing body
- **WHEN** an event is submitted without a `body`
- **THEN** the request is rejected with `400` and nothing is persisted

### Requirement: Store raw event before processing
The system SHALL persist each accepted event to `raw_events` with status `RECEIVED`, its receive time, and zero processing attempts, and return `202 Accepted` with `{"accepted": true, "eventId": "<raw event id>"}`. Processing MUST start only after this write has committed.

#### Scenario: New event accepted
- **WHEN** a valid event with a previously unseen `id` is submitted
- **THEN** a raw event row is stored with status `RECEIVED`
- **AND** the response is `202` with the new raw event's id
- **AND** asynchronous extraction is triggered after the transaction commits

### Requirement: Idempotent ingestion
The system SHALL use the client-supplied `id` as the event's external identifier and MUST NOT create a second raw event or trigger reprocessing when an event with the same external identifier already exists. When no `id` is supplied, the system SHALL generate a random external identifier, so such submissions are never deduplicated.

#### Scenario: Duplicate submission
- **WHEN** an event is submitted with an `id` that was already accepted
- **THEN** the response is `202` with the existing raw event's id
- **AND** no new raw event is stored and no processing is triggered

#### Scenario: Submission without an id
- **WHEN** the same payload is submitted twice without an `id`
- **THEN** two separate raw events are stored

### Requirement: Raw event bodies are not logged
The system SHALL NOT write raw event bodies to application logs. Processing errors MUST record only the exception type, not message contents.

#### Scenario: Extraction failure
- **WHEN** extraction of an event throws an exception
- **THEN** the raw event's `last_processing_error` contains only the exception class name
