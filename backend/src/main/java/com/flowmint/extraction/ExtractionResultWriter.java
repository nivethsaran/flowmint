package com.flowmint.extraction;

import com.flowmint.accounts.Account;
import com.flowmint.accounts.AccountService;
import com.flowmint.events.MessageKind;
import com.flowmint.events.RawEvent;
import com.flowmint.events.RawEventRepository;
import com.flowmint.transactions.Transaction;
import com.flowmint.transactions.TransactionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Persists extraction outcomes. Each method is its own short transaction; no LLM call ever runs inside one. */
@Component
public class ExtractionResultWriter {
    static final Duration DUPLICATE_WINDOW = Duration.ofMinutes(15);

    private final RawEventRepository rawEvents;
    private final TransactionRepository transactions;
    private final AccountService accounts;
    private final CategoryOverrides categoryOverrides;

    public ExtractionResultWriter(RawEventRepository rawEvents, TransactionRepository transactions, AccountService accounts, CategoryOverrides categoryOverrides) {
        this.rawEvents = rawEvents;
        this.transactions = transactions;
        this.accounts = accounts;
        this.categoryOverrides = categoryOverrides;
    }

    @Transactional
    public void complete(UUID eventId, ExtractionResult result) {
        RawEvent event = rawEvents.findById(eventId).orElseThrow();
        if (!result.isTransaction()) {
            event.markIgnored(result.kind(), result.reason());
            return;
        }
        if (transactions.findByExternalEventId(event.getExternalEventId()).isEmpty()) {
            TransactionDraft draft = categoryOverrides.apply(result.transaction());
            Account account = accounts.resolve(draft.accountType(), draft.accountLast4(), draft.institution());
            Transaction transaction = new Transaction(event.getExternalEventId(), event.getSource(), draft.method(), draft.type(), draft.direction(), draft.amount(), draft.currency(),
                draft.merchant(), draft.category(), draft.channel(), account, draft.date(), event.getEventTimestamp(), draft.confidence(), draft.reviewReasons());
            findOriginal(transaction).ifPresent(original -> transaction.markDuplicateOf(original.getId()));
            transactions.save(transaction);
            accounts.recordBalance(account, draft.availableBalance(), event.getEventTimestamp());
        }
        event.markProcessed(MessageKind.TRANSACTION, result.reason());
    }

    @Transactional
    public void retry(UUID eventId, String error, Instant nextAttempt) {
        rawEvents.findById(eventId).ifPresent(event -> event.markRetry(error, nextAttempt));
    }

    @Transactional
    public void fail(UUID eventId, String error) {
        rawEvents.findById(eventId).ifPresent(event -> event.markFailed(error));
    }

    /** The same money movement reported twice (e.g. bank SMS plus UPI app notification) within the window. */
    private Optional<Transaction> findOriginal(Transaction candidate) {
        Instant at = candidate.getOccurredAt();
        UUID accountId = candidate.getAccount() == null ? null : candidate.getAccount().getId();
        return transactions.findDuplicateCandidates(candidate.getAmount(), candidate.getDirection(), at.minus(DUPLICATE_WINDOW), at.plus(DUPLICATE_WINDOW), candidate.getExternalEventId())
            .stream()
            .filter(existing -> accountId == null || existing.getAccount() == null || Objects.equals(existing.getAccount().getId(), accountId))
            .findFirst();
    }
}
