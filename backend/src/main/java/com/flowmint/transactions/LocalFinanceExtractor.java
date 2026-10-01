package com.flowmint.transactions;

import com.flowmint.events.RawEvent;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LocalFinanceExtractor implements LlmClient {
    private static final Pattern AMOUNT = Pattern.compile("(?:INR|RS\\.?|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE = Pattern.compile("(\\d{2})[-/](\\d{2})[-/](\\d{4})");

    @Override
    public FinanceExtraction extract(RawEvent event) {
        String body = event.getBody();
        Matcher amountMatcher = AMOUNT.matcher(body);
        BigDecimal amount = amountMatcher.find() ? new BigDecimal(amountMatcher.group(1).replace(",", "")) : BigDecimal.ZERO;
        Matcher dateMatcher = DATE.matcher(body);
        LocalDate date = dateMatcher.find() ? LocalDate.of(Integer.parseInt(dateMatcher.group(3)), Integer.parseInt(dateMatcher.group(2)), Integer.parseInt(dateMatcher.group(1))) : event.getEventTimestamp().atZone(java.time.ZoneOffset.UTC).toLocalDate();
        String merchant = merchantFrom(body);
        boolean expense = body.toLowerCase(Locale.ROOT).matches(".*(debited|spent|paid|purchase|withdrawn).* ".trim() + ".*");
        boolean confident = amount.signum() > 0 && !merchant.equals("Unknown merchant");
        return new FinanceExtraction(expense ? TransactionType.EXPENSE : TransactionType.UNKNOWN, amount, "INR", merchant, categoryFor(merchant), date, confident ? new BigDecimal("0.78") : new BigDecimal("0.25"), !confident);
    }

    private String merchantFrom(String body) {
        String upper = body.toUpperCase(Locale.ROOT);
        if (upper.contains("AMAZON") || upper.contains("AMZN")) return "Amazon";
        if (upper.contains("SWIGGY")) return "Swiggy";
        if (upper.contains("UBER")) return "Uber";
        if (upper.contains("NETFLIX")) return "Netflix";
        if (upper.contains("ELECTRICITY")) return "Electricity";
        return "Unknown merchant";
    }

    private String categoryFor(String merchant) {
        return switch (merchant) {
            case "Amazon" -> "Shopping";
            case "Swiggy" -> "Food";
            case "Uber" -> "Transport";
            case "Netflix" -> "Subscriptions";
            case "Electricity" -> "Bills & Utilities";
            default -> "Other";
        };
    }
}
