# Spec Delta

## Purpose

Defines how Flowmint computes income, spending, net cash flow, savings rate, and breakdowns for any date range, so that every number on the overview and analytics pages is consistent and never double-counts transfers or duplicate notifications.

## ADDED Requirements

### Requirement: Counting rules
Analytics SHALL use transaction dates and exclude transactions marked as duplicates. For a set of transactions:
- **income** = sum of `INCOME`
- **spending** = sum of `EXPENSE` + sum of `CASH_WITHDRAWAL` − sum of `REFUND`
- **invested** = sum of `INVESTMENT` debits − sum of `INVESTMENT` credits
- **net cash flow** = income − spending − invested
- **savings rate** = (income − spending) ÷ income, or `null` when income is zero

`TRANSFER` (including credit card bill payments) and `UNKNOWN` transactions MUST NOT count toward any of these.

#### Scenario: Card purchase and bill payment
- **WHEN** a ₹1,000 card purchase and a ₹1,000 card bill payment from the bank fall in the range
- **THEN** spending is ₹1,000, not ₹2,000

#### Scenario: Refund
- **WHEN** the range has ₹5,000 of expenses and a ₹500 refund
- **THEN** spending is ₹4,500

#### Scenario: Duplicate notification
- **WHEN** a ₹450 debit and its duplicate fall in the range
- **THEN** spending includes ₹450 once

### Requirement: Summary API
The backend SHALL expose `GET /api/v1/analytics/summary?from=YYYY-MM-DD&to=YYYY-MM-DD` (both required, `from` ≤ `to`, span ≤ 1,100 days, else `400 VALIDATION_ERROR`) returning:
- `totals` (income, spending, refunds, invested, netCashFlow, savingsRate, transactionCount) for the range
- `previous`: the same totals for the immediately preceding period of equal length
- `series`: zero-filled buckets with income and spending; buckets are days when the span is ≤ 31 days, Monday-start weeks when ≤ 184 days, otherwise months
- `categories`: `EXPENSE` and `CASH_WITHDRAWAL` amounts grouped by category, with share of the gross total, count, and previous-period amount, sorted by amount descending
- `topMerchants`: the five merchants with the largest `EXPENSE` + `CASH_WITHDRAWAL` totals
- `accounts`: spending and income per account
- `reviewCount`: transactions in the range requiring review

#### Scenario: Thirty-day summary
- **WHEN** the operator requests a 30-day range
- **THEN** the series has 30 daily buckets and `previous` covers the 30 days before `from`

#### Scenario: Missing parameter
- **WHEN** `to` is omitted
- **THEN** the response is `400 VALIDATION_ERROR`

### Requirement: Analytics page
The web UI SHALL provide `/analytics` with range presets (7D, 30D, 3M, 6M, 1Y, This month, Last month) showing:
- metric cards for income, spending, net cash flow, savings rate, and invested, with change versus the previous period
- an income-versus-spending chart over the series buckets
- category totals as ranked bars with share and change
- top merchants
- spending and income by account

Each section MUST show an empty state when it has no data.

#### Scenario: Switching range
- **WHEN** the operator switches from 30D to 6M
- **THEN** all sections reload for the last six months and the chart shows weekly buckets

#### Scenario: Spending increase
- **WHEN** spending is 20% higher than the previous period
- **THEN** the spending card shows a +20% change styled as unfavorable
