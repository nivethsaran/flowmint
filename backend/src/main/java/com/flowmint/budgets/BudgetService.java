package com.flowmint.budgets;

import com.flowmint.analytics.AnalyticsRow;
import com.flowmint.analytics.Totals;
import com.flowmint.budgets.BudgetMonth.*;
import com.flowmint.common.ApiException;
import com.flowmint.common.AppTime;
import com.flowmint.transactions.Category;
import com.flowmint.transactions.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.*;

@Service
public class BudgetService {
    public static final String TOTAL = "TOTAL";
    static final BigDecimal AT_RISK_PERCENT = new BigDecimal("80");
    /** Projections from the first few days of a month are too noisy to flag a budget. */
    static final int MIN_DAYS_FOR_PROJECTION = 7;
    private static final BigDecimal MAX_LIMIT = new BigDecimal("1000000000000");
    private final BudgetRepository budgets;
    private final TransactionRepository transactions;
    private final AppTime time;

    public BudgetService(BudgetRepository budgets, TransactionRepository transactions, AppTime time) {
        this.budgets = budgets;
        this.transactions = transactions;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public BudgetMonth month(YearMonth month) {
        List<AnalyticsRow> rows = transactions.findAnalyticsRows(month.atDay(1), month.atEndOfMonth());
        return evaluate(month, time.currentMonth().equals(month) ? time.today().getDayOfMonth() : month.isBefore(time.currentMonth()) ? month.lengthOfMonth() : 0,
            budgets.findAll(), rows);
    }

    static BudgetMonth evaluate(YearMonth month, int daysElapsed, List<Budget> configured, List<AnalyticsRow> rows) {
        Totals totals = new Totals();
        Map<Category, BigDecimal> spentByCategory = new EnumMap<>(Category.class);
        for (AnalyticsRow row : rows) {
            totals.add(row);
            if (row.isGrossSpending()) spentByCategory.merge(row.category(), row.amount(), BigDecimal::add);
        }

        int days = month.lengthOfMonth();
        BudgetStatus total = null;
        List<BudgetStatus> statuses = new ArrayList<>();
        Set<Category> budgeted = EnumSet.noneOf(Category.class);
        for (Budget budget : configured) {
            if (TOTAL.equals(budget.getCategory())) {
                total = status(TOTAL, budget.getMonthlyLimit(), totals.spending(), daysElapsed, days);
                continue;
            }
            Category category = Category.valueOf(budget.getCategory());
            budgeted.add(category);
            statuses.add(status(category.name(), budget.getMonthlyLimit(), spentByCategory.getOrDefault(category, BigDecimal.ZERO), daysElapsed, days));
        }
        statuses.sort(Comparator.comparing(BudgetStatus::percentUsed).reversed());

        List<Unbudgeted> unbudgeted = spentByCategory.entrySet().stream()
            .filter(e -> !budgeted.contains(e.getKey()) && e.getValue().signum() > 0)
            .sorted(Map.Entry.<Category, BigDecimal>comparingByValue().reversed())
            .map(e -> new Unbudgeted(e.getKey(), e.getValue()))
            .toList();
        return new BudgetMonth(month, days, daysElapsed, total, statuses, unbudgeted);
    }

    static BudgetStatus status(String category, BigDecimal limit, BigDecimal spent, int daysElapsed, int daysInMonth) {
        BigDecimal percent = spent.multiply(BigDecimal.valueOf(100)).divide(limit, 1, RoundingMode.HALF_UP);
        BigDecimal projected = daysElapsed == 0 ? BigDecimal.ZERO
            : spent.multiply(BigDecimal.valueOf(daysInMonth)).divide(BigDecimal.valueOf(daysElapsed), 2, RoundingMode.HALF_UP);
        Status status = spent.compareTo(limit) > 0 ? Status.OVER
            : percent.compareTo(AT_RISK_PERCENT) >= 0 || (daysElapsed >= MIN_DAYS_FOR_PROJECTION && projected.compareTo(limit) > 0) ? Status.AT_RISK
            : Status.ON_TRACK;
        return new BudgetStatus(category, limit, spent, limit.subtract(spent), percent, projected, status);
    }

    @Transactional
    public void put(String key, BigDecimal amount) {
        validateKey(key);
        if (amount == null || amount.signum() <= 0 || amount.compareTo(MAX_LIMIT) > 0) throw ApiException.validation("amount", "Must be greater than 0");
        budgets.findById(key).ifPresentOrElse(b -> b.setMonthlyLimit(amount), () -> budgets.save(new Budget(key, amount)));
    }

    @Transactional
    public void delete(String key) {
        validateKey(key);
        budgets.findById(key).ifPresent(budgets::delete);
    }

    private static void validateKey(String key) {
        if (TOTAL.equals(key)) return;
        Category category;
        try { category = Category.valueOf(key); } catch (IllegalArgumentException e) { throw ApiException.validation("category", "Unknown category"); }
        if (category.kind() != Category.Kind.SPENDING && category.kind() != Category.Kind.INVESTMENT) {
            throw ApiException.validation("category", category.label() + " is not a spending category");
        }
    }
}
