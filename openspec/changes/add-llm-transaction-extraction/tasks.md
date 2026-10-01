# Tasks

## 1. Configuration and dependencies

- [x] 1.1 Add `langchain4j` and `langchain4j-open-ai` 1.20.2 plus `spring-boot-starter-test` to `backend/pom.xml`; verify `mvn package` resolves them
- [x] 1.2 Add `app.llm.*` properties (base URL, API key, model, timeout, max tokens, max attempts, retry delays, review confidence threshold) bound to a validated `LlmProperties` record; verify startup fails when the base URL is blank
- [x] 1.3 Add `FLOWMINT_LLM_BASE_URL`, `FLOWMINT_LLM_API_KEY`, `FLOWMINT_LLM_MODEL` to `docker-compose.yml` (required with `:?`), `.env.example` (placeholders), and the local `.env`; verify `docker compose config` errors without them

## 2. Data model

- [x] 2.1 Write `V2__llm_extraction.sql` (raw event columns, `accounts` table, transaction columns, type/category/direction backfill); verify Flyway applies it on a fresh Postgres and on a database with V1 data
- [x] 2.2 Add enums `MessageKind`, `TransactionDirection`, `AccountType`, `PaymentChannel`, `Category`, `ExtractionMethod`; reduce `TransactionType` to the new set; add `RETRY` to `ProcessingStatus`; verify Hibernate `validate` passes at startup
- [x] 2.3 Add `Account` entity and repository; extend `RawEvent` and `Transaction` with the new fields; verify startup schema validation

## 3. LLM extraction

- [x] 3.1 Write the system prompt resource with kind/type/account/channel definitions and worked examples
- [ ] 3.2 Add the `LlmExtraction` record with `@Description` fields and the LangChain4j `ExtractionAssistant` AiService, wired to `OpenAiChatModel` with JSON-schema response format and request logging disabled; verify with the live evaluation test
- [x] 3.3 Implement `ExtractionValidator` (amount presence, last-four check, date window, balance check, currency, direction from type, review flags); verify with unit tests for each rule
- [x] 3.4 Implement `RuleBasedExtractor` (OTP → ignored, debit/credit keywords, amount, card/account detection); verify with unit tests

## 4. Processing pipeline

- [x] 4.1 Add the atomic claim query and due/stuck queries to `RawEventRepository`; verify with an integration run against Postgres
- [x] 4.2 Implement `ExtractionWorker` (claim → LLM → validate → persist; retry scheduling; final-attempt fallback) with a single-thread `extractionExecutor`, the after-commit listener, and the 30 s sweep; remove `EventProcessor` and `LocalFinanceExtractor`; verify with unit tests using a fake `LlmClient`
- [x] 4.3 Implement `ExtractionResultWriter` (transaction save, account discovery and balance update, duplicate detection, event status); verify with an end-to-end run where two notifications of one payment produce one duplicate
- [x] 4.4 Add reprocess endpoints (single and bulk by scope) with `404`/`409` handling; verify with curl against the running stack

## 5. Frontend middleware

- [x] 5.1 Exempt `/api/flowmint/events` from the session check and return `401` JSON for other unauthenticated `/api/*` calls; verify a cookie-less device post reaches the backend (`202`) and a cookie-less `/api/v1/transactions` gets `401`

## 6. Verification

- [ ] 6.1 Run the live evaluation test against the configured LLM with labelled messages (OTP, bank UPI debit, card purchase, card bill payment both sides, salary, refund, ATM, collect request, declined, statement, promo) and verify at least 10 of 12 classify correctly
- [ ] 6.2 Run the stack against a temporary local Postgres, post the README example and an OTP via the device route, and verify one `PROCESSED` transaction with an account and one `IGNORED` OTP event
