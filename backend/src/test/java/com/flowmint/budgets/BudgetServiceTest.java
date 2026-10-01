package com.flowmint.budgets;

import com.flowmint.analytics.AnalyticsRow;
import com.flowmint.budgets.BudgetMonth.Status;
import com.flowmint.transactions.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetServiceTest {
    private static AnalyticsRow row(TransactionType type, TransactionDirection direction, String amount, Category category) {
        return new AnalyticsRow(LocalDate.parse("2026-10-05"), type, direction, new BigDecimal(amount), category, "M", "m", null, null, PaymentChannel.UPI, false);
    }

    @Test
    void statusThresholds() {
        assertThat(BudgetService.status("FOOD_DINING", new BigDecimal("10000"), new BigDecimal("3000"), 15, 30).status()).isEqualTo(Status.ON_TRACK);
        assertThat(BudgetService.status("FOOD_DINING", new BigDecimal("10000"), new BigDecimal("8000"), 29, 30).status()).isEqualTo(Status.AT_RISK);
        assertThat(BudgetService.status("FOOD_DINING", new BigDecimal("10000"), new BigDecimal("6000"), 10, 30).status()).isEqualTo(Status.AT_RISK);
        assertThat(BudgetService.status("FOOD_DINING", new BigDecimal("10000"), new BigDecimal("10001"), 30, 30).status()).isEqualTo(Status.OVER);
        assertThat(BudgetService.status("TOTAL", new BigDecimal("50000"), new BigDecimal("2000"), 1, 31).status()).isEqualTo(Status.ON_TRACK);
    }

    @Test
    void projectionAndPercent() {
        BudgetMonth.BudgetStatus status = BudgetService.status("FOOD_DINING", new BigDecimal("10000"), new BigDecimal("4000"), 10, 31);

        assertThat(status.percentUsed()).isEqualByComparingTo("40.0");
        assertThat(status.projected()).isEqualByComparingTo("12400");
        assertThat(status.remaining()).isEqualByComparingTo("6000");
        assertThat(BudgetService.status("FOOD_DINING", new BigDecimal("10000"), BigDecimal.ZERO, 0, 31).projected()).isEqualByComparingTo("0");
    }

    @Test
    void totalBudgetUsesNetSpendingAndCategoriesUseGross() {
        List<AnalyticsRow> rows = List.of(
            row(TransactionType.EXPENSE, TransactionDirection.DEBIT, "5000", Category.SHOPPING),
            row(TransactionType.REFUND, TransactionDirection.CREDIT, "1000", Category.REFUNDS),
            row(TransactionType.EXPENSE, TransactionDirection.DEBIT, "700", Category.FOOD_DINING),
            row(TransactionType.TRANSFER, TransactionDirection.DEBIT, "20000", Category.CREDIT_CARD_PAYMENT)
        );
        List<Budget> budgets = List.of(new Budget("TOTAL", new BigDecimal("50000")), new Budget("SHOPPING", new BigDecimal("4000")));

        BudgetMonth month = BudgetService.evaluate(YearMonth.of(2026, 10), 31, budgets, rows);

        assertThat(month.total().spent()).isEqualByComparingTo("4700");
        assertThat(month.budgets().get(0).spent()).isEqualByComparingTo("5000");
        assertThat(month.budgets().get(0).status()).isEqualTo(Status.OVER);
        assertThat(month.unbudgeted()).extracting(BudgetMonth.Unbudgeted::category).containsExactly(Category.FOOD_DINING);
    }
}
