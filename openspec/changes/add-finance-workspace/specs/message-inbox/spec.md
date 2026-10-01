# Spec Delta

## Purpose

Gives the operator visibility into every message Flowmint received, how it was classified, and what happened to it, with the ability to re-run extraction.

## ADDED Requirements

### Requirement: Message list API
The backend SHALL expose `GET /api/v1/events` with optional `status` (one or more, comma-separated), `kind`, and `q` (case-insensitive match on body, sender, and title) filters and `page`/`size` pagination (same limits as transactions), ordered by received time newest first. Each item MUST include `id`, `source`, `sender`, `title`, `preview` (first 140 characters of the body), `eventTimestamp`, `receivedAt`, `status`, `kind`, `reason`, `attempts`, `nextAttemptAt`, and `transactionId`.

#### Scenario: Ignored OTPs
- **WHEN** the operator requests `?kind=OTP`
- **THEN** only messages classified as OTP are returned

### Requirement: Message detail and counts
The backend SHALL expose `GET /api/v1/events/{id}` returning the full body and all list fields (`404` if unknown), and `GET /api/v1/events/stats` returning message counts per status.

#### Scenario: Status counts
- **WHEN** 10 messages were processed, 4 ignored, and 1 is retrying
- **THEN** the stats show `PROCESSED: 10`, `IGNORED: 4`, `RETRY: 1`

### Requirement: Inbox page
The web UI SHALL provide `/inbox` with:
- tabs for All, Transactions (`PROCESSED`), Ignored, Pending (`RECEIVED`/`PROCESSING`/`RETRY`), and Failed, each showing its count
- search
- rows showing sender, preview, received time, kind badge, status, and reason; expanding a row shows the full body, a link to the resulting transaction, and a "Reprocess" action
- bulk actions "Re-extract rule-based" and "Retry failed"

The page MUST refresh automatically every 10 seconds while any message is pending.

#### Scenario: Reprocess from the inbox
- **WHEN** the operator clicks "Reprocess" on a failed message
- **THEN** it moves to Pending and later to Transactions or Ignored without a manual reload

#### Scenario: Edited transaction
- **WHEN** reprocessing returns `409 TRANSACTION_EDITED`
- **THEN** the page explains that edited transactions are kept and are not re-extracted
