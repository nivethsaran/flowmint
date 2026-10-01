# Spec Delta

## MODIFIED Requirements

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
- **THEN** the request is rejected before any credential check with `400 VALIDATION_ERROR`
