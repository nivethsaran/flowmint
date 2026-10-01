package com.flowmint.analytics;

import com.flowmint.transactions.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsServiceTest {
    static final UUID CARD = UUID.randomUUID();

    static AnalyticsRow row(String date, TransactionType type, TransactionDirection direction, String amount, Category category, String merchant) {
        return new AnalyticsRow(LocalDate.parse(date), type, direction, new BigDecimal(amount), category, merchant, merchant.toLowerCase(), CARD, "Card ••5678", PaymentChannel.CREDIT_CARD, false);
    }

    static AnalyticsRow expense(String date, String amount, Category category, String merchant) {
        return row(date, TransactionType.EXPENSE, TransactionDirection.DEBIT, amount, category, merchant);
    }

    @Test
    void totalsFollowTheCountingRules() {
        Totals totals = new Totals();
        List.of(
            row("2026-10-01", TransactionType.INCOME, TransactionDirection.CREDIT, "100000", Category.SALARY, "Acme"),
            expense("2026-10-02", "1000", Category.SHOPPING, "Amazon"),
            row("2026-10-03", TransactionType.CASH_WITHDRAWAL, TransactionDirection.DEBIT, "2000", Category.CASH, "ATM"),
            row("2026-10-04", TransactionType.REFUND, TransactionDirection.CREDIT, "300", Category.REFUNDS, "Amazon"),
            row("2026-10-05", TransactionType.TRANSFER, TransactionDirection.DEBIT, "15000", Category.CREDIT_CARD_PAYMENT, "HDFC"),
            row("2026-10-05", TransactionType.TRANSFER, TransactionDirection.CREDIT, "15000", Category.CREDIT_CARD_PAYMENT, "HDFC"),
            row("2026-10-06", TransactionType.INVESTMENT, TransactionDirection.DEBIT, "5000", Category.INVESTMENTS, "Zerodha"),
            row("2026-10-07", TransactionType.UNKNOWN, TransactionDirection.DEBIT, "999", Category.OTHER, "?")
        ).forEach(totals::add);

        assertThat(totals.income()).isEqualByComparingTo("100000");
        assertThat(totals.spending()).isEqualByComparingTo("2700");
        assertThat(totals.refunds()).isEqualByComparingTo("300");
        assertThat(totals.invested()).isEqualByComparingTo("5000");
        assertThat(totals.netCashFlow()).isEqualByComparingTo("92300");
        assertThat(totals.savingsRate()).isEqualByComparingTo("0.973");
    }

    @Test
    void savingsRateIsNullWithoutIncome() {
        Totals totals = new Totals();
        totals.add(expense("2026-10-02", "1000", Category.SHOPPING, "Amazon"));
        assertThat(totals.savingsRate()).isNull();
    }

    @Test
    void granularityFollowsRangeLength() {
        assertThat(AnalyticsService.granularity(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"))).isEqualTo(AnalyticsSummary.Granularity.DAY);
        assertThat(AnalyticsService.granularity(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-10-31"))).isEqualTo(AnalyticsSummary.Granularity.WEEK);
        assertThat(AnalyticsService.granularity(LocalDate.parse("2025-11-01"), LocalDate.parse("2026-10-31"))).isEqualTo(AnalyticsSummary.Granularity.MONTH);
    }

    @Test
    void weeklyBucketsAreClippedToTheRange() {
        List<LocalDate[]> buckets = AnalyticsService.buckets(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-11-15"), AnalyticsSummary.Granularity.WEEK);

        assertThat(buckets.get(0)).containsExactly(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-04"));
        assertThat(buckets.get(1)).containsExactly(LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-11"));
        assertThat(buckets.get(buckets.size() - 1)).containsExactly(LocalDate.parse("2026-11-09"), LocalDate.parse("2026-11-15"));
    }

    @Test
    void summarizesCurrentAndPreviousPeriods() {
        List<AnalyticsRow> rows = List.of(
            expense("2026-09-10", "400", Category.FOOD_DINING, "Swiggy"),
            expense("2026-10-02", "1000", Category.SHOPPING, "Amazon"),
            expense("2026-10-02", "500", Category.SHOPPING, "AMAZON"),
            expense("2026-10-03", "600", Category.FOOD_DINING, "Swiggy"),
            row("2026-10-04", TransactionType.REFUND, TransactionDirection.CREDIT, "300", Category.REFUNDS, "Amazon")
        );

        AnalyticsSummary summary = AnalyticsService.summarize(rows, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"),
            LocalDate.parse("2026-08-31"), LocalDate.parse("2026-09-30"));

        assertThat(summary.totals().spending()).isEqualByComparingTo("1800");
        assertThat(summary.previous().spending()).isEqualByComparingTo("400");
        assertThat(summary.series()).hasSize(31);
        assertThat(summary.series().get(1).spending()).isEqualByComparingTo("1500");
        assertThat(summary.series().get(3).spending()).isEqualByComparingTo("-300");
        assertThat(summary.categories().get(0).category()).isEqualTo(Category.SHOPPING);
        assertThat(summary.categories().get(0).share()).isEqualByComparingTo("0.7143");
        assertThat(summary.categories().get(1).previousAmount()).isEqualByComparingTo("400");
        assertThat(summary.topMerchants().get(0).amount()).isEqualByComparingTo("1500");
        assertThat(summary.topMerchants().get(0).count()).isEqualTo(2);
        assertThat(summary.accounts().get(0).spending()).isEqualByComparingTo("1800");
    }
}
