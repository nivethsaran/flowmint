package com.flowmint.recurring;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "recurring_dismissals")
public class RecurringDismissal {
    @Id @Column(name = "merchant_key") private String merchantKey;
    @Column(name = "dismissed_at", nullable = false) private Instant dismissedAt;

    protected RecurringDismissal() {}
    public RecurringDismissal(String merchantKey) { this.merchantKey = merchantKey; this.dismissedAt = Instant.now(); }
    public String getMerchantKey() { return merchantKey; }
}
