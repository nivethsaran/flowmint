package com.flowmint.budgets;

import com.flowmint.transactions.Category;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record BudgetMonth(YearMonth month, int daysInMonth, int daysElapsed, BudgetStatus total, List<BudgetStatus> budgets, List<Unbudgeted> unbudgeted) {
    public enum Status { ON_TRACK, AT_RISK, OVER }

    /** {@code category} is a {@link Category} name, or "TOTAL" for the overall budget. */
    public record BudgetStatus(String category, BigDecimal limit, BigDecimal spent, BigDecimal remaining, BigDecimal percentUsed, BigDecimal projected, Status status) {}

    public record Unbudgeted(Category category, BigDecimal spent) {}
}
