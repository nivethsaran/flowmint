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

    /** Expenses and cash withdrawals: the gross spending that categories and budgets break down. */
    public boolean isGrossSpending() {
        return type == TransactionType.EXPENSE || type == TransactionType.CASH_WITHDRAWAL;
    }
}
