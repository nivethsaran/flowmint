package com.flowmint.analytics;

import com.flowmint.transactions.Category;
import com.flowmint.transactions.PaymentChannel;
import com.flowmint.transactions.TransactionDirection;
import com.flowmint.transactions.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AnalyticsRow(LocalDate date, TransactionType type, TransactionDirection direction, BigDecimal amount, Category category,
                           String merchant, String merchantKey, UUID accountId, String accountName, PaymentChannel channel, boolean requiresReview) {

    /** Cash outflows that categories and budgets break down. */
    public boolean isGrossSpending() {
        return type == TransactionType.EXPENSE || type == TransactionType.CASH_WITHDRAWAL
            || (type == TransactionType.INVESTMENT && direction == TransactionDirection.DEBIT);
    }
}
