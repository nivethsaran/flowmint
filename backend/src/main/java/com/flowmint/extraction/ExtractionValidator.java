package com.flowmint.extraction;

import com.flowmint.accounts.AccountType;
import com.flowmint.events.MessageKind;
import com.flowmint.events.RawEvent;
import com.flowmint.transactions.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.Locale;

/** Turns raw LLM output into a trusted result, or rejects it. The LLM is never trusted on numbers it could invent. */
@Component
public class ExtractionValidator {
    private static final int MAX_MERCHANT = 200;
    private static final int MAX_INSTITUTION = 120;
    private final BigDecimal reviewThreshold;

    public ExtractionValidator(LlmProperties properties) {
        this.reviewThreshold = BigDecimal.valueOf(properties.reviewConfidenceThreshold());
    }

    public ExtractionResult validate(LlmExtraction extraction, RawEvent event) {
        String reason = clean(extraction.reason());
        if (extraction.kind() != MessageKind.TRANSACTION) return ExtractionResult.ignored(extraction.kind(), reason);

        String text = event.searchableText();
        BigDecimal amount = BigDecimal.valueOf(extraction.amount());
        if (amount.signum() <= 0 || !MessageText.containsNumber(text, amount)) throw new InvalidExtractionException("amount_not_in_message");

        EnumSet<ReviewReason> reasons = EnumSet.noneOf(ReviewReason.class);
        TransactionType type = toType(extraction.type());
        if (type == TransactionType.UNKNOWN) reasons.add(ReviewReason.UNKNOWN_TYPE);

        TransactionDirection stated = toDirection(extraction.direction());
        TransactionDirection direction = type.impliedDirection();
        if (direction == null) {
            direction = stated != null ? stated : TransactionDirection.DEBIT;
            if (stated == null) reasons.add(ReviewReason.DIRECTION_MISMATCH);
        } else if (stated != null && stated != direction) {
            reasons.add(ReviewReason.DIRECTION_MISMATCH);
        }

        String last4 = lastFourDigits(extraction.accountLast4(), text);
        AccountType accountType = extraction.accountType() == null ? AccountType.UNKNOWN : extraction.accountType();

        LocalDate eventDate = event.getEventTimestamp().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate date = parseDate(extraction.transactionDate());
        if (!MessageText.plausibleDate(date, eventDate)) date = eventDate;

        BigDecimal balance = MessageText.parseAmount(extraction.availableBalance());
        if (balance == null || !MessageText.containsNumber(text, balance) || !(accountType == AccountType.BANK_ACCOUNT || accountType == AccountType.WALLET)) balance = null;

        String currency = extraction.currency() == null ? "" : extraction.currency().trim().toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) currency = "INR";

        String merchant = truncate(clean(extraction.merchant()), MAX_MERCHANT);
        if (merchant.isEmpty()) { merchant = TransactionDraft.UNKNOWN_MERCHANT; reasons.add(ReviewReason.UNKNOWN_MERCHANT); }

        Category category = extraction.category() == null ? Category.OTHER : extraction.category();
        if (category == Category.OTHER) reasons.add(ReviewReason.UNCATEGORIZED);

        BigDecimal confidence = BigDecimal.valueOf(Math.max(0, Math.min(1, extraction.confidence()))).setScale(4, RoundingMode.HALF_UP);
        if (confidence.compareTo(reviewThreshold) < 0) reasons.add(ReviewReason.LOW_CONFIDENCE);
        if (MessageText.mentionsOtp(text)) reasons.add(ReviewReason.OTP_WORDING);

        PaymentChannel channel = extraction.channel() == null ? PaymentChannel.OTHER : extraction.channel();
        String institution = truncate(clean(extraction.institution()), MAX_INSTITUTION);

        return ExtractionResult.transaction(reason, new TransactionDraft(type, direction, amount, currency, merchant, category, accountType, channel,
            last4, institution.isEmpty() ? null : institution, date, balance, confidence, reasons, ExtractionMethod.LLM));
    }

    /** Keeps the last four digits only if they actually appear in the message. */
    static String lastFourDigits(String value, String text) {
        if (value == null) return null;
        String digits = value.replaceAll("\\D", "");
        if (digits.length() < 4) return null;
        String last4 = digits.substring(digits.length() - 4);
        return text.contains(last4) ? last4 : null;
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDate.parse(value.trim()); } catch (DateTimeParseException e) { return null; }
    }

    private static TransactionType toType(LlmExtraction.Type type) {
        if (type == null || type == LlmExtraction.Type.NONE) return TransactionType.UNKNOWN;
        return TransactionType.valueOf(type.name());
    }

    private static TransactionDirection toDirection(LlmExtraction.Direction direction) {
        if (direction == null || direction == LlmExtraction.Direction.NONE) return null;
        return TransactionDirection.valueOf(direction.name());
    }

    private static String clean(String value) { return value == null ? "" : value.strip(); }

    private static String truncate(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
}
