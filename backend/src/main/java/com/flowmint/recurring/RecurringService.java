package com.flowmint.recurring;

import com.flowmint.analytics.AnalyticsRow;
import com.flowmint.common.ApiException;
import com.flowmint.common.AppTime;
import com.flowmint.recurring.RecurringDetector.Payment;
import com.flowmint.recurring.RecurringDetector.Series;
import com.flowmint.transactions.TransactionDirection;
import com.flowmint.transactions.TransactionRepository;
import com.flowmint.transactions.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RecurringService {
    static final int LOOKBACK_DAYS = 400;
    private final TransactionRepository transactions;
    private final RecurringDismissalRepository dismissals;
    private final AppTime time;

    public RecurringService(TransactionRepository transactions, RecurringDismissalRepository dismissals, AppTime time) {
        this.transactions = transactions;
        this.dismissals = dismissals;
        this.time = time;
    }

    public record Overview(BigDecimal monthlyTotal, int activeCount, List<Series> items) {}

    @Transactional(readOnly = true)
    public Overview overview(boolean includeDismissed) {
        LocalDate today = time.today();
        List<Payment> payments = transactions.findAnalyticsRows(today.minusDays(LOOKBACK_DAYS), today).stream()
            .filter(RecurringService::isOutgoingCommitment)
            .map(r -> new Payment(r.merchantKey(), r.merchant(), r.date(), r.amount(), r.category(), r.channel(), r.accountName()))
            .toList();
        Set<String> dismissed = dismissals.findAll().stream().map(RecurringDismissal::getMerchantKey).collect(Collectors.toSet());
        List<Series> all = RecurringDetector.detect(payments, today).stream().map(s -> s.dismissed(dismissed.contains(s.key()))).toList();
        List<Series> active = all.stream().filter(s -> !s.dismissed() && s.status() == RecurringDetector.Status.ACTIVE).toList();
        BigDecimal monthlyTotal = active.stream().map(Series::monthlyEquivalent).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Overview(monthlyTotal, active.size(), includeDismissed ? all : all.stream().filter(i -> !i.dismissed()).toList());
    }

    @Transactional
    public void dismiss(String merchantKey) {
        validate(merchantKey);
        if (!dismissals.existsById(merchantKey)) dismissals.save(new RecurringDismissal(merchantKey));
    }

    @Transactional
    public void restore(String merchantKey) {
        validate(merchantKey);
        dismissals.deleteById(merchantKey);
    }

    private static boolean isOutgoingCommitment(AnalyticsRow row) {
        return row.direction() == TransactionDirection.DEBIT && (row.type() == TransactionType.EXPENSE || row.type() == TransactionType.INVESTMENT);
    }

    private static void validate(String merchantKey) {
        if (merchantKey == null || !merchantKey.matches("[a-z0-9]{1,200}")) throw ApiException.validation("merchantKey", "Invalid merchant key");
    }
}
