package com.flowmint.recurring;

import com.flowmint.transactions.Category;
import com.flowmint.transactions.PaymentChannel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Finds merchants charged on a steady cadence for similar amounts. Pure: no persistence, no clock. */
public final class RecurringDetector {
    static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.25");
    static final double CONSISTENT_SHARE = 0.75;
    private static final Set<Category> COMMITMENT_CATEGORIES = EnumSet.of(Category.SUBSCRIPTIONS, Category.LOANS_EMI, Category.INSURANCE);

    public enum Cadence {
        WEEKLY(5, 9, Period.ofWeeks(1), 3, 52, 12),
        MONTHLY(25, 35, Period.ofMonths(1), 7, 1, 1),
        QUARTERLY(80, 100, Period.ofMonths(3), 15, 1, 3),
        YEARLY(350, 380, Period.ofYears(1), 30, 1, 12);

        final int minDays, maxDays, graceDays;
        final Period period;
        /** Payments per month as an exact fraction, so a yearly ₹24,000 is ₹2,000 a month, not ₹1,999.99. */
        final int perMonthNumerator, perMonthDenominator;

        Cadence(int minDays, int maxDays, Period period, int graceDays, int perMonthNumerator, int perMonthDenominator) {
            this.minDays = minDays; this.maxDays = maxDays; this.period = period; this.graceDays = graceDays;
            this.perMonthNumerator = perMonthNumerator; this.perMonthDenominator = perMonthDenominator;
        }

        BigDecimal monthly(BigDecimal amount) {
            return amount.multiply(BigDecimal.valueOf(perMonthNumerator)).divide(BigDecimal.valueOf(perMonthDenominator), 2, RoundingMode.HALF_UP);
        }

        boolean contains(long days) { return days >= minDays && days <= maxDays; }

        static Optional<Cadence> of(long medianGap) {
            return Arrays.stream(values()).filter(c -> c.contains(medianGap)).findFirst();
        }
    }

    public enum Status { ACTIVE, LAPSED }

    public record Payment(String merchantKey, String merchant, LocalDate date, BigDecimal amount, Category category, PaymentChannel channel, String accountName) {}

    public record Series(String key, String merchant, Category category, Cadence cadence, BigDecimal amount, BigDecimal lastAmount, LocalDate lastDate,
                         int occurrences, String accountName, LocalDate nextExpectedDate, BigDecimal monthlyEquivalent, Status status, boolean dismissed) {
        public Series dismissed(boolean value) {
            return new Series(key, merchant, category, cadence, amount, lastAmount, lastDate, occurrences, accountName, nextExpectedDate, monthlyEquivalent, status, value);
        }
    }

    private RecurringDetector() {}

    public static List<Series> detect(List<Payment> payments, LocalDate today) {
        Map<String, List<Payment>> byMerchant = new HashMap<>();
        for (Payment payment : payments) {
            if (payment.merchantKey() == null || payment.merchantKey().isEmpty()) continue;
            byMerchant.computeIfAbsent(payment.merchantKey(), key -> new ArrayList<>()).add(payment);
        }
        List<Series> result = new ArrayList<>();
        for (List<Payment> group : byMerchant.values()) seriesOf(group, today).ifPresent(result::add);
        result.sort(Comparator.comparing(Series::nextExpectedDate).thenComparing(Series::merchant));
        return result;
    }

    static Optional<Series> seriesOf(List<Payment> group, LocalDate today) {
        if (group.size() < 2) return Optional.empty();
        List<Payment> sorted = group.stream().sorted(Comparator.comparing(Payment::date)).toList();
        Payment last = sorted.get(sorted.size() - 1);
        boolean commitment = sorted.stream().anyMatch(p -> p.channel() == PaymentChannel.AUTOPAY || COMMITMENT_CATEGORIES.contains(p.category()));
        if (sorted.size() < (commitment ? 2 : 3)) return Optional.empty();

        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < sorted.size(); i++) gaps.add(ChronoUnit.DAYS.between(sorted.get(i - 1).date(), sorted.get(i).date()));
        Optional<Cadence> cadence = Cadence.of(Math.round(median(gaps.stream().map(BigDecimal::valueOf).toList()).doubleValue()));
        if (cadence.isEmpty()) return Optional.empty();
        long inBand = gaps.stream().filter(cadence.get()::contains).count();
        if (inBand < CONSISTENT_SHARE * gaps.size()) return Optional.empty();

        BigDecimal typical = median(sorted.stream().map(Payment::amount).toList());
        BigDecimal tolerance = typical.multiply(AMOUNT_TOLERANCE);
        long similar = sorted.stream().filter(p -> p.amount().subtract(typical).abs().compareTo(tolerance) <= 0).count();
        if (similar < CONSISTENT_SHARE * sorted.size()) return Optional.empty();

        LocalDate next = last.date().plus(cadence.get().period);
        Status status = today.isAfter(next.plusDays(cadence.get().graceDays)) ? Status.LAPSED : Status.ACTIVE;
        BigDecimal amount = typical.setScale(2, RoundingMode.HALF_UP);
        return Optional.of(new Series(last.merchantKey(), last.merchant(), last.category(), cadence.get(), amount, last.amount(), last.date(), sorted.size(),
            last.accountName(), next, cadence.get().monthly(amount), status, false));
    }

    static BigDecimal median(List<BigDecimal> values) {
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) return sorted.get(middle);
        return sorted.get(middle - 1).add(sorted.get(middle)).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }
}
