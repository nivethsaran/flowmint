package com.flowmint.extraction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic checks on message text shared by the validator and the rule-based fallback. */
final class MessageText {
    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");
    private static final Pattern OTP = Pattern.compile("\\b(otp|one[\\s-]?time[\\s-]?password|verification code)\\b", Pattern.CASE_INSENSITIVE);

    private MessageText() {}

    /** Every number in the text, with thousands separators (including Indian 1,29,999 grouping) removed. */
    static Set<BigDecimal> numbers(String text) {
        Set<BigDecimal> numbers = new HashSet<>();
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            String digits = matcher.group().replace(",", "");
            try { numbers.add(new BigDecimal(digits).stripTrailingZeros()); } catch (NumberFormatException ignored) { }
        }
        return numbers;
    }

    static boolean containsNumber(String text, BigDecimal value) {
        return value != null && numbers(text).contains(value.stripTrailingZeros());
    }

    static boolean mentionsOtp(String text) {
        return OTP.matcher(text).find();
    }

    static BigDecimal parseAmount(String value) {
        if (value == null) return null;
        String cleaned = value.replaceAll("[^0-9.]", "");
        if (cleaned.isEmpty() || cleaned.chars().filter(c -> c == '.').count() > 1) return null;
        try { return new BigDecimal(cleaned); } catch (NumberFormatException e) { return null; }
    }

    /** True when {@code date} is no more than one day after and no more than 60 days before {@code eventDate}. */
    static boolean plausibleDate(LocalDate date, LocalDate eventDate) {
        return date != null && !date.isAfter(eventDate.plusDays(1)) && !date.isBefore(eventDate.minusDays(60));
    }
}
