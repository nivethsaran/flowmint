# Spec Delta

## MODIFIED Requirements

### Requirement: Asynchronous processing lifecycle
The system SHALL process each accepted raw event asynchronously, outside the request that accepted it. Before extraction the event MUST be claimed by moving it to `PROCESSING` and incrementing `processing_attempts`; an event that is already claimed and whose claim has not expired MUST NOT be processed concurrently. The slow extraction call MUST NOT run inside a database transaction. The outcome MUST move the event to exactly one of:
- `PROCESSED`: a transaction was saved (or already existed)
- `IGNORED`: the message was classified as something other than a completed transaction
- `RETRY`: the attempt failed and another attempt is scheduled
- `FAILED`: all attempts and the rule-based fallback failed to produce a result

The system SHALL also periodically pick up events that are `RECEIVED` or `RETRY` and due, and events whose `PROCESSING` claim has expired (for example after a crash), and process them.

#### Scenario: Successful extraction
- **WHEN** a raw event is processed and the LLM classifies it as a completed transaction that passes validation
- **THEN** a transaction is saved and the raw event's status becomes `PROCESSED` with no error recorded

#### Scenario: Non-transaction message
- **WHEN** the LLM classifies a message as an OTP
- **THEN** no transaction is saved and the raw event's status becomes `IGNORED` with kind `OTP` and a short reason recorded

#### Scenario: Extraction throws
- **WHEN** the LLM call throws or its output fails validation on an attempt before the last
- **THEN** no transaction is saved, the raw event's status becomes `RETRY` with the exception class name recorded, and a next attempt time is set

#### Scenario: Stuck event is recovered
- **WHEN** the backend restarts while an event is `PROCESSING` and its claim later expires
- **THEN** the periodic sweep claims and processes the event again

## REMOVED Requirements

### Requirement: Pluggable extractor
**Reason**: The deterministic local extractor is no longer the default; extraction is performed by the configured LLM, with the rule-based extractor kept only as a last-resort fallback.
**Migration**: See "LLM structured extraction" and "Rule-based fallback after retries are exhausted". Configure `FLOWMINT_LLM_BASE_URL` and `FLOWMINT_LLM_API_KEY`.

### Requirement: Local amount and currency extraction
**Reason**: Amount and currency are extracted by the LLM and verified against the message text.
**Migration**: See "LLM structured extraction" and "Extraction output validation".

### Requirement: Local date extraction
**Reason**: The transaction date is extracted by the LLM and sanity-checked against the event timestamp.
**Migration**: See "Extraction output validation".

### Requirement: Local merchant and category mapping
**Reason**: The five-merchant keyword table is replaced by LLM merchant extraction into a closed category set.
**Migration**: See "Closed category set". Existing category labels are migrated to category keys.

### Requirement: Local transaction type
**Reason**: Type is now classified by the LLM using explicit definitions, including income, refunds, transfers, and credit card payments.
**Migration**: See "Transaction semantics". Existing `BILL_PAYMENT`, `EMI`, and `FEE` rows become `EXPENSE`; `INTEREST` rows become `INCOME`.

### Requirement: Confidence and review flag
**Reason**: Fixed confidence values are replaced by model-reported confidence plus validation-driven review flags.
**Migration**: See "Review flagging".

## ADDED Requirements

### Requirement: Message classification
Every processed message SHALL first be classified into exactly one kind:
- `TRANSACTION`: money has already moved (debited, spent, paid, withdrawn, credited, received, refunded)
- `OTP`: contains a one-time password or verification code, even if it mentions an amount or merchant
- `PAYMENT_REQUEST`: a request or collect request for money that has not been paid
- `FAILED_TRANSACTION`: a declined, failed, or failed-and-reversed transaction
- `BILL_REMINDER`: a bill or statement notice, amount due, or upcoming autopay/EMI
- `BALANCE_ALERT`: balance information without a new money movement
- `PROMOTIONAL`: offers, cashback offers, loan offers, advertisements
- `OTHER`: anything else

Only `TRANSACTION` messages SHALL create a transaction. The kind and a short reason (at most 300 characters, never containing OTP codes or full account numbers) MUST be stored on the raw event.

#### Scenario: OTP mentioning an amount
- **WHEN** the message is "123456 is your OTP for txn of INR 2,499.00 at AMAZON on card XX5678. Do not share."
- **THEN** the event is `IGNORED` with kind `OTP` and no transaction is created

#### Scenario: UPI collect request
- **WHEN** the message says another person "has requested Rs 500" via UPI
- **THEN** the event is `IGNORED` with kind `PAYMENT_REQUEST`

#### Scenario: Declined card transaction
- **WHEN** the message says a card transaction "was declined"
- **THEN** the event is `IGNORED` with kind `FAILED_TRANSACTION`

#### Scenario: Card statement
- **WHEN** the message states a card's total due and due date
- **THEN** the event is `IGNORED` with kind `BILL_REMINDER`

### Requirement: LLM structured extraction
The system SHALL classify and extract each message by calling the configured OpenAI-compatible chat completions endpoint at temperature 0, constraining the response to a JSON schema covering kind, transaction type, direction, amount, currency, merchant, category, account type, channel, account last four digits, institution, transaction date, available balance, confidence, and reason. The request MUST include the message body, title, sender, source, and event timestamp. The instructions MUST define each message kind, transaction type, account type, and channel, and include worked examples distinguishing OTPs, bank debits, credit card debits, credit card bill payments, refunds, and payment requests.

#### Scenario: Structured response
- **WHEN** an event is processed
- **THEN** the LLM endpoint is called with a JSON-schema response format and the parsed result determines the event outcome

#### Scenario: Unparseable response
- **WHEN** the LLM returns output that does not match the schema
- **THEN** the attempt is treated as failed and retried

### Requirement: Transaction semantics
A transaction SHALL carry:
- **type**: one of `EXPENSE` (goods, services, bills, EMIs, fees, payments to people), `INCOME` (salary, money received, interest), `REFUND`, `TRANSFER` (between the user's own accounts, including credit card bill payments), `CASH_WITHDRAWAL`, `INVESTMENT`, `UNKNOWN`
- **direction**: `DEBIT` or `CREDIT`
- **account type**: `BANK_ACCOUNT` (savings/current accounts, including debit card, UPI, net banking, ATM), `CREDIT_CARD`, `WALLET`, or `UNKNOWN`
- **channel**: `UPI`, `DEBIT_CARD`, `CREDIT_CARD`, `NETBANKING`, `ATM`, `AUTOPAY`, or `OTHER`

The direction MUST be `DEBIT` for `EXPENSE`, `CASH_WITHDRAWAL`, and `INVESTMENT`, and `CREDIT` for `INCOME` and `REFUND`; when the extracted direction contradicts the type, the type-implied direction is used and the transaction is flagged for review.

#### Scenario: Bank account UPI debit
- **WHEN** the message is "INR 450.00 debited from A/c XX1234 to VPA swiggy@icici (UPI Ref 4321)"
- **THEN** the transaction is an `EXPENSE`, `DEBIT`, account type `BANK_ACCOUNT`, channel `UPI`, last four `1234`

#### Scenario: Salary credit
- **WHEN** the message says an account was credited with salary by NEFT
- **THEN** the transaction is `INCOME`, `CREDIT`, category `SALARY`

### Requirement: Credit card accounting
A purchase on a credit card SHALL be recorded as an `EXPENSE` on the card at the time of the purchase. Paying a credit card bill SHALL be recorded as a `TRANSFER` with category `CREDIT_CARD_PAYMENT`, both for the bank-side debit and for the card-side "payment received" credit, so that card spending is never counted twice.

#### Scenario: Card purchase
- **WHEN** the message is "Rs.1,299.00 spent on your HDFC Bank Credit Card XX5678 at AMAZON"
- **THEN** the transaction is an `EXPENSE`, `DEBIT`, account type `CREDIT_CARD`, channel `CREDIT_CARD`, last four `5678`

#### Scenario: Card bill paid from bank
- **WHEN** the message says "Rs 15,000 debited from A/c XX1234 towards Credit Card XX5678 payment"
- **THEN** the transaction is a `TRANSFER`, `DEBIT`, category `CREDIT_CARD_PAYMENT` on the bank account

#### Scenario: Card receives payment
- **WHEN** the message says "Payment of Rs 15,000 received on your Credit Card XX5678"
- **THEN** the transaction is a `TRANSFER`, `CREDIT`, category `CREDIT_CARD_PAYMENT` on the card

### Requirement: Closed category set
Every transaction SHALL have exactly one category key from: `FOOD_DINING`, `GROCERIES`, `SHOPPING`, `TRANSPORT`, `FUEL`, `TRAVEL`, `BILLS_UTILITIES`, `RENT_HOUSING`, `SUBSCRIPTIONS`, `ENTERTAINMENT`, `HEALTH`, `EDUCATION`, `PERSONAL_CARE`, `INSURANCE`, `LOANS_EMI`, `FEES_CHARGES`, `TAXES`, `GIFTS_DONATIONS`, `INVESTMENTS`, `SALARY`, `INTEREST`, `REFUNDS`, `CASH`, `TRANSFERS`, `CREDIT_CARD_PAYMENT`, `OTHER_INCOME`, `OTHER`.

#### Scenario: Food delivery
- **WHEN** a debit is paid to Swiggy
- **THEN** the category is `FOOD_DINING`

### Requirement: Extraction output validation
For a `TRANSACTION` result the system SHALL verify the LLM output against the message before saving:
- The amount MUST be greater than zero and MUST equal a number that appears in the message text (ignoring thousands separators); otherwise the attempt fails.
- The account last four digits MUST appear in the message; otherwise they are discarded.
- The transaction date MUST be a valid date no later than one day after and no earlier than 60 days before the event timestamp; otherwise the event timestamp's date is used.
- An available balance is kept only if that number appears in the message and the account is a bank account or wallet.
- A currency that is not a three-letter code defaults to `INR`.

#### Scenario: Hallucinated amount
- **WHEN** the LLM returns amount `1500` for a message that contains no number equal to 1500
- **THEN** the attempt fails and is retried

#### Scenario: Missing date
- **WHEN** the message states no date
- **THEN** the transaction date is the event timestamp's UTC date

### Requirement: Review flagging
A transaction created from an LLM result SHALL be flagged `requiresReview` when any of these hold: model confidence below 0.7, unknown merchant, category `OTHER`, type `UNKNOWN`, a type/direction contradiction, or the message contains an OTP keyword despite being classified as a transaction.

#### Scenario: Low confidence
- **WHEN** the LLM reports confidence 0.5
- **THEN** the transaction is saved with `requiresReview = true`

### Requirement: Retry with backoff
A failed attempt SHALL be retried up to a configurable maximum number of attempts (default 5) with increasing delays (default 1, 5, 15, and 60 minutes).

#### Scenario: LLM endpoint unreachable
- **WHEN** the LLM endpoint is down for the first attempt
- **THEN** the event becomes `RETRY` with its next attempt about one minute later

### Requirement: Rule-based fallback after retries are exhausted
When the final attempt fails, the system SHALL apply the deterministic rules:
- If the message contains an OTP keyword, the event becomes `IGNORED` with kind `OTP`.
- Otherwise, if an amount prefixed by `INR`, `Rs`, `Rs.`, or `₹` and a debit or credit keyword are found, a transaction is saved with extraction method `RULES` and `requiresReview = true`, and the event becomes `PROCESSED`.
- Otherwise the event becomes `FAILED`.

#### Scenario: LLM down for all attempts
- **WHEN** every attempt for "INR 1,299.00 debited from A/c XX1234 at AMAZON" fails
- **THEN** an `EXPENSE` of 1299.00 is saved with extraction method `RULES` and flagged for review

#### Scenario: Unrecognizable message after all attempts
- **WHEN** every attempt fails for a message with no recognizable amount
- **THEN** the event becomes `FAILED` and no transaction is saved

### Requirement: Duplicate notification detection
When a new transaction has the same amount and direction as an existing non-duplicate transaction whose event time is within 15 minutes, and their accounts do not conflict (equal, or at least one unknown), the system SHALL save the new transaction with `duplicateOf` referencing the existing one.

#### Scenario: SMS and app notification for one payment
- **WHEN** a bank SMS and a UPI app notification for the same ₹450 debit arrive two minutes apart
- **THEN** the second transaction is saved as a duplicate of the first

### Requirement: Event reprocessing
The backend SHALL expose `POST /api/v1/events/{id}/reprocess`, which deletes the event's existing transaction (unless it was edited by the user, in which case it returns `409` with error `TRANSACTION_EDITED`), resets the event to `RECEIVED` with zero attempts, and schedules processing, returning `202`. It SHALL also expose `POST /api/v1/events/reprocess?scope=RULES|FAILED`, which does the same for all events whose transaction was produced by rules (scope `RULES`) or that are `FAILED` (scope `FAILED`), skipping user-edited transactions, and returns the number of events scheduled.

#### Scenario: Reprocess a rules-based transaction
- **WHEN** an operator reprocesses an event whose transaction was extracted by rules and not edited
- **THEN** the old transaction is deleted and the event is extracted again by the LLM

#### Scenario: Reprocess an edited transaction
- **WHEN** an operator reprocesses an event whose transaction they edited
- **THEN** the response is `409 TRANSACTION_EDITED` and nothing changes

#### Scenario: Unknown event
- **WHEN** the event id does not exist
- **THEN** the response is `404`
