# Proposal

## Why

Transactions are currently extracted by a handful of regexes that recognize five merchants, treat every message with an amount as a transaction, and cannot tell an OTP, a payment request, a credit-card swipe, or a card bill payment apart. That makes every number on the dashboard unreliable. Flowmint has a self-hosted, OpenAI-compatible LLM endpoint (llama.cpp serving Qwen3.5-2B) that supports schema-constrained JSON output, so extraction can now classify messages properly and return structured, validated data.

## What Changes

- Replace the regex extractor as the primary path with an LLM call through LangChain4j that returns schema-constrained structured output.
- Classify every message before extracting anything: completed transaction, OTP, payment request, failed/declined transaction, bill/statement reminder, balance alert, promotional, other. Only completed transactions create a transaction; all others are stored as `IGNORED` with their kind and a short reason.
- Extract richer transaction semantics: type (expense, income, refund, transfer, cash withdrawal, investment), direction (debit/credit), account type (bank account, credit card, wallet), channel (UPI, debit card, credit card, net banking, ATM, autopay), last four digits, institution, closed category set, available balance, confidence.
- Account for credit cards so spending isn't double-counted: card purchases are expenses when they happen; card bill payments (bank side and card side) are transfers.
- Check the LLM's output in code. For example, the amount must appear in the message and card or account digits must appear in the message. Low-confidence or suspicious results are flagged for review.
- Make processing reliable. The slow LLM call runs outside database transactions. Failures are retried with backoff, and a recurring sweep picks up due and stuck events. After the final failed attempt, the rule-based extractor produces a best-guess transaction flagged for review, so nothing is silently lost.
- Discover accounts (bank accounts, credit cards, wallets) from transactions and track the latest stated bank balance.
- Detect duplicate notifications of the same transaction (e.g. bank SMS plus app notification) and link them to the original.
- Add reprocessing of a single event or of all rule-extracted/failed events.
- Fix device ingestion: `/api/flowmint/events` no longer requires a browser session cookie (it is authenticated by the device bearer token), and unauthenticated `/api/*` calls get `401` JSON instead of a login redirect.
- **BREAKING**: new required environment variables `FLOWMINT_LLM_BASE_URL` and `FLOWMINT_LLM_API_KEY`. Transaction types `BILL_PAYMENT`, `EMI`, `FEE`, and `INTEREST` are folded into `EXPENSE`/`INCOME` with categories; stored categories become stable keys (e.g. `FOOD_DINING`).

## Capabilities

### New Capabilities
- `accounts`: Discovery of the user's bank accounts, credit cards, and wallets from transaction messages, and tracking of stated bank balances.

### Modified Capabilities
- `transaction-extraction`: LLM classification and structured extraction replace the local regex rules as the primary path; adds output validation, retry/backoff with rules fallback, duplicate detection, and reprocessing.
- `event-ingestion`: device events reach the backend without a browser session; message bodies may be sent to the configured LLM endpoint and nowhere else.
- `web-authentication`: unauthenticated API calls get `401` JSON instead of a redirect, and the device ingestion route is exempt from the session check.
- `deployment`: LLM endpoint configuration becomes required.

## Impact

- Backend: new `extraction` package (LangChain4j assistant, prompt, validator, worker, rules fallback), new `accounts` package, changes to `events` and `transactions`; Flyway migration `V2`.
- Dependencies: `dev.langchain4j:langchain4j` and `langchain4j-open-ai`.
- Frontend: `middleware.ts`.
- Config: `.env`, `.env.example`, `docker-compose.yml`, `application.yml`.
- Privacy: raw message bodies are now sent to the configured LLM endpoint (self-hosted); they are still never logged.
- Throughput: ~5–20 s per message on the current model; processing is asynchronous and serialized.
