# transaction-extraction Specification

## Purpose
Turns stored raw events into structured transactions asynchronously, using a swappable extractor so the deterministic local rules can later be replaced by an LLM-backed adapter without changing ingestion or persistence.

## Requirements

### Requirement: Asynchronous processing lifecycle
The system SHALL process each newly accepted raw event asynchronously in its own database transaction. The raw event MUST move to `PROCESSING` (incrementing `processing_attempts`) before extraction, then to `PROCESSED` on success or `FAILED` on any exception.

#### Scenario: Successful extraction
- **WHEN** a raw event is processed and extraction succeeds
- **THEN** a transaction is saved and the raw event's status becomes `PROCESSED` with no error recorded

#### Scenario: Extraction throws
- **WHEN** the extractor throws an exception
- **THEN** no transaction is saved and the raw event's status becomes `FAILED` with the exception class name recorded

### Requirement: At most one transaction per event
The system SHALL create at most one transaction per external event identifier. If a transaction already exists for the event, processing MUST mark the raw event `PROCESSED` without extracting again.

#### Scenario: Transaction already exists
- **WHEN** processing starts for a raw event whose external identifier already has a transaction
- **THEN** the raw event is marked `PROCESSED` and no new transaction is created

### Requirement: Pluggable extractor
Extraction SHALL go through the `LlmClient` interface, which maps a raw event to a structured result containing type, amount, currency, merchant, category, transaction date, confidence, and a requires-review flag. The default implementation MUST be the deterministic, offline `LocalFinanceExtractor`.

#### Scenario: Running without an LLM
- **WHEN** the system runs with no LLM service available
- **THEN** events are still extracted using the local deterministic rules

### Requirement: Local amount and currency extraction
The local extractor SHALL take the first amount in the body that is prefixed by `INR`, `Rs`, `Rs.`, or `₹` (case-insensitive), ignoring thousands separators and allowing up to two decimal places. If no amount is found the amount MUST be `0`. Currency SHALL always be `INR`.

#### Scenario: Amount with separators
- **WHEN** the body contains "INR 1,299.00 debited"
- **THEN** the extracted amount is `1299.00` and the currency is `INR`

#### Scenario: No amount present
- **WHEN** the body contains no recognizable amount
- **THEN** the extracted amount is `0`

### Requirement: Local date extraction
The local extractor SHALL use the first `DD-MM-YYYY` or `DD/MM/YYYY` date in the body as the transaction date and fall back to the UTC date of the event's `timestamp` when none is found.

#### Scenario: Date in body
- **WHEN** the body contains "on 01-10-2026"
- **THEN** the transaction date is 1 October 2026

#### Scenario: No date in body
- **WHEN** the body contains no date
- **THEN** the transaction date is the UTC calendar date of the event timestamp

### Requirement: Local merchant and category mapping
The local extractor SHALL recognize merchants by case-insensitive keyword and assign a fixed category:

| Keyword(s) | Merchant | Category |
|---|---|---|
| `AMAZON`, `AMZN` | Amazon | Shopping |
| `SWIGGY` | Swiggy | Food |
| `UBER` | Uber | Transport |
| `NETFLIX` | Netflix | Subscriptions |
| `ELECTRICITY` | Electricity | Bills & Utilities |

Keywords MUST be checked in table order. Bodies matching none of them SHALL get merchant "Unknown merchant" and category "Other".

#### Scenario: Known merchant
- **WHEN** the body mentions "AMAZON"
- **THEN** the merchant is "Amazon" and the category is "Shopping"

#### Scenario: Unknown merchant
- **WHEN** the body mentions none of the known keywords
- **THEN** the merchant is "Unknown merchant" and the category is "Other"

### Requirement: Local transaction type
The local extractor SHALL classify an event as `EXPENSE` when its body contains any of "debited", "spent", "paid", "purchase", or "withdrawn" (case-insensitive), and as `UNKNOWN` otherwise. Other transaction types (income, transfer, refund, etc.) exist in the data model but MUST NOT be produced by the local extractor.

#### Scenario: Debit notification
- **WHEN** the body contains "debited"
- **THEN** the transaction type is `EXPENSE`

### Requirement: Confidence and review flag
The local extractor SHALL assign confidence `0.78` and `requiresReview = false` when the amount is greater than zero and the merchant is known. Otherwise it MUST assign confidence `0.25` and `requiresReview = true`.

#### Scenario: Unrecognized merchant needs review
- **WHEN** an event has a positive amount but an unknown merchant
- **THEN** the transaction has confidence `0.25` and is flagged for review
