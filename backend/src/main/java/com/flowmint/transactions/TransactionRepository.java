package com.flowmint.transactions;

import com.flowmint.analytics.AnalyticsRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {
    Optional<Transaction> findByExternalEventId(String externalEventId);

    List<Transaction> findByMerchantNormalizedAndUserEditedFalse(String merchantNormalized);

    @Query("""
        select t from Transaction t
        where t.duplicateOfTransactionId is null and t.amount = :amount and t.direction = :direction
          and t.occurredAt between :start and :end and (t.externalEventId is null or t.externalEventId <> :externalEventId)
        order by t.occurredAt""")
    List<Transaction> findDuplicateCandidates(BigDecimal amount, TransactionDirection direction, Instant start, Instant end, String externalEventId);

    @Modifying
    @Query("update Transaction t set t.duplicateOfTransactionId = null where t.duplicateOfTransactionId = :original")
    int clearDuplicatesOf(UUID original);

    /** Lightweight rows for analytics; duplicates are never counted. */
    @Query("""
        select new com.flowmint.analytics.AnalyticsRow(t.transactionDate, t.type, t.direction, t.amount, t.category, t.merchant, t.merchantNormalized,
                                                       a.id, a.displayName, t.channel, t.requiresReview)
        from Transaction t left join t.account a
        where t.duplicateOfTransactionId is null and t.transactionDate between :from and :to""")
    List<AnalyticsRow> findAnalyticsRows(LocalDate from, LocalDate to);

    @Query("select t.externalEventId, t.id from Transaction t where t.externalEventId in :externalEventIds")
    List<Object[]> findIdsByExternalEventIds(Collection<String> externalEventIds);

    @Query("select t.account.id, count(t), max(t.transactionDate) from Transaction t where t.account is not null group by t.account.id")
    List<Object[]> accountActivity();

    @Query("""
        select t.account.id, t.direction, sum(t.amount) from Transaction t
        where t.account is not null and t.duplicateOfTransactionId is null and t.transactionDate between :from and :to
        group by t.account.id, t.direction""")
    List<Object[]> accountFlows(LocalDate from, LocalDate to);
}
