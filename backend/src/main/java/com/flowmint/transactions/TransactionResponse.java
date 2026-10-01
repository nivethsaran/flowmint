package com.flowmint.transactions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(UUID id, String merchant, BigDecimal amount, String category, LocalDate date, boolean requiresReview) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(transaction.getId(), transaction.getMerchant(), transaction.getAmount(), transaction.getCategory(), transaction.getTransactionDate(), transaction.isRequiresReview());
    }
}
