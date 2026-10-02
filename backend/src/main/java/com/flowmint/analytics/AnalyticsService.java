package com.flowmint.analytics;

import com.flowmint.analytics.AnalyticsSummary.*;
import com.flowmint.common.ApiException;
import com.flowmint.transactions.Category;
import com.flowmint.transactions.TransactionDirection;
import com.flowmint.transactions.TransactionRepository;
import com.flowmint.transactions.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
public class AnalyticsService {
    static final int MAX_SPAN_DAYS = 1100;
    private static final int TOP_MERCHANTS = 5;
    private final TransactionRepository transactions;

    public AnalyticsService(TransactionRepository transactions) { this.transactions = transactions; }

    @Transactional(readOnly = true)
    public AnalyticsSummary summary(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) throw ApiException.validation("from", "Must not be after to");
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_SPAN_DAYS) throw ApiException.validation("to", "Range is limited to " + MAX_SPAN_DAYS + " days");
        LocalDate previousTo = from.minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(days - 1);
        return summarize(transactions.findAnalyticsRows(previousFrom, to), from, to, previousFrom, previousTo);
    }

    static AnalyticsSummary summarize(List<AnalyticsRow> rows, LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo) {
        Granularity granularity = granularity(from, to);
        List<LocalDate[]> bounds = buckets(from, to, granularity);
        BigDecimal[] bucketIncome = zeros(bounds.size());
        BigDecimal[] bucketSpending = zeros(bounds.size());

        Totals current = new Totals();
        Totals previous = new Totals();
        Map<Category, BigDecimal> categoryAmounts = new EnumMap<>(Category.class);
        Map<Category, Integer> categoryCounts = new EnumMap<>(Category.class);
        Map<Category, BigDecimal> previousCategoryAmounts = new EnumMap<>(Category.class);
        Map<String, MerchantTally> merchants = new HashMap<>();
        Map<UUID, AccountTally> accounts = new LinkedHashMap<>();
        int reviewCount = 0;

        for (AnalyticsRow row : rows) {
            if (!row.date().isBefore(previousFrom) && !row.date().isAfter(previousTo)) {
                previous.add(row);
                if (row.isGrossSpending()) previousCategoryAmounts.merge(row.category(), row.amount(), BigDecimal::add);
                continue;
            }
            if (row.date().isBefore(from) || row.date().isAfter(to)) continue;

            current.add(row);
            if (row.requiresReview()) reviewCount++;
            int bucket = bucketIndex(bounds, row.date());
            BigDecimal signedSpending = signedSpending(row);
            if (row.type() == TransactionType.INCOME) bucketIncome[bucket] = bucketIncome[bucket].add(row.amount());
            bucketSpending[bucket] = bucketSpending[bucket].add(signedSpending);

            if (row.isGrossSpending()) {
                categoryAmounts.merge(row.category(), row.amount(), BigDecimal::add);
                categoryCounts.merge(row.category(), 1, Integer::sum);
                if (row.merchantKey() != null && !row.merchantKey().isEmpty()) {
                    merchants.computeIfAbsent(row.merchantKey(), key -> new MerchantTally()).add(row);
                }
            }
            if (signedSpending.signum() != 0 || row.type() == TransactionType.INCOME) {
                AccountTally tally = accounts.computeIfAbsent(row.accountId(), id -> new AccountTally(row.accountId(), row.accountId() == null ? "No account" : row.accountName()));
                tally.spending = tally.spending.add(signedSpending);
                if (row.type() == TransactionType.INCOME) tally.income = tally.income.add(row.amount());
            }
        }

        List<Bucket> series = new ArrayList<>();
        for (int i = 0; i < bounds.size(); i++) series.add(new Bucket(bounds.get(i)[0], bounds.get(i)[1], bucketIncome[i], bucketSpending[i]));

        BigDecimal gross = current.grossSpending();
        List<CategoryAmount> categories = categoryAmounts.entrySet().stream()
            .map(e -> new CategoryAmount(e.getKey(), e.getValue(), categoryCounts.get(e.getKey()),
                gross.signum() == 0 ? BigDecimal.ZERO : e.getValue().divide(gross, 4, RoundingMode.HALF_UP),
                previousCategoryAmounts.getOrDefault(e.getKey(), BigDecimal.ZERO)))
            .sorted(Comparator.comparing(CategoryAmount::amount).reversed())
            .toList();

        List<MerchantAmount> topMerchants = merchants.values().stream()
            .sorted(Comparator.comparing((MerchantTally m) -> m.amount).reversed())
            .limit(TOP_MERCHANTS)
            .map(m -> new MerchantAmount(m.name, m.amount, m.count))
            .toList();

        List<AccountAmount> accountAmounts = accounts.values().stream()
            .sorted(Comparator.comparing((AccountTally a) -> a.spending).reversed())
            .map(a -> new AccountAmount(a.id, a.name, a.spending, a.income))
            .toList();

        return new AnalyticsSummary(new Range(from, to), new Range(previousFrom, previousTo), granularity, current.snapshot(), previous.snapshot(),
            series, categories, topMerchants, accountAmounts, reviewCount);
    }

    /** Contribution to spending: expenses, cash, and investment debits add; credits and refunds subtract. */
    private static BigDecimal signedSpending(AnalyticsRow row) {
        return switch (row.type()) {
            case EXPENSE, CASH_WITHDRAWAL -> row.amount();
            case REFUND -> row.amount().negate();
            case INVESTMENT -> row.direction() == TransactionDirection.CREDIT ? row.amount().negate() : row.amount();
            default -> BigDecimal.ZERO;
        };
    }

    static Granularity granularity(LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days <= 31) return Granularity.DAY;
        if (days <= 184) return Granularity.WEEK;
        return Granularity.MONTH;
    }

    /** Consecutive [start, end] buckets covering exactly [from, to]; the first and last may be partial. */
    static List<LocalDate[]> buckets(LocalDate from, LocalDate to, Granularity granularity) {
        List<LocalDate[]> result = new ArrayList<>();
        LocalDate start = switch (granularity) {
            case DAY -> from;
            case WEEK -> from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> from.withDayOfMonth(1);
        };
        while (!start.isAfter(to)) {
            LocalDate next = switch (granularity) {
                case DAY -> start.plusDays(1);
                case WEEK -> start.plusWeeks(1);
                case MONTH -> start.plusMonths(1);
            };
            LocalDate bucketStart = start.isBefore(from) ? from : start;
            LocalDate bucketEnd = next.minusDays(1).isAfter(to) ? to : next.minusDays(1);
            result.add(new LocalDate[] {bucketStart, bucketEnd});
            start = next;
        }
        return result;
    }

    private static int bucketIndex(List<LocalDate[]> bounds, LocalDate date) {
        int low = 0, high = bounds.size() - 1;
        while (low < high) {
            int mid = (low + high + 1) / 2;
            if (bounds.get(mid)[0].isAfter(date)) high = mid - 1; else low = mid;
        }
        return low;
    }

    private static BigDecimal[] zeros(int size) {
        BigDecimal[] values = new BigDecimal[size];
        Arrays.fill(values, BigDecimal.ZERO);
        return values;
    }

    private static final class MerchantTally {
        String name;
        LocalDate latest;
        BigDecimal amount = BigDecimal.ZERO;
        int count;

        void add(AnalyticsRow row) {
            amount = amount.add(row.amount());
            count++;
            if (latest == null || !row.date().isBefore(latest)) { latest = row.date(); name = row.merchant(); }
        }
    }

    private static final class AccountTally {
        final UUID id;
        final String name;
        BigDecimal spending = BigDecimal.ZERO;
        BigDecimal income = BigDecimal.ZERO;

        AccountTally(UUID id, String name) { this.id = id; this.name = name; }
    }
}
