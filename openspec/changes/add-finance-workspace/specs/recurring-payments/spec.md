# Spec Delta

## Purpose

Finds repeating payments such as subscriptions, EMIs, SIPs, and rent in the transaction history so the operator can see what they are committed to, when it is due next, and what it costs per month.

## ADDED Requirements

### Requirement: Recurring payment detection
The system SHALL group non-duplicate `EXPENSE` and `INVESTMENT` debits from the last 400 days by normalized merchant (lowercase letters and digits only; unknown merchants excluded). It SHALL report a group as a recurring series when all of these hold:
- the median gap between consecutive payments falls in a cadence band: `WEEKLY` 5–9 days, `MONTHLY` 25–35, `QUARTERLY` 80–100, `YEARLY` 350–380
- at least 75% of gaps fall in that band
- at least 75% of amounts are within 25% of the median amount
- the group has at least 3 payments, or at least 2 when any payment was via `AUTOPAY` or categorized `SUBSCRIPTIONS`, `LOANS_EMI`, or `INSURANCE`

#### Scenario: Monthly subscription
- **WHEN** Netflix charged ₹649 on 5 July, 5 August, and 5 September
- **THEN** a `MONTHLY` Netflix series with amount ₹649 is reported

#### Scenario: Irregular merchant
- **WHEN** Swiggy orders occurred on 2, 3, 19, and 20 September
- **THEN** no Swiggy series is reported

#### Scenario: Two autopay EMIs
- **WHEN** two EMI debits via autopay occurred one month apart
- **THEN** a `MONTHLY` series is reported

### Requirement: Recurring series details
Each series SHALL include:
- `key`, `merchant`, `category`, `cadence`
- `amount` (the median), `lastAmount`, `lastDate`, `occurrences`, `accountName`
- `nextExpectedDate`: last date plus one cadence period
- `monthlyEquivalent`: amount × 52/12, 1, 1/3, or 1/12 for weekly, monthly, quarterly, and yearly
- `status`: `ACTIVE`, or `LAPSED` once today is past the next expected date by more than the grace (3, 7, 15, or 30 days)

#### Scenario: Missed subscription
- **WHEN** a monthly series' next expected date was 10 days ago
- **THEN** its status is `LAPSED`

### Requirement: Recurring API and dismissal
The backend SHALL expose `GET /api/v1/recurring` returning the series sorted by next expected date and `monthlyTotal` (sum of monthly equivalents of active, non-dismissed series). `POST /api/v1/recurring/{key}/dismiss` SHALL hide a series and `DELETE /api/v1/recurring/{key}/dismiss` SHALL restore it, both returning `204`. Dismissed series MUST be returned only when `includeDismissed=true`, marked `dismissed: true`.

#### Scenario: Dismiss a false positive
- **WHEN** the operator dismisses a series
- **THEN** it no longer appears by default and is excluded from `monthlyTotal`

### Requirement: Recurring page
The web UI SHALL provide `/recurring` showing:
- the monthly total, active count, and next payment due
- a list of series with merchant, cadence, amount, next expected date as relative time ("in 3 days", "4 days overdue"), account, and status
- dismiss and restore actions, and a "Show dismissed" toggle

#### Scenario: Upcoming payment
- **WHEN** a series is next expected in 3 days
- **THEN** its row shows "in 3 days"
