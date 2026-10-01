package com.flowmint.transactions;

import com.flowmint.accounts.Account;
import com.flowmint.common.MerchantKey;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {
    @Id private UUID id;
    @Column(name = "external_event_id", unique = true) private String externalEventId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TransactionType type;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TransactionDirection direction;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;
    private String merchant;
    @Column(name = "merchant_normalized") private String merchantNormalized;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Category category;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PaymentChannel channel;
    @ManyToOne @JoinColumn(name = "account_id") private Account account;
    @Column(name = "transaction_date", nullable = false) private LocalDate transactionDate;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    private BigDecimal confidence;
    @Column(name = "requires_review", nullable = false) private boolean requiresReview;
    @Convert(converter = ReviewReasonsConverter.class) @Column(name = "review_reasons") private Set<ReviewReason> reviewReasons = EnumSet.noneOf(ReviewReason.class);
    @Column(nullable = false) private String source;
    @Enumerated(EnumType.STRING) @Column(name = "extraction_method", nullable = false) private ExtractionMethod extractionMethod;
    @Column(name = "user_edited", nullable = false) private boolean userEdited;
    private String notes;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "duplicate_of_transaction_id") private UUID duplicateOfTransactionId;

    protected Transaction() {}

    public Transaction(String externalEventId, String source, ExtractionMethod extractionMethod, TransactionType type, TransactionDirection direction, BigDecimal amount, String currency,
                       String merchant, Category category, PaymentChannel channel, Account account, LocalDate transactionDate, Instant occurredAt, BigDecimal confidence, Set<ReviewReason> reviewReasons) {
        this.id = UUID.randomUUID(); this.externalEventId = externalEventId; this.source = source; this.extractionMethod = extractionMethod;
        this.type = type; this.direction = direction; this.amount = amount; this.currency = currency;
        setMerchant(merchant); this.category = category; this.channel = channel; this.account = account;
        this.transactionDate = transactionDate; this.occurredAt = occurredAt; this.confidence = confidence; setReviewReasons(reviewReasons);
        this.createdAt = Instant.now(); this.updatedAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getExternalEventId() { return externalEventId; }
    public TransactionType getType() { return type; }
    public TransactionDirection getDirection() { return direction; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getMerchant() { return merchant; }
    public String getMerchantNormalized() { return merchantNormalized; }
    public Category getCategory() { return category; }
    public PaymentChannel getChannel() { return channel; }
    public Account getAccount() { return account; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public Instant getOccurredAt() { return occurredAt; }
    public BigDecimal getConfidence() { return confidence; }
    public boolean isRequiresReview() { return requiresReview; }
    public Set<ReviewReason> getReviewReasons() { return reviewReasons; }
    public String getSource() { return source; }
    public ExtractionMethod getExtractionMethod() { return extractionMethod; }
    public boolean isUserEdited() { return userEdited; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public UUID getDuplicateOfTransactionId() { return duplicateOfTransactionId; }

    public void markDuplicateOf(UUID original) { duplicateOfTransactionId = original; touch(); }
    public void clearDuplicate() { duplicateOfTransactionId = null; touch(); }

    public void setMerchant(String value) { merchant = value; merchantNormalized = MerchantKey.of(value); touch(); }
    public void setType(TransactionType value) { type = value; touch(); }
    public void setDirection(TransactionDirection value) { direction = value; touch(); }
    public void setAmount(BigDecimal value) { amount = value; touch(); }
    public void setCategory(Category value) { category = value; touch(); }
    public void setAccount(Account value) { account = value; touch(); }
    public void setTransactionDate(LocalDate value) { transactionDate = value; touch(); }
    public void setNotes(String value) { notes = value; touch(); }
    public void setReviewReasons(Set<ReviewReason> value) {
        reviewReasons = value == null || value.isEmpty() ? EnumSet.noneOf(ReviewReason.class) : EnumSet.copyOf(value);
        requiresReview = !reviewReasons.isEmpty();
        touch();
    }
    public void removeReviewReason(ReviewReason reason) {
        EnumSet<ReviewReason> remaining = reviewReasons.isEmpty() ? EnumSet.noneOf(ReviewReason.class) : EnumSet.copyOf(reviewReasons);
        remaining.remove(reason);
        setReviewReasons(remaining);
    }
    public void markReviewed() { setReviewReasons(Set.of()); }
    public void markUserEdited() { userEdited = true; touch(); }

    private void touch() { updatedAt = Instant.now(); }
}
