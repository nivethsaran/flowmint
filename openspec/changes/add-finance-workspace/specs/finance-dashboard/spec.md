# Spec Delta

## MODIFIED Requirements

### Requirement: Data loading states
Each overview section SHALL load its own data from the API (analytics summary for the selected range, recent transactions, budgets for the current month, recurring series, review queue) and show its own loading skeleton, empty state, or error state. When requests fail with a non-401 error, a page-level banner MUST say "Flowmint API unavailable" with a retry action.

#### Scenario: Backend unavailable
- **WHEN** the overview's requests fail with a non-401 error
- **THEN** the "Flowmint API unavailable" banner is shown with a retry action

#### Scenario: No transactions
- **WHEN** no transactions exist yet
- **THEN** the overview shows a "No transactions yet" empty state explaining that messages from the device create transactions

### Requirement: Expense summary
The overview SHALL show four metric cards for the selected range, computed by the analytics counting rules, each with the change versus the previous period:
- Net cash flow, with a sparkline of the series
- Income
- Spending, with transaction count
- Savings rate (shown as "—" when income is zero)

Amounts MUST be formatted as Indian rupees (`en-IN`, no fractional digits).

#### Scenario: Transactions loaded
- **WHEN** the range has ₹50,000 income and ₹30,000 spending, with no investments
- **THEN** the cards show net cash flow ₹20,000, income ₹50,000, spending ₹30,000, and savings rate 40%

### Requirement: Spending breakdown
The overview SHALL show a donut of spending by category for the selected range. The five largest categories are shown individually and the rest are combined into "Other categories". A legend shows each slice's amount and share, and the gross total is in the center.

#### Scenario: More than four categories
- **WHEN** spending spans seven categories
- **THEN** the five largest are shown individually and the remaining two are combined as "Other categories"

### Requirement: Review queue
The overview SHALL show the total number of transactions requiring review (across all dates). When non-zero, it lists up to three of them with merchant and amount, each opening the transaction detail drawer, plus a link to `/transactions?review=true`.

#### Scenario: Transactions need review
- **WHEN** two transactions have `requiresReview = true`
- **THEN** the review queue shows a count of 2 and the message "2 transactions need context"

### Requirement: Recent activity list
The overview SHALL list the six most recent transactions with merchant, category, account, date, and a signed amount ("−" debit, "+" credit, "⇄" transfer). Flagged transactions show a "Review" badge and duplicates show a "Duplicate" badge. Clicking a row opens the transaction detail drawer.

#### Scenario: Flagged transaction in the list
- **WHEN** a listed transaction has `requiresReview = true`
- **THEN** it displays a "Review" badge

### Requirement: Sign out
The header's user menu SHALL provide a sign-out action that posts to `/api/auth/logout` with the CSRF token and then navigates to `/login`.

#### Scenario: Operator signs out
- **WHEN** the operator chooses "Sign out" in the user menu
- **THEN** the session is ended and the browser shows the login page

## REMOVED Requirements

### Requirement: Date range selector is presentational
**Reason**: The range selector now drives the overview's analytics.
**Migration**: See "Date range selection".

## ADDED Requirements

### Requirement: Date range selection
The overview SHALL offer 7D, 30D, 3M, 6M, and 1Y ranges ending today (in the browser's local date), with 30D selected by default. Selecting a range MUST reload the metric cards and spending breakdown for that range and MUST be remembered in the URL (`?range=3M`).

#### Scenario: Selecting a range
- **WHEN** the operator selects "7D"
- **THEN** the metrics and breakdown show the last seven days and the URL contains `range=7D`

### Requirement: Application shell and navigation
Every authenticated page SHALL share a shell with:
- a sidebar linking to real routes: Overview `/`, Transactions `/transactions`, Analytics `/analytics`, Budgets `/budgets`, Recurring `/recurring`, Accounts `/accounts`, Inbox `/inbox`
- the active route highlighted
- a pending-message indicator on Inbox

Page content MUST be laid out in a centered container that fills the available width up to its maximum, so no dead gutter appears beside the content on wide screens. Below 900 px wide the sidebar MUST become a bottom navigation bar with Overview, Transactions, Analytics, Budgets, and a "More" sheet for the remaining pages.

#### Scenario: Wide screen
- **WHEN** the app is viewed on a 2560 px wide window
- **THEN** the content column is centered in the space beside the sidebar

#### Scenario: Phone
- **WHEN** the app is viewed on a 390 px wide screen
- **THEN** navigation is a bottom bar and every page is usable without horizontal scrolling

### Requirement: Global search
The header SHALL have a search control, opened by click or by ⌘K / Ctrl+K, that shows a command palette. Typing at least two characters MUST search transactions through the transaction search API (debounced) and list matches with merchant, date, and signed amount. Choosing one opens its detail drawer. The palette MUST also list matching navigation commands (page names, "Add transaction", "Needs review") and support arrow keys, Enter, and Escape. "See all results" MUST open `/transactions?q=<query>`.

#### Scenario: Find a transaction
- **WHEN** the operator presses ⌘K and types "amaz"
- **THEN** Amazon transactions are listed and Enter opens the first one's detail

#### Scenario: Jump to a page
- **WHEN** the operator types "budg" and presses Enter
- **THEN** the browser navigates to `/budgets`

### Requirement: Personalized header
The header SHALL show today's date and a greeting by time of day ("Good morning/afternoon/evening") with the signed-in username from `GET /api/v1/auth/me`. The avatar MUST show the username's first letter.

#### Scenario: Evening visit
- **WHEN** user "admin" opens the overview at 19:00 local time
- **THEN** the header reads "Good evening, admin" and the avatar shows "A"

### Requirement: Budget and recurring highlights
The overview SHALL show the current month's budget status (overall budget progress if set, otherwise the three category budgets with the highest percentage used) and the recurring payments expected in the next 14 days with their amounts. Each panel MUST link to its page and offer a call to action when empty.

#### Scenario: No budgets yet
- **WHEN** no budgets exist
- **THEN** the budget panel offers "Set a budget", linking to `/budgets`
