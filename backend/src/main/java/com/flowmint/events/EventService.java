package com.flowmint.events;

import com.flowmint.common.ApiException;
import com.flowmint.transactions.Transaction;
import com.flowmint.transactions.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class EventService {
    private final RawEventRepository rawEvents;
    private final TransactionRepository transactions;
    private final ApplicationEventPublisher publisher;

    public EventService(RawEventRepository rawEvents, TransactionRepository transactions, ApplicationEventPublisher publisher) {
        this.rawEvents = rawEvents;
        this.transactions = transactions;
        this.publisher = publisher;
    }

    @Transactional
    public RawEvent accept(EventRequest request) {
        String externalId = request.id() == null ? UUID.randomUUID().toString() : request.id().toString();
        return rawEvents.findByExternalEventId(externalId).orElseGet(() -> {
            RawEvent event = rawEvents.save(new RawEvent(UUID.randomUUID(), externalId, request.source(), request.sender(), request.packageName(), request.title(), request.body(), request.timestamp(), request.deviceId()));
            publisher.publishEvent(new RawEventAccepted(event.getId()));
            return event;
        });
    }

    @Transactional
    public void reprocess(UUID eventId) {
        RawEvent event = rawEvents.findById(eventId).orElseThrow(() -> ApiException.notFound("Event not found"));
        if (!reset(event)) throw ApiException.conflict("TRANSACTION_EDITED", "The transaction from this message was edited and is kept as is");
    }

    /** Re-extracts every event in scope, skipping those whose transaction was edited. Returns how many were scheduled. */
    @Transactional
    public int reprocess(ReprocessScope scope) {
        List<RawEvent> events = switch (scope) {
            case RULES -> rawEvents.findWithRuleExtractedTransactions();
            case FAILED -> rawEvents.findByProcessingStatus(ProcessingStatus.FAILED);
        };
        int scheduled = 0;
        for (RawEvent event : events) if (reset(event)) scheduled++;
        return scheduled;
    }

    private boolean reset(RawEvent event) {
        Optional<Transaction> existing = transactions.findByExternalEventId(event.getExternalEventId());
        if (existing.isPresent()) {
            if (existing.get().isUserEdited()) return false;
            transactions.clearDuplicatesOf(existing.get().getId());
            transactions.delete(existing.get());
        }
        event.resetForReprocessing();
        publisher.publishEvent(new RawEventAccepted(event.getId()));
        return true;
    }

    public enum ReprocessScope { RULES, FAILED }
}
