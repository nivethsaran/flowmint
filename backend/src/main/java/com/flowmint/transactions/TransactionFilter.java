package com.flowmint.transactions;

import com.flowmint.accounts.Account;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Search and filters for the transaction list. Every field is optional; present fields are combined with AND. */
public record TransactionFilter(String q, TransactionType type, Category category, UUID accountId, TransactionDirection direction,
                                Boolean review, boolean includeDuplicates, LocalDate from, LocalDate to) {

    public Specification<Transaction> toSpecification() {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (q != null && !q.isBlank()) predicates.add(text(root, cb, q.strip()));
            if (type != null) predicates.add(cb.equal(root.get("type"), type));
            if (category != null) predicates.add(cb.equal(root.get("category"), category));
            if (accountId != null) predicates.add(cb.equal(root.get("account").get("id"), accountId));
            if (direction != null) predicates.add(cb.equal(root.get("direction"), direction));
            if (review != null) predicates.add(cb.equal(root.get("requiresReview"), review));
            if (!includeDuplicates) predicates.add(cb.isNull(root.get("duplicateOfTransactionId")));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), to));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate text(Root<Transaction> root, CriteriaBuilder cb, String query) {
        String like = "%" + query.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        Join<Transaction, Account> account = root.join("account", JoinType.LEFT);
        List<Predicate> any = new ArrayList<>();
        any.add(cb.like(cb.lower(root.get("merchant")), like, '\\'));
        any.add(cb.like(cb.lower(root.get("notes")), like, '\\'));
        any.add(cb.like(cb.lower(account.get("displayName")), like, '\\'));
        List<Category> categories = Arrays.stream(Category.values()).filter(c -> c.labelContains(query)).toList();
        if (!categories.isEmpty()) any.add(root.get("category").in(categories));
        BigDecimal amount = amountOf(query);
        if (amount != null) any.add(cb.equal(root.get("amount"), amount));
        return cb.or(any.toArray(Predicate[]::new));
    }

    private static BigDecimal amountOf(String query) {
        String cleaned = query.replace(",", "").replace("₹", "").strip();
        if (!cleaned.matches("\\d+(\\.\\d+)?")) return null;
        return new BigDecimal(cleaned);
    }
}
