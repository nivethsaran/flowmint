package com.flowmint.extraction;

import com.flowmint.accounts.AccountType;
import com.flowmint.transactions.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

public record TransactionDraft(
    TransactionType type,
    TransactionDirection direction,
    BigDecimal amount,
    String currency,
    String merchant,
    Category category,
    AccountType accountType,
    PaymentChannel channel,
    String accountLast4,
    String institution,
    LocalDate date,
    BigDecimal availableBalance,
    BigDecimal confidence,
    Set<ReviewReason> reviewReasons,
    ExtractionMethod method
) {
    public static final String UNKNOWN_MERCHANT = "Unknown merchant";

    public TransactionDraft {
        reviewReasons = reviewReasons.isEmpty() ? EnumSet.noneOf(ReviewReason.class) : EnumSet.copyOf(reviewReasons);
    }

    public boolean requiresReview() { return !reviewReasons.isEmpty(); }

    /** Applies a user's category rule; the category is then known, so it no longer needs review for that reason. */
    public TransactionDraft withRuleCategory(Category value) {
        EnumSet<ReviewReason> reasons = reviewReasons.isEmpty() ? EnumSet.noneOf(ReviewReason.class) : EnumSet.copyOf(reviewReasons);
        reasons.remove(ReviewReason.UNCATEGORIZED);
        return new TransactionDraft(type, direction, amount, currency, merchant, value, accountType, channel, accountLast4, institution, date, availableBalance, confidence, reasons, method);
    }
}
