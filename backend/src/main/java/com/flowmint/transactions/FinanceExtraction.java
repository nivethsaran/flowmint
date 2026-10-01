package com.flowmint.transactions;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinanceExtraction(TransactionType type, BigDecimal amount, String currency, String merchant, String category, LocalDate transactionDate, BigDecimal confidence, boolean requiresReview) {}
