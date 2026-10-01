package com.flowmint.transactions;

import com.flowmint.accounts.Account;
import com.flowmint.accounts.AccountType;
import com.flowmint.events.MessageKind;
import com.flowmint.events.RawEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Transaction API shapes: {@link Item} for lists, {@link Detail} for a single transaction. */
public final class TransactionResponses {
    private TransactionResponses() {}

    public record Item(UUID id, LocalDate date, String merchant, BigDecimal amount, String currency, TransactionType type, TransactionDirection direction,
                       Category category, UUID accountId, String accountName, PaymentChannel channel, boolean requiresReview, UUID duplicateOfId,
                       ExtractionMethod extractionMethod, String notes) {
        public static Item from(Transaction t) {
            Account account = t.getAccount();
            return new Item(t.getId(), t.getTransactionDate(), t.getMerchant(), t.getAmount(), t.getCurrency(), t.getType(), t.getDirection(), t.getCategory(),
                account == null ? null : account.getId(), account == null ? null : account.getDisplayName(), t.getChannel(), t.isRequiresReview(),
                t.getDuplicateOfTransactionId(), t.getExtractionMethod(), t.getNotes());
        }
    }

    public record Reason(ReviewReason code, String description) {}

    public record Source(UUID eventId, String source, String sender, String title, String body, Instant receivedAt, MessageKind kind, String reason) {
        static Source from(RawEvent e) {
            return e == null ? null : new Source(e.getId(), e.getSource(), e.getSender(), e.getTitle(), e.getBody(), e.getReceivedAt(), e.getMessageKind(), e.getClassificationReason());
        }
    }

    public record Detail(UUID id, LocalDate date, String merchant, BigDecimal amount, String currency, TransactionType type, TransactionDirection direction,
                         Category category, UUID accountId, String accountName, AccountType accountType, PaymentChannel channel, boolean requiresReview,
                         List<Reason> reviewReasons, UUID duplicateOfId, ExtractionMethod extractionMethod, String notes, BigDecimal confidence,
                         Instant occurredAt, boolean userEdited, Instant createdAt, Instant updatedAt, Source source) {
        public static Detail from(Transaction t, RawEvent event) {
            Account account = t.getAccount();
            return new Detail(t.getId(), t.getTransactionDate(), t.getMerchant(), t.getAmount(), t.getCurrency(), t.getType(), t.getDirection(), t.getCategory(),
                account == null ? null : account.getId(), account == null ? null : account.getDisplayName(), account == null ? null : account.getType(),
                t.getChannel(), t.isRequiresReview(), t.getReviewReasons().stream().map(r -> new Reason(r, r.description())).toList(),
                t.getDuplicateOfTransactionId(), t.getExtractionMethod(), t.getNotes(), t.getConfidence(), t.getOccurredAt(), t.isUserEdited(),
                t.getCreatedAt(), t.getUpdatedAt(), Source.from(event));
        }
    }
}
