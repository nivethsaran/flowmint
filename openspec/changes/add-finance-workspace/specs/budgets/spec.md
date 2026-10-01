# Spec Delta

## Purpose

Lets the operator set monthly spending limits per category and overall, and see how each month is tracking against them before the month ends.

## ADDED Requirements

### Requirement: Manage budgets
The backend SHALL expose `PUT /api/v1/budgets/{category}` with body `{"amount": n}` (amount > 0) to create or update the monthly limit for a spending category key, or for `TOTAL` (overall), and `DELETE /api/v1/budgets/{category}` returning `204` (or `404` if no budget exists). Income, transfer, and investment categories (`SALARY`, `INTEREST`, `OTHER_INCOME`, `REFUNDS`, `TRANSFERS`, `CREDIT_CARD_PAYMENT`, `INVESTMENTS`) MUST be rejected with `400 VALIDATION_ERROR`.

#### Scenario: Set a food budget
- **WHEN** the operator puts `{"amount": 8000}` to `/api/v1/budgets/FOOD_DINING`
- **THEN** a monthly ₹8,000 food budget exists

#### Scenario: Budget for a non-spending category
- **WHEN** the operator puts a budget for `SALARY`
- **THEN** the response is `400 VALIDATION_ERROR`

### Requirement: Budget status for a month
The backend SHALL expose `GET /api/v1/budgets?month=YYYY-MM` (default: the current month in the configured time zone) returning, for each budget:
- `limit` and `spent`. For a category, spent is `EXPENSE` + `CASH_WITHDRAWAL` in that category in the month, excluding duplicates. For `TOTAL`, spent is the analytics spending for the month.
- `remaining`, `percentUsed`, and `projected`. Projected is spent ÷ days elapsed × days in month for the current month, spent for past months, and 0 for future months.
- `status`: `OVER` when spent > limit; `AT_RISK` when percent used ≥ 80%, or when projected > limit and at least 7 days have elapsed (earlier projections are too noisy to act on); otherwise `ON_TRACK`.

The response MUST also list `unbudgeted` spending categories with spending in the month but no budget.

#### Scenario: On pace to overspend
- **WHEN** on day 10 of a 30-day month ₹4,000 of an ₹8,000 food budget is spent
- **THEN** projected is ₹12,000 and the status is `AT_RISK`

#### Scenario: Early-month projection is not an alarm
- **WHEN** on day 1 ₹2,000 of a ₹50,000 budget is spent
- **THEN** projected is ₹60,000 but the status is `ON_TRACK`

#### Scenario: Unbudgeted category
- **WHEN** ₹2,000 was spent on `TRAVEL` this month and no travel budget exists
- **THEN** `TRAVEL` appears in `unbudgeted` with ₹2,000

### Requirement: Budgets page
The web UI SHALL provide `/budgets` with:
- a month switcher
- an overall budget card
- a progress row per category budget showing spent, limit, remaining, projected, and status color
- inline edit and delete
- an add-budget form
- unbudgeted categories with a one-click "Set budget" that pre-fills the form

#### Scenario: Add from unbudgeted
- **WHEN** the operator clicks "Set budget" on an unbudgeted category
- **THEN** the add form opens with that category selected and the amount suggested as the month's spend rounded up to the next ₹500
