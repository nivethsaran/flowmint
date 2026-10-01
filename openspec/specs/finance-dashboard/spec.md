# finance-dashboard Specification

## Purpose
Presents the operator's transaction data in a mobile-friendly, installable web dashboard with summary metrics, a spending breakdown, a review queue, and recent activity.

## Requirements

### Requirement: Data loading states
The dashboard SHALL load transactions from `/api/transactions` on first render and show exactly one of these states: a loading message while the request is in flight, an empty message when no transactions exist, an error message ("Flowmint API unavailable") when the request fails, or the populated dashboard.

#### Scenario: Backend unavailable
- **WHEN** the transaction request fails with a non-401 error
- **THEN** the dashboard shows the "Flowmint API unavailable" error state

#### Scenario: No transactions
- **WHEN** the request succeeds with an empty list
- **THEN** the dashboard shows the "No transactions yet" empty state

### Requirement: Expense summary
The dashboard SHALL show an "Expenses" metric equal to the sum of the amounts of all loaded transactions, formatted as Indian rupees (`en-IN`, no fractional digits), along with the number of transactions. Income, net cash flow, and savings rate MUST show a placeholder ("—") until income analytics exist.

#### Scenario: Transactions loaded
- **WHEN** three transactions totaling ₹2,500 are loaded
- **THEN** the Expenses metric shows "₹2,500" and "3 transactions"

### Requirement: Spending breakdown
The dashboard SHALL group loaded transaction amounts by category and list up to four categories, in the order they first appear, with their totals and the overall amount spent.

#### Scenario: More than four categories
- **WHEN** loaded transactions span five categories
- **THEN** only the first four categories encountered are listed

### Requirement: Review queue
The dashboard SHALL show the number of loaded transactions flagged `requiresReview` and, when it is non-zero, a prompt to review them.

#### Scenario: Transactions need review
- **WHEN** two loaded transactions have `requiresReview = true`
- **THEN** the review queue shows a count of 2 and the message "2 transactions need context"

### Requirement: Recent activity list
The dashboard SHALL list the five most recent loaded transactions with merchant, category, date, amount shown as an outflow, and a "Review" badge for flagged transactions.

#### Scenario: Flagged transaction in the list
- **WHEN** a listed transaction has `requiresReview = true`
- **THEN** it displays a "Review" badge

### Requirement: Sign out
The dashboard SHALL provide a sign-out control that posts to `/api/auth/logout` with the CSRF token and then navigates to `/login`.

#### Scenario: Operator signs out
- **WHEN** the operator activates the sign-out control
- **THEN** the session is ended and the browser shows the login page

### Requirement: Date range selector is presentational
The dashboard SHALL offer 7D, 30D, 3M, 6M, and 1Y range buttons with 30D selected by default. Selecting a range MUST only change which button is highlighted; it does not yet change the transactions loaded or the metrics shown.

#### Scenario: Selecting a range
- **WHEN** the operator selects "7D"
- **THEN** the 7D button is highlighted and the displayed data is unchanged

### Requirement: Installable web app
The frontend SHALL serve a web app manifest (`/manifest.webmanifest`) named "Flowmint Finance OS" with standalone display, so the dashboard can be installed as a PWA.

#### Scenario: Install prompt
- **WHEN** a supporting browser loads the app
- **THEN** it can install Flowmint as a standalone app using the manifest
