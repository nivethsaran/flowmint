package com.flowmint.extraction;

import com.flowmint.accounts.AccountType;
import com.flowmint.events.MessageKind;
import com.flowmint.events.RawEvent;
import com.flowmint.transactions.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Last-resort extraction used only after every LLM attempt failed. Deliberately conservative:
 * everything it produces is flagged for review.
 */
@Component
public class RuleBasedExtractor {
    private static final Pattern AMOUNT = Pattern.compile("(?:INR|RS\\.?|₹)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEBIT = Pattern.compile("\\b(debited|spent|paid|sent|purchase|withdrawn|charged)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCHEDULED_DEBIT = Pattern.compile("\\b(?:will be debited|scheduled to be debited|will be charged)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXECUTED_MANDATE = Pattern.compile("\\bmandate\\b.{0,240}\\bsuccessfully executed\\b", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CREDIT = Pattern.compile("\\b(credited|received|refund(?:ed)?|deposited)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LAST4 = Pattern.compile("(?:[x*]{2,}|ending\\s*(?:in|with)?\\s*|no\\.?\\s*)(\\d{4})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MERCHANT = Pattern.compile("\\b(?:at|to|towards|from|against)\\s+(?:vpa\\s+)?([A-Za-z][A-Za-z0-9&.'@ -]{1,40}?)(?=\\s+(?:on|via|ref|upi|for|using|thru|through)\\b|[.,;(]|$)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern FAILED = Pattern.compile("\\b(failed|declined|unsuccessful|could not be processed|will not be debited)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern REQUEST = Pattern.compile("\\b(has requested|collect request|requested (?:money|payment|rs|inr))\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE = Pattern.compile("\\b(\\d{1,2})[-/](\\d{1,2})[-/](\\d{2}|\\d{4})\\b");
    private static final BigDecimal CONFIDENCE = new BigDecimal("0.3000");

    /** Returns null when the message cannot be interpreted at all. */
    public ExtractionResult extract(RawEvent event) {
        String text = event.searchableText();
        if (MessageText.mentionsOtp(text)) return ExtractionResult.ignored(MessageKind.OTP, "OTP keyword found (rule-based fallback)");
        // A failed payment or a request moved no money; recording it would invent spending.
        if (FAILED.matcher(text).find()) return ExtractionResult.ignored(MessageKind.FAILED_TRANSACTION, "Payment failed or was declined (rule-based fallback)");
        if (REQUEST.matcher(text).find()) return ExtractionResult.ignored(MessageKind.PAYMENT_REQUEST, "Payment request, not a payment (rule-based fallback)");
        // Future tense is not evidence that a debit has already happened.
        if (SCHEDULED_DEBIT.matcher(text).find()) return ExtractionResult.ignored(MessageKind.BILL_REMINDER, "Debit is scheduled for a future date (rule-based fallback)");

        Matcher amountMatcher = AMOUNT.matcher(text);
        if (!amountMatcher.find()) return null;
        BigDecimal amount = new BigDecimal(amountMatcher.group(1).replace(",", ""));
        if (amount.signum() <= 0) return null;

        boolean executedMandate = EXECUTED_MANDATE.matcher(text).find();
        boolean debit = DEBIT.matcher(text).find() || executedMandate;
        boolean credit = CREDIT.matcher(text).find();
        if (debit == credit) return null; // neither, or ambiguous

        String lower = text.toLowerCase(Locale.ROOT);
        TransactionType type;
        Category category;
        if (debit) {
            boolean atm = lower.contains("atm") && lower.contains("withdrawn");
            type = atm ? TransactionType.CASH_WITHDRAWAL : TransactionType.EXPENSE;
            category = atm ? Category.CASH : Category.OTHER;
        } else {
            boolean refund = lower.contains("refund");
            type = refund ? TransactionType.REFUND : TransactionType.INCOME;
            category = refund ? Category.REFUNDS : Category.OTHER_INCOME;
        }

        LocalDate eventDate = event.getEventTimestamp().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate date = parseDate(text);
        if (!MessageText.plausibleDate(date, eventDate)) date = eventDate;

        Matcher last4Matcher = LAST4.matcher(text);
        String last4 = last4Matcher.find() ? last4Matcher.group(1) : null;

        TransactionDraft draft = new TransactionDraft(type, type.impliedDirection(), amount, "INR", merchant(text), category, accountType(lower), channel(lower),
            last4, null, date, null, CONFIDENCE, EnumSet.of(ReviewReason.RULES_FALLBACK), ExtractionMethod.RULES);
        return ExtractionResult.transaction("Extracted by rule-based fallback after LLM attempts failed", draft);
    }

    private static String merchant(String text) {
        Matcher matcher = MERCHANT.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group(1).strip();
            if (candidate.contains("@")) candidate = candidate.substring(0, candidate.indexOf('@'));
            String lower = candidate.toLowerCase(Locale.ROOT);
            if (candidate.length() < 2 || lower.startsWith("a/c") || lower.startsWith("your") || lower.startsWith("acct") || lower.matches(".*\\d{4}.*")) continue;
            return candidate.substring(0, 1).toUpperCase(Locale.ROOT) + candidate.substring(1);
        }
        return TransactionDraft.UNKNOWN_MERCHANT;
    }

    private static AccountType accountType(String lower) {
        if (lower.contains("credit card")) return AccountType.CREDIT_CARD;
        if (lower.contains("wallet")) return AccountType.WALLET;
        if (lower.contains("a/c") || lower.contains("acct") || lower.contains("account") || lower.contains("debit card")) return AccountType.BANK_ACCOUNT;
        return AccountType.UNKNOWN;
    }

    private static PaymentChannel channel(String lower) {
        if (lower.contains("upi") || lower.contains("vpa")) return PaymentChannel.UPI;
        if (lower.contains("atm")) return PaymentChannel.ATM;
        if (lower.contains("credit card")) return PaymentChannel.CREDIT_CARD;
        if (lower.contains("debit card")) return PaymentChannel.DEBIT_CARD;
        if (lower.matches("(?s).*\\b(neft|imps|rtgs|net ?banking)\\b.*")) return PaymentChannel.NETBANKING;
        if (lower.matches("(?s).*\\b(autopay|mandate|nach|ecs|standing instruction|auto-debit)\\b.*")) return PaymentChannel.AUTOPAY;
        return PaymentChannel.OTHER;
    }

    private static LocalDate parseDate(String text) {
        Matcher matcher = DATE.matcher(text);
        if (!matcher.find()) return null;
        try {
            int year = Integer.parseInt(matcher.group(3));
            if (year < 100) year += 2000;
            return LocalDate.of(year, Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(1)));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
