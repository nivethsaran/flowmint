package com.flowmint.events;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RawEventRepository extends JpaRepository<RawEvent, UUID>, JpaSpecificationExecutor<RawEvent> {
    List<ProcessingStatus> CLAIMABLE = List.of(ProcessingStatus.RECEIVED, ProcessingStatus.RETRY);

    Optional<RawEvent> findByExternalEventId(String externalEventId);

    /**
     * Atomically claims an event for processing. Succeeds (returns 1) only if the event is waiting and due,
     * or its previous claim expired; concurrent claimers see 0.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update RawEvent e
        set e.processingStatus = :processing, e.processingAttempts = e.processingAttempts + 1, e.lockedUntil = :lockUntil
        where e.id = :id and (
            (e.processingStatus in :claimable and (e.nextAttemptAt is null or e.nextAttemptAt <= :now))
            or (e.processingStatus = :processing and (e.lockedUntil is null or e.lockedUntil < :now)))""")
    int claim(UUID id, Collection<ProcessingStatus> claimable, ProcessingStatus processing, Instant now, Instant lockUntil);

    @Query("""
        select e.id from RawEvent e
        where (e.processingStatus in :claimable and (e.nextAttemptAt is null or e.nextAttemptAt <= :now))
           or (e.processingStatus = :processing and (e.lockedUntil is null or e.lockedUntil < :now))
        order by e.receivedAt""")
    List<UUID> findDue(Collection<ProcessingStatus> claimable, ProcessingStatus processing, Instant now, Pageable page);

    List<RawEvent> findByProcessingStatus(ProcessingStatus status);

    @Query("""
        select e from RawEvent e
        where e.externalEventId in (select t.externalEventId from Transaction t where t.extractionMethod = com.flowmint.transactions.ExtractionMethod.RULES and t.userEdited = false)""")
    List<RawEvent> findWithRuleExtractedTransactions();

    @Query("select e.processingStatus, count(e) from RawEvent e group by e.processingStatus")
    List<Object[]> countByStatus();

    @Query("select e from RawEvent e where e.userFeedback is not null and e.id <> :excludeId order by e.feedbackAt desc")
    List<RawEvent> findRecentFeedback(UUID excludeId, Pageable page);
}
