package com.flowmint.extraction;

import com.flowmint.events.ProcessingStatus;
import com.flowmint.events.RawEvent;
import com.flowmint.events.RawEventAccepted;
import com.flowmint.events.RawEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drives each raw event through claim → LLM → validate → persist. New events are queued after their accepting
 * transaction commits; a periodic sweep picks up retries that are due and claims that expired (e.g. after a crash).
 */
@Component
public class ExtractionWorker {
    private static final Logger log = LoggerFactory.getLogger(ExtractionWorker.class);
    private static final int SWEEP_BATCH = 50;

    private final RawEventRepository rawEvents;
    private final LlmClient llm;
    private final ExtractionValidator validator;
    private final RuleBasedExtractor rules;
    private final ExtractionResultWriter writer;
    private final LlmProperties properties;
    private final TaskExecutor executor;
    private final Clock clock;
    private final Set<UUID> queued = ConcurrentHashMap.newKeySet();

    public ExtractionWorker(RawEventRepository rawEvents, LlmClient llm, ExtractionValidator validator, RuleBasedExtractor rules, ExtractionResultWriter writer,
                            LlmProperties properties, @Qualifier("extractionExecutor") TaskExecutor extractionExecutor, Clock clock) {
        this.rawEvents = rawEvents;
        this.llm = llm;
        this.validator = validator;
        this.rules = rules;
        this.writer = writer;
        this.properties = properties;
        this.executor = extractionExecutor;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccepted(RawEventAccepted accepted) {
        enqueue(accepted.eventId());
    }

    @Scheduled(initialDelayString = "PT15S", fixedDelayString = "PT30S")
    public void sweep() {
        rawEvents.findDue(RawEventRepository.CLAIMABLE, ProcessingStatus.PROCESSING, clock.instant(), PageRequest.of(0, SWEEP_BATCH)).forEach(this::enqueue);
    }

    void enqueue(UUID eventId) {
        if (!queued.add(eventId)) return;
        try {
            executor.execute(() -> run(eventId));
        } catch (TaskRejectedException e) {
            queued.remove(eventId); // queue full; the sweep will pick it up later
        }
    }

    private void run(UUID eventId) {
        try {
            process(eventId);
        } catch (RuntimeException e) {
            // Persisting failed; the claim expires and the sweep retries.
            log.warn("Extraction of event {} could not be persisted: {}", eventId, e.getClass().getSimpleName());
        } finally {
            queued.remove(eventId);
        }
    }

    void process(UUID eventId) {
        Instant now = clock.instant();
        if (rawEvents.claim(eventId, RawEventRepository.CLAIMABLE, ProcessingStatus.PROCESSING, now, now.plus(properties.claimTimeout())) == 0) return;
        RawEvent event = rawEvents.findById(eventId).orElse(null);
        if (event == null) return;

        ExtractionResult result;
        try {
            result = validator.validate(llm.extract(event), event);
        } catch (RuntimeException e) {
            handleFailure(event, e);
            return;
        }
        writer.complete(eventId, result);
    }

    private void handleFailure(RawEvent event, RuntimeException failure) {
        String error = describe(failure);
        int attempts = event.getProcessingAttempts();
        log.info("Extraction attempt {} for event {} failed: {}", attempts, event.getId(), error);
        if (attempts < properties.maxAttempts()) {
            writer.retry(event.getId(), error, clock.instant().plus(properties.retryDelay(attempts)));
            return;
        }
        ExtractionResult fallback = rules.extract(event);
        if (fallback != null) writer.complete(event.getId(), fallback);
        else writer.fail(event.getId(), error);
    }

    /** Exception type only (plus our own validation code) so message contents never reach the database or logs. */
    private static String describe(RuntimeException failure) {
        if (failure instanceof InvalidExtractionException) return failure.getClass().getSimpleName() + ": " + failure.getMessage();
        return failure.getClass().getSimpleName();
    }
}
