package com.flowmint.rules;

import com.flowmint.transactions.Category;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "merchant_rules")
public class MerchantRule {
    @Id @Column(name = "merchant_key") private String merchantKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Category category;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected MerchantRule() {}
    public MerchantRule(String merchantKey, Category category) {
        this.merchantKey = merchantKey; this.category = category; this.createdAt = Instant.now(); this.updatedAt = createdAt;
    }
    public String getMerchantKey() { return merchantKey; }
    public Category getCategory() { return category; }
    public void setCategory(Category value) { category = value; updatedAt = Instant.now(); }
}
