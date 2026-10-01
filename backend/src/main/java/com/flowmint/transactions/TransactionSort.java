package com.flowmint.transactions;

import org.springframework.data.domain.Sort;

public enum TransactionSort {
    date_desc(Sort.by(Sort.Order.desc("transactionDate"), Sort.Order.desc("occurredAt"), Sort.Order.desc("createdAt"))),
    date_asc(Sort.by(Sort.Order.asc("transactionDate"), Sort.Order.asc("occurredAt"), Sort.Order.asc("createdAt"))),
    amount_desc(Sort.by(Sort.Order.desc("amount"), Sort.Order.desc("transactionDate"))),
    amount_asc(Sort.by(Sort.Order.asc("amount"), Sort.Order.desc("transactionDate")));

    private final Sort sort;

    TransactionSort(Sort sort) { this.sort = sort; }

    public Sort sort() { return sort; }
}
