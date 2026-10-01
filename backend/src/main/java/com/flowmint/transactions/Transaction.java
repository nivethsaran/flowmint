package com.flowmint.transactions;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {
    @Id private UUID id;
    @Column(name = "external_event_id", nullable = false, unique = true) private String externalEventId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TransactionType type;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;
    private String merchant;
    @Column(name = "merchant_normalized") private String merchantNormalized;
    private String category;
    private String subcategory;
    @Column(name = "account_hint") private String accountHint;
    @Column(name = "transaction_date", nullable = false) private LocalDate transactionDate;
    private String description;
    private BigDecimal confidence;
    @Column(name = "requires_review", nullable = false) private boolean requiresReview;
    @Column(nullable = false) private String source;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "duplicate_of_transaction_id") private UUID duplicateOfTransactionId;
    protected Transaction() {}
    public Transaction(String externalEventId, TransactionType type, BigDecimal amount, String currency, String merchant, String category, LocalDate transactionDate, BigDecimal confidence, boolean requiresReview, String source) {
        this.id = UUID.randomUUID(); this.externalEventId = externalEventId; this.type = type; this.amount = amount; this.currency = currency; this.merchant = merchant; this.merchantNormalized = merchant; this.category = category; this.transactionDate = transactionDate; this.confidence = confidence; this.requiresReview = requiresReview; this.source = source; this.createdAt = Instant.now(); this.updatedAt = this.createdAt;
    }
    public UUID getId() { return id; }
    public String getMerchant() { return merchant; }
    public BigDecimal getAmount() { return amount; }
    public String getCategory() { return category; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public boolean isRequiresReview() { return requiresReview; }
}
