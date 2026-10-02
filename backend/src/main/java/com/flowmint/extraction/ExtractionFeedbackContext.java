package com.flowmint.extraction;

import com.flowmint.events.EventFeedback;
import com.flowmint.events.RawEvent;
import com.flowmint.events.RawEventRepository;
import com.flowmint.transactions.Transaction;
import com.flowmint.transactions.TransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Turns recent inbox corrections into a small set of in-context examples for future extraction calls. */
@Component
public class ExtractionFeedbackContext {
    private static final int MAX_EXAMPLES = 4;
    private static final Pattern AMOUNT = Pattern.compile("(?i)(INR|Rs\\.?|₹)\\s*[.:]?\\s*[0-9][0-9,]*(?:\\.[0-9]{1,2})?");
    private static final Pattern MASKED_ACCOUNT = Pattern.compile("(?i)(?:XX|X{2,}|\\*{2,})\\s*\\d{2,}");
    private static final Pattern ACCOUNT_SUFFIX = Pattern.compile("(?i)\\b(card|account|acct|a/c)\\s*(?:no\\.?\\s*)?(?:ending\\s*)?\\d{2,}\\b");
    private static final Pattern REFERENCE = Pattern.compile("(?i)\\b(id|ref(?:erence)?|txn)\\s*(?:no\\.?\\s*)?[:#-]?\\s*[A-Za-z0-9@_-]{5,}");
    private static final Pattern URL = Pattern.compile("(?i)https?://\\S+");
    private static final Pattern LONG_NUMBER = Pattern.compile("\\b\\d{5,}\\b");
    private static final Pattern LONG_HEX = Pattern.compile("(?i)\\b[0-9a-f]{16,}\\b");

    private final RawEventRepository events;
    private final TransactionRepository transactions;

    public ExtractionFeedbackContext(RawEventRepository events, TransactionRepository transactions) {
        this.events = events;
        this.transactions = transactions;
    }

    public String forEvent(RawEvent current) {
        List<String> lines = new ArrayList<>();
        if (current.getUserFeedback() == EventFeedback.FALSE_NEGATIVE) {
            lines.add("Current-message correction: the user confirmed this message reports a completed transaction. Classify it as TRANSACTION and extract only details supported by this message.");
        } else if (current.getUserFeedback() == EventFeedback.FALSE_POSITIVE) {
            lines.add("Current-message correction: the user marked this message as a false positive. Do not create a transaction from it.");
        }

        int examplesAdded = 0;
        for (RawEvent example : events.findRecentFeedback(current.getId(), PageRequest.of(0, MAX_EXAMPLES * 5))) {
            Optional<String> line = feedbackExample(example);
            if (line.isPresent()) {
                lines.add(line.get());
                if (++examplesAdded == MAX_EXAMPLES) break;
            }
        }
        if (lines.isEmpty()) return "";
        return "Verified user feedback from the inbox:\n" + String.join("\n", lines);
    }

    private Optional<String> feedbackExample(RawEvent event) {
        String message = sanitize(event.searchableText());
        if (message.isBlank()) return Optional.empty();
        String result = switch (event.getUserFeedback()) {
            case FALSE_POSITIVE -> "not a completed transaction; do not create a transaction";
            case FALSE_NEGATIVE -> "TRANSACTION; user confirmed a completed money movement";
            case INCORRECT_TAG -> correctedTag(event);
        };
        return result == null ? Optional.empty() : Optional.of("Message pattern: \"" + message + "\" => " + result + ".");
    }

    private String correctedTag(RawEvent event) {
        Transaction transaction = transactions.findByExternalEventId(event.getExternalEventId()).orElse(null);
        if (transaction == null || !transaction.isUserEdited()) return null;
        return "TRANSACTION; corrected by user to type " + transaction.getType() + ", direction " + transaction.getDirection()
            + ", category " + transaction.getCategory();
    }

    private static String sanitize(String text) {
        String value = AMOUNT.matcher(text).replaceAll("$1 [amount]");
        value = MASKED_ACCOUNT.matcher(value).replaceAll("[account]");
        value = ACCOUNT_SUFFIX.matcher(value).replaceAll("$1 [account]");
        value = REFERENCE.matcher(value).replaceAll("$1 [reference]");
        value = URL.matcher(value).replaceAll("[link]");
        value = LONG_NUMBER.matcher(value).replaceAll("[number]");
        value = LONG_HEX.matcher(value).replaceAll("[reference]");
        value = value.replaceAll("\\s+", " ").strip();
        return value.length() <= 320 ? value : value.substring(0, 320) + "…";
    }
}
