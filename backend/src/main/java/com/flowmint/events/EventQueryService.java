package com.flowmint.events;

import com.flowmint.common.PageResponse;
import com.flowmint.transactions.TransactionRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

/** Read side of the message inbox. */
@Service
public class EventQueryService {
    private final RawEventRepository rawEvents;
    private final TransactionRepository transactions;

    public EventQueryService(RawEventRepository rawEvents, TransactionRepository transactions) {
        this.rawEvents = rawEvents;
        this.transactions = transactions;
    }

    static final int PREVIEW_LENGTH = 140;

    /** {@code body} is only filled for single-message detail; lists carry {@code preview}. */
    public record Item(UUID id, String source, String sender, String packageName, String title, String preview, String body, Instant eventTimestamp, Instant receivedAt,
                       ProcessingStatus status, MessageKind kind, String reason, int attempts, String lastError, Instant nextAttemptAt, UUID transactionId) {
        static Item from(RawEvent e, UUID transactionId, boolean withBody) {
            String body = e.getBody() == null ? "" : e.getBody();
            String preview = body.length() <= PREVIEW_LENGTH ? body : body.substring(0, PREVIEW_LENGTH);
            return new Item(e.getId(), e.getSource(), e.getSender(), e.getPackageName(), e.getTitle(), preview, withBody ? body : null, e.getEventTimestamp(), e.getReceivedAt(),
                e.getProcessingStatus(), e.getMessageKind(), e.getClassificationReason(), e.getProcessingAttempts(), e.getLastProcessingError(), e.getNextAttemptAt(), transactionId);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<Item> list(Collection<ProcessingStatus> statuses, MessageKind kind, String q, int page, int size) {
        Specification<RawEvent> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (statuses != null && !statuses.isEmpty()) predicates.add(root.get("processingStatus").in(statuses));
            if (kind != null) predicates.add(cb.equal(root.get("messageKind"), kind));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("body")), like, '\\'), cb.like(cb.lower(root.get("sender")), like, '\\'), cb.like(cb.lower(root.get("title")), like, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<RawEvent> events = rawEvents.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Order.desc("receivedAt"))));
        Map<String, UUID> transactionIds = transactionIds(events.getContent().stream().map(RawEvent::getExternalEventId).toList());
        return PageResponse.of(events, events.getContent().stream().map(e -> Item.from(e, transactionIds.get(e.getExternalEventId()), false)).toList());
    }

    @Transactional(readOnly = true)
    public Optional<Item> get(UUID id) {
        return rawEvents.findById(id).map(e -> Item.from(e, transactionIds(List.of(e.getExternalEventId())).get(e.getExternalEventId()), true));
    }

    @Transactional(readOnly = true)
    public Map<ProcessingStatus, Long> stats() {
        Map<ProcessingStatus, Long> counts = new EnumMap<>(ProcessingStatus.class);
        for (ProcessingStatus status : ProcessingStatus.values()) counts.put(status, 0L);
        for (Object[] row : rawEvents.countByStatus()) counts.put((ProcessingStatus) row[0], (Long) row[1]);
        return counts;
    }

    private Map<String, UUID> transactionIds(List<String> externalIds) {
        if (externalIds.isEmpty()) return Map.of();
        Map<String, UUID> ids = new HashMap<>();
        for (Object[] row : transactions.findIdsByExternalEventIds(externalIds)) ids.put((String) row[0], (UUID) row[1]);
        return ids;
    }
}
