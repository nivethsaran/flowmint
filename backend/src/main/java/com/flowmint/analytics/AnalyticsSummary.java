package com.flowmint.analytics;

import com.flowmint.transactions.Category;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AnalyticsSummary(
    Range range,
    Range previousRange,
    Granularity granularity,
    Totals.Snapshot totals,
    Totals.Snapshot previous,
    List<Bucket> series,
    List<CategoryAmount> categories,
    List<MerchantAmount> topMerchants,
    List<AccountAmount> accounts,
    int reviewCount
) {
    public enum Granularity { DAY, WEEK, MONTH }

    public record Range(LocalDate from, LocalDate to) {}

    public record Bucket(LocalDate start, LocalDate end, BigDecimal income, BigDecimal spending) {}

    public record CategoryAmount(Category category, BigDecimal amount, int count, BigDecimal share, BigDecimal previousAmount) {}

    public record MerchantAmount(String merchant, BigDecimal amount, int count) {}

    public record AccountAmount(UUID accountId, String name, BigDecimal spending, BigDecimal income) {}
}
