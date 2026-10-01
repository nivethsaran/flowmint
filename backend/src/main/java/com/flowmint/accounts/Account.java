package com.flowmint.accounts;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {
    @Id private UUID id;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AccountType type;
    private String institution;
    @Column(nullable = false, length = 4) private String last4;
    @Column(name = "display_name", nullable = false) private String displayName;
    @Column(name = "last_known_balance", precision = 19, scale = 4) private BigDecimal lastKnownBalance;
    @Column(name = "balance_as_of") private Instant balanceAsOf;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected Account() {}
    public Account(AccountType type, String last4, String institution) {
        this.id = UUID.randomUUID(); this.type = type; this.last4 = last4; this.institution = institution;
        this.displayName = (institution == null || institution.isBlank() ? type.label() : institution) + " ••" + last4;
        this.createdAt = Instant.now(); this.updatedAt = createdAt;
    }
    public UUID getId() { return id; }
    public AccountType getType() { return type; }
    public String getInstitution() { return institution; }
    public String getLast4() { return last4; }
    public String getDisplayName() { return displayName; }
    public BigDecimal getLastKnownBalance() { return lastKnownBalance; }
    public Instant getBalanceAsOf() { return balanceAsOf; }

    /** Keeps the balance from the most recent message; older messages processed late never overwrite it. */
    public void recordBalance(BigDecimal balance, Instant asOf) {
        if (balanceAsOf != null && !asOf.isAfter(balanceAsOf)) return;
        lastKnownBalance = balance; balanceAsOf = asOf; updatedAt = Instant.now();
    }

    public void fillInstitution(String value) {
        if ((institution == null || institution.isBlank()) && value != null && !value.isBlank()) { institution = value; updatedAt = Instant.now(); }
    }

    public void rename(String value) { displayName = value; updatedAt = Instant.now(); }
}
