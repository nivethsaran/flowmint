package com.flowmint.events;

import com.flowmint.transactions.FinanceExtraction;
import com.flowmint.transactions.LlmClient;
import com.flowmint.transactions.Transaction;
import com.flowmint.transactions.TransactionRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import java.util.UUID;
import java.util.Objects;

@Service
public class EventProcessor {
    private final RawEventRepository rawEvents;
    private final TransactionRepository transactions;
    private final LlmClient extractor;

    public EventProcessor(RawEventRepository rawEvents, TransactionRepository transactions, LlmClient extractor) {
        this.rawEvents = rawEvents;
        this.transactions = transactions;
        this.extractor = extractor;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processAsync(RawEventAccepted accepted) {
        UUID eventId = Objects.requireNonNull(accepted.eventId());
        RawEvent event = rawEvents.findById(eventId).orElseThrow();
        if (transactions.findByExternalEventId(event.getExternalEventId()).isPresent()) {
            event.markProcessed();
            return;
        }
        try {
            event.markProcessing();
            FinanceExtraction extraction = extractor.extract(event);
            transactions.save(new Transaction(event.getExternalEventId(), extraction.type(), extraction.amount(), extraction.currency(), extraction.merchant(), extraction.category(), extraction.transactionDate(), extraction.confidence(), extraction.requiresReview(), event.getSource()));
            event.markProcessed();
        } catch (Exception exception) {
            event.markFailed(exception);
        }
    }
}