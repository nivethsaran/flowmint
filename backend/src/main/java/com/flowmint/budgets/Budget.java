package com.flowmint.budgets;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** A monthly spending limit for one category, or for all spending when the key is {@link BudgetService#TOTAL}. */
@Entity
@Table(name = "budgets")
public class Budget {
    @Id private String category;
    @Column(name = "monthly_limit", nullable = false, precision = 19, scale = 4) private BigDecimal monthlyLimit;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected Budget() {}
    public Budget(String category, BigDecimal monthlyLimit) {
        this.category = category; this.monthlyLimit = monthlyLimit; this.createdAt = Instant.now(); this.updatedAt = createdAt;
    }
    public String getCategory() { return category; }
    public BigDecimal getMonthlyLimit() { return monthlyLimit; }
    public void setMonthlyLimit(BigDecimal value) { monthlyLimit = value; updatedAt = Instant.now(); }
}
