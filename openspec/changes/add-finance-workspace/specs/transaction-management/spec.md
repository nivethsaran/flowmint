# Spec Delta

## Purpose

Lets the operator inspect, correct, and complete their transaction record: see the source message, fix extraction mistakes, clear the review queue, teach category rules per merchant, add transactions no message reported, and resolve duplicates.

## ADDED Requirements

### Requirement: Transaction detail
The backend SHALL expose `GET /api/v1/transactions/{id}` returning every list field plus `confidence`, `occurredAt`, `accountType`, `userEdited`, `createdAt`, `updatedAt`, and, for transactions created from a message, `source` with the message's `body`, `sender`, `title`, `source`, `receivedAt`, `kind`, and `reason`. An unknown id MUST return `404 NOT_FOUND`.

#### Scenario: Transaction from a message
- **WHEN** the operator opens a transaction extracted from an SMS
- **THEN** the response includes the original SMS body and its classification reason

#### Scenario: Unknown transaction
- **WHEN** the id does not exist
- **THEN** the response is `404 NOT_FOUND`

### Requirement: Edit a transaction
The backend SHALL expose `PATCH /api/v1/transactions/{id}` accepting any of `merchant` (1–200 chars), `amount` (> 0), `type`, `category`, `accountId` (an existing account, or `null` to unlink), `date`, `notes` (≤ 1000 chars), and `reviewed`. Changing `type` MUST set `direction` to the type-implied direction (a `TRANSFER` keeps its direction unless `direction` is also supplied). Any edit MUST set `userEdited = true`. `reviewed: true` MUST set `requiresReview = false`. The response is the updated detail.

#### Scenario: Recategorize
- **WHEN** the operator patches `{"category": "GROCERIES", "reviewed": true}`
- **THEN** the transaction's category is `GROCERIES`, it no longer requires review, and it is marked user-edited

#### Scenario: Invalid amount
- **WHEN** the operator patches `{"amount": 0}`
- **THEN** the response is `400 VALIDATION_ERROR` and nothing changes

### Requirement: Merchant category rules
A `PATCH` with `category` and `applyToMerchant: true` SHALL save a rule mapping the transaction's normalized merchant (lowercase letters and digits only) to that category, update every other non-user-edited transaction with the same normalized merchant to that category, and clear their review flag if they were flagged only because of category. Newly extracted transactions whose normalized merchant has a rule MUST use the rule's category.

#### Scenario: Teach a merchant
- **WHEN** the operator sets "DMart" to `GROCERIES` with `applyToMerchant: true`
- **THEN** past DMart transactions become `GROCERIES` and future DMart transactions are extracted as `GROCERIES`

### Requirement: Manual transactions
The backend SHALL expose `POST /api/v1/transactions` accepting `date`, `amount` (> 0), `type`, `category`, `merchant`, and optionally `accountId`, `direction` (only for `TRANSFER`), and `notes`. Manual transactions have extraction method `MANUAL`, no source message, and `requiresReview = false`. The response MUST be `201` with the created detail.

#### Scenario: Cash expense
- **WHEN** the operator adds a ₹200 cash expense at "Chai stall" in `FOOD_DINING`
- **THEN** a `MANUAL` transaction is created and included in analytics

### Requirement: Delete a transaction
The backend SHALL expose `DELETE /api/v1/transactions/{id}` returning `204`. Transactions that referenced it as their original MUST stop being duplicates. The source message, if any, MUST remain in the inbox.

#### Scenario: Delete a duplicate original
- **WHEN** the operator deletes a transaction that another transaction duplicates
- **THEN** the other transaction is no longer marked as a duplicate

### Requirement: Resolve duplicates
The backend SHALL expose `POST /api/v1/transactions/{id}/not-duplicate`, which clears the duplicate link and returns the updated detail.

#### Scenario: Two genuine identical payments
- **WHEN** the operator marks a flagged duplicate as not a duplicate
- **THEN** it is counted in analytics like any other transaction

### Requirement: Transactions page
The web UI SHALL provide `/transactions` with:
- a search box and filters for type, category, account, date range, and needs review, all reflected in the URL query
- paginated rows showing merchant, category, account, date, a signed amount ("−" for debits, "+" for credits, "⇄" for transfers), and badges for review, duplicate, and manual
- a detail drawer with the source message, an edit form, "Mark reviewed", "Apply to all <merchant>", "Not a duplicate", and delete with confirmation
- an "Add transaction" form

#### Scenario: Deep link
- **WHEN** the browser opens `/transactions?review=true`
- **THEN** the needs-review filter is active and only flagged transactions are listed

#### Scenario: Review from the drawer
- **WHEN** the operator corrects a category in the drawer and saves
- **THEN** the row updates and the review badge disappears
