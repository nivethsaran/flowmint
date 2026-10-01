package com.flowmint.recurring;

import com.flowmint.recurring.RecurringDetector.*;
import com.flowmint.transactions.Category;
import com.flowmint.transactions.PaymentChannel;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecurringDetectorTest {
    private static final LocalDate TODAY = LocalDate.parse("2026-10-15");

    private static Payment payment(String merchant, String date, String amount, Category category, PaymentChannel channel) {
        return new Payment(merchant.toLowerCase(), merchant, LocalDate.parse(date), new BigDecimal(amount), category, channel, "HDFC ••1234");
    }

    private static List<Payment> monthly(String merchant, String amount, String... dates) {
        List<Payment> payments = new ArrayList<>();
        for (String date : dates) payments.add(payment(merchant, date, amount, Category.SHOPPING, PaymentChannel.UPI));
        return payments;
    }

    @Test
    void detectsAMonthlySeries() {
        List<Series> series = RecurringDetector.detect(monthly("Gym", "1500", "2026-07-03", "2026-08-02", "2026-09-03", "2026-10-03"), TODAY);

        assertThat(series).hasSize(1);
        Series gym = series.get(0);
        assertThat(gym.cadence()).isEqualTo(Cadence.MONTHLY);
        assertThat(gym.nextExpectedDate()).isEqualTo(LocalDate.parse("2026-11-03"));
        assertThat(gym.monthlyEquivalent()).isEqualByComparingTo("1500");
        assertThat(gym.status()).isEqualTo(Status.ACTIVE);
        assertThat(gym.occurrences()).isEqualTo(4);
    }

    @Test
    void twoPaymentsAreEnoughForSubscriptionsAndAutopay() {
        List<Payment> netflix = List.of(payment("Netflix", "2026-08-05", "649", Category.SUBSCRIPTIONS, PaymentChannel.CREDIT_CARD), payment("Netflix", "2026-09-05", "649", Category.SUBSCRIPTIONS, PaymentChannel.CREDIT_CARD));
        List<Payment> sip = List.of(payment("Zerodha", "2026-08-10", "5000", Category.INVESTMENTS, PaymentChannel.AUTOPAY), payment("Zerodha", "2026-09-10", "5000", Category.INVESTMENTS, PaymentChannel.AUTOPAY));

        assertThat(RecurringDetector.detect(netflix, TODAY)).hasSize(1);
        assertThat(RecurringDetector.detect(sip, TODAY)).hasSize(1);
        assertThat(RecurringDetector.detect(monthly("Gym", "1500", "2026-08-03", "2026-09-03"), TODAY)).isEmpty();
    }

    @Test
    void irregularSpendingIsNotRecurring() {
        assertThat(RecurringDetector.detect(monthly("Swiggy", "450", "2026-09-01", "2026-09-03", "2026-09-04", "2026-09-20", "2026-10-02"), TODAY)).isEmpty();
    }

    @Test
    void amountsMustBeSimilar() {
        List<Payment> varying = List.of(
            payment("Amazon", "2026-07-01", "200", Category.SHOPPING, PaymentChannel.UPI),
            payment("Amazon", "2026-08-01", "5000", Category.SHOPPING, PaymentChannel.UPI),
            payment("Amazon", "2026-09-01", "900", Category.SHOPPING, PaymentChannel.UPI),
            payment("Amazon", "2026-10-01", "12000", Category.SHOPPING, PaymentChannel.UPI));

        assertThat(RecurringDetector.detect(varying, TODAY)).isEmpty();
    }

    @Test
    void lapsedWhenWellPastTheExpectedDate() {
        Series series = RecurringDetector.detect(monthly("Gym", "1500", "2026-05-03", "2026-06-03", "2026-07-03"), TODAY).get(0);

        assertThat(series.status()).isEqualTo(Status.LAPSED);
    }

    @Test
    void yearlyAndWeeklyCadences() {
        List<Payment> insurance = List.of(payment("LIC", "2024-10-01", "24000", Category.INSURANCE, PaymentChannel.NETBANKING), payment("LIC", "2025-10-01", "24000", Category.INSURANCE, PaymentChannel.NETBANKING));
        Series yearly = RecurringDetector.detect(insurance, TODAY).get(0);
        assertThat(yearly.cadence()).isEqualTo(Cadence.YEARLY);
        assertThat(yearly.monthlyEquivalent()).isEqualByComparingTo("2000.00");

        assertThat(RecurringDetector.detect(monthly("Milk", "60", "2026-09-17", "2026-09-24", "2026-10-01", "2026-10-08"), TODAY).get(0).cadence()).isEqualTo(Cadence.WEEKLY);
    }

    @Test
    void lapsesOnlyAfterTheGracePeriod() {
        List<Payment> netflix = List.of(payment("Netflix", "2026-08-05", "649", Category.SUBSCRIPTIONS, PaymentChannel.CREDIT_CARD), payment("Netflix", "2026-09-05", "649", Category.SUBSCRIPTIONS, PaymentChannel.CREDIT_CARD));

        assertThat(RecurringDetector.detect(netflix, LocalDate.parse("2026-10-12")).get(0).status()).isEqualTo(Status.ACTIVE);
        assertThat(RecurringDetector.detect(netflix, LocalDate.parse("2026-10-15")).get(0).status()).isEqualTo(Status.LAPSED);
    }

    @Test
    void medianOfEvenCountAveragesTheMiddle() {
        assertThat(RecurringDetector.median(List.of(new BigDecimal("10"), new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("40")))).isEqualByComparingTo("25");
    }
}
