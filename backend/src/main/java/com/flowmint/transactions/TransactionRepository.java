package com.flowmint.transactions;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.*;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByExternalEventId(String externalEventId);
    List<Transaction> findTop20ByOrderByTransactionDateDesc();
    List<Transaction> findByTransactionDateBetweenOrderByTransactionDateDesc(LocalDate from, LocalDate to);
}
