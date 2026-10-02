package com.flowmint.analytics;

import com.flowmint.transactions.TransactionDirection;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The single definition of income, spending, refunds, and investments. Every surface (overview, analytics, budgets)
 * accumulates through this class, so their numbers always agree. Rows must already exclude duplicates.
 */
public final class Totals {
    private BigDecimal income = BigDecimal.ZERO;
    private BigDecimal grossSpending = BigDecimal.ZERO;
    private BigDecimal refunds = BigDecimal.ZERO;
    private BigDecimal grossInvested = BigDecimal.ZERO;
    private BigDecimal invested = BigDecimal.ZERO;
    private int count;

    public void add(AnalyticsRow row) {
        count++;
        switch (row.type()) {
            case INCOME -> income = income.add(row.amount());
            case EXPENSE, CASH_WITHDRAWAL -> grossSpending = grossSpending.add(row.amount());
            case REFUND -> refunds = refunds.add(row.amount());
            case INVESTMENT -> {
                if (row.direction() == TransactionDirection.DEBIT) grossInvested = grossInvested.add(row.amount());
                invested = row.direction() == TransactionDirection.CREDIT ? invested.subtract(row.amount()) : invested.add(row.amount());
            }
            case TRANSFER, UNKNOWN -> { }
        }
    }

    public BigDecimal income() { return income; }
    /** Expenses, cash withdrawals, and investment contributions, minus refunds. */
    public BigDecimal spending() { return grossSpending.add(grossInvested).subtract(refunds); }
    public BigDecimal grossSpending() { return grossSpending.add(grossInvested); }
    public BigDecimal refunds() { return refunds; }
    public BigDecimal invested() { return invested; }
    public BigDecimal netCashFlow() { return income.subtract(grossSpending).add(refunds).subtract(invested); }
    public int count() { return count; }

    /** (income − spending, including investment contributions) / income, or null when there was no income. */
    public BigDecimal savingsRate() {
        if (income.signum() == 0) return null;
        return income.subtract(spending()).divide(income, 4, RoundingMode.HALF_UP);
    }

    public Snapshot snapshot() {
        return new Snapshot(income, spending(), refunds, invested, netCashFlow(), savingsRate(), count);
    }

    public record Snapshot(BigDecimal income, BigDecimal spending, BigDecimal refunds, BigDecimal invested, BigDecimal netCashFlow, BigDecimal savingsRate, int transactionCount) {}
}
