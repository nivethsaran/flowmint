# Design

## Context

- Extraction today: `EventProcessor` runs `@Async` after the accepting transaction commits, inside a `REQUIRES_NEW` transaction, and calls `LlmClient.extract`, implemented only by `LocalFinanceExtractor` (regex). There is no retry, and a failure leaves the event `FAILED` forever.
- LLM: `https://inferra.niveth.dev/v1` is a llama.cpp server running Qwen3.5-2B Q4_K_M. Probed on 2026-10-01: `response_format: json_schema` and tool calling both work. Generation is about 10 tokens/s and prompt processing 50–100 tokens/s, so one extraction takes roughly 5–20 s. In the probe the model mislabelled a bank-account debit as a card debit, so its output must be checked.
- Runtime: one backend instance, Supabase Postgres, Spring Boot 3.5.6, Java 21.

## Goals / Non-Goals

**Goals:**
- Reliable classification and extraction with a small local model: explicit definitions, worked examples, schema-constrained output, and validation in code.
- No lost events: retries, recovery of stuck events, and a deterministic fallback that is always flagged for review.
- Keep the `LlmClient` seam so the model or provider can change without touching ingestion.

**Non-Goals:**
- Multi-instance processing (the claim logic is safe for it, but nothing else is tuned for it).
- Non-INR currencies beyond storing the code.
- Fine-tuning or evaluating other models.

## Decisions

### LangChain4j AiService with JSON-schema structured output
Use `langchain4j` + `langchain4j-open-ai` (1.20.2). `OpenAiChatModel` points at `FLOWMINT_LLM_BASE_URL` with `supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)` and `strictJsonSchema(true)`. An `AiServices` interface returns a Java record (`LlmExtraction`) annotated with `@Description`; LangChain4j derives the JSON schema from it, so llama.cpp constrains decoding to valid JSON with enum values.
- *Alternative*: Python LangChain sidecar. Rejected (user decision): an extra service and network hop for no gain.
- *Alternative*: tool calling. It also works on this server, but `response_format` is the more direct fit for "return one object".

### Flat output schema with sentinel values
The record is flat: enums include `NONE`/`UNKNOWN`, strings use `""`, and the balance is a string. A small model fills flat schemas more reliably than nested optional objects, and strict mode requires every property anyway. Field order puts `kind` and a short `reason` first so the model commits to a classification before filling in details.

### Prompt
- The system prompt is a classpath resource, `prompts/extraction-system.txt`, of about 900 tokens. It holds kind definitions with "never TRANSACTION" rules for OTPs and requests, definitions of account type, channel and type (including credit card accounting), field rules, and nine one-line worked examples.
- The user message gives source, sender, title, event timestamp (so the model can resolve two-digit years), and body.
- llama.cpp caches the prompt prefix across calls, so the fixed system prompt is mostly reused after the first call.

### Validation in code, not trust
`ExtractionValidator` turns an `LlmExtraction` plus the `RawEvent` into either a validated `ExtractionResult` or an `InvalidExtractionException`, following the spec rules: the amount must appear among the numbers in the message, digits are checked, the date is sanity-windowed, and direction is derived from type. Review flags are computed here. Validation failures count as failed attempts, because a retry at temperature 0 with the same input usually gives the same output. The retries mainly cover transport errors; the review fallback covers bad output.

### Claim → call → persist, no transaction across the LLM call
`ExtractionWorker.process(id)`:
1. **Claim**: a single conditional `UPDATE` sets `PROCESSING`, `processing_attempts + 1`, and `locked_until = now + 5 min`, only if the event is `RECEIVED`/`RETRY` and due, or `PROCESSING` with an expired lock. Zero rows updated means someone else owns the event.
2. **Call**: the LLM is called with no open transaction.
3. **Persist**: `ExtractionResultWriter` (its own `@Transactional` bean) saves the transaction, links the account, detects duplicates, and marks the event.

Triggers: an `@TransactionalEventListener(AFTER_COMMIT)` on `@Async("extractionExecutor")` for new events, plus a `@Scheduled(fixedDelay = 30 s)` sweep that submits due and stuck ids to the same executor. The executor has one thread and a bounded queue (default 500), because a single llama.cpp slot gains nothing from concurrency.
- *Alternative*: keep `REQUIRES_NEW` around the whole thing. Rejected: it holds a pooled connection and row locks for 5–20 s per message.

### Retry schedule and fallback
Attempts are capped by `app.llm.max-attempts` (default 5), with delays from `app.llm.retry-delays` (1m, 5m, 15m, 60m). On the last failure `RuleBasedExtractor` runs; this is the old regex logic extended with OTP detection, credit keywords, and last-four/card detection. Its results are always `requiresReview` with extraction method `RULES`.

### Data model (Flyway `V2__llm_extraction.sql`)
- `raw_events` gains `message_kind varchar(30)`, `classification_reason varchar(300)`, `next_attempt_at timestamptz`, and `locked_until timestamptz`, plus an index on `(processing_status, next_attempt_at)`.
- New `accounts` table: `id`, `type`, `institution`, `last4`, `display_name`, `last_known_balance`, `balance_as_of`, `created_at`, `updated_at`, with `unique(type, last4)`.
- `transactions` gains:
  - `direction`, `channel`, `account_id` (FK to accounts), `occurred_at` (event time, used for duplicate windows)
  - `extraction_method` (`LLM` | `RULES` | `MANUAL`), `user_edited`, `notes`
  - `review_reasons`: why a transaction is flagged (e.g. `LOW_CONFIDENCE`, `UNCATEGORIZED`, `RULES_FALLBACK`), so later actions such as a merchant category rule can clear only the reason they resolve, and the UI can explain each flag. `requires_review` stays as the indexed "has any reason" flag.
  - an index on `occurred_at`
- Data migration:
  - Types: `BILL_PAYMENT`/`EMI`/`FEE` become `EXPENSE` and `INTEREST` becomes `INCOME`. Direction is backfilled from the type.
  - Category labels are mapped to keys (e.g. `Food` → `FOOD_DINING`, `Bills & Utilities` → `BILLS_UTILITIES`); anything unknown becomes `OTHER`.
  - `extraction_method` is set to `RULES` and `occurred_at` is backfilled from the raw event.
- Categories are stored as enum keys from here on, so labels can change without data migrations.

### Duplicate detection
Inside the persist transaction, look for an existing non-duplicate transaction with the same amount and direction, an `occurred_at` within ±15 minutes, a different external event id, and a compatible account. If one exists, set `duplicate_of_transaction_id`. The existing column and FK are reused.

### Middleware fix
`/api/flowmint/events` is added to the middleware's public paths, since the backend enforces the bearer token. Other `/api/*` paths without a session cookie get `401` JSON instead of an HTML redirect, which `fetch` callers cannot handle.

### Logging
`logRequests`/`logResponses` are false on the model. LLM exceptions are recorded by class name only (the existing behavior).

## Risks / Trade-offs

- [The small model misclassifies edge cases] → schema enums, worked examples, amount/digit validation, review flags at confidence < 0.7, and an opt-in live evaluation test (`LlmExtractionEvaluationTest`, runs only when `FLOWMINT_LLM_API_KEY` is set) that measures the prompt against labelled messages.
- [Throughput of about 4–10 messages/minute] → messages arrive at human rates; backlogs drain through the queue and sweep, and nothing blocks ingestion.
- [Validation failures are deterministic at temperature 0, so retries waste time] → acceptable: after at most about 81 minutes the event falls back to a review-flagged rules result. The delays are configurable.
- [Duplicate heuristic false positives (two genuine ₹100 payments within 15 minutes)] → duplicates stay visible and can be unmarked (finance workspace change); same-account matching lowers the risk.
- [Prompt prefix cache misses] → the first call after a server restart pays about 10 s of prompt processing; later calls reuse the cache.

## Migration Plan

1. Add `FLOWMINT_LLM_BASE_URL`, `FLOWMINT_LLM_API_KEY`, and optionally `FLOWMINT_LLM_MODEL` to `.env`.
2. Deploy; Flyway applies `V2` (additive columns plus in-place value mapping).
3. Optionally call `POST /api/v1/events/reprocess?scope=RULES` to re-extract historical rule-based transactions with the LLM.
4. Rollback: redeploy the previous image. The V2 columns are additive, but category keys and folded types would need a reverse mapping; `V2` is written so a reverse script is mechanical.
