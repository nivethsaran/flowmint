package com.flowmint.transactions;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowmint.common.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * A partial update. Parsed from raw JSON so that an absent field (leave unchanged) differs from an explicit null
 * (clear it, for {@code accountId} and {@code notes}).
 */
public record TransactionPatch(
    Optional<String> merchant,
    Optional<BigDecimal> amount,
    Optional<TransactionType> type,
    Optional<TransactionDirection> direction,
    Optional<Category> category,
    boolean accountIdPresent, UUID accountId,
    Optional<LocalDate> date,
    boolean notesPresent, String notes,
    boolean reviewed,
    boolean applyToMerchant
) {
    static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000000");
    private static final Set<String> FIELDS = Set.of("merchant", "amount", "type", "direction", "category", "accountId", "date", "notes", "reviewed", "applyToMerchant");

    public boolean changesFields() {
        return merchant.isPresent() || amount.isPresent() || type.isPresent() || direction.isPresent() || category.isPresent()
            || accountIdPresent || date.isPresent() || notesPresent;
    }

    public static TransactionPatch parse(JsonNode body) {
        if (body == null || !body.isObject()) throw ApiException.validation("Body must be a JSON object");
        Map<String, String> errors = new LinkedHashMap<>();
        body.fieldNames().forEachRemaining(name -> { if (!FIELDS.contains(name)) errors.put(name, "Unknown field"); });

        Optional<String> merchant = text(body, "merchant", errors).map(String::strip);
        merchant.ifPresent(m -> { if (m.isEmpty() || m.length() > 200) errors.put("merchant", "Must be 1–200 characters"); });

        Optional<BigDecimal> amount = Optional.empty();
        if (body.has("amount")) {
            JsonNode node = body.get("amount");
            if (!node.isNumber()) errors.put("amount", "Must be a number");
            else if (node.decimalValue().signum() <= 0 || node.decimalValue().compareTo(MAX_AMOUNT) > 0) errors.put("amount", "Must be greater than 0");
            else amount = Optional.of(node.decimalValue());
        }

        Optional<TransactionType> type = enumValue(body, "type", TransactionType.class, errors);
        type.ifPresent(t -> { if (t == TransactionType.UNKNOWN) errors.put("type", "Choose a specific type"); });
        Optional<TransactionDirection> direction = enumValue(body, "direction", TransactionDirection.class, errors);
        Optional<Category> category = enumValue(body, "category", Category.class, errors);

        UUID accountId = null;
        boolean accountIdPresent = body.has("accountId");
        if (accountIdPresent && !body.get("accountId").isNull()) {
            try { accountId = UUID.fromString(body.get("accountId").asText()); } catch (IllegalArgumentException e) { errors.put("accountId", "Must be an account id"); }
        }

        Optional<LocalDate> date = Optional.empty();
        Optional<String> dateText = text(body, "date", errors);
        if (dateText.isPresent()) {
            try { date = Optional.of(LocalDate.parse(dateText.get())); } catch (DateTimeParseException e) { errors.put("date", "Must be YYYY-MM-DD"); }
        }

        boolean notesPresent = body.has("notes");
        String notes = null;
        if (notesPresent && !body.get("notes").isNull()) {
            if (!body.get("notes").isTextual()) errors.put("notes", "Must be text");
            else {
                notes = body.get("notes").asText().strip();
                if (notes.length() > 1000) errors.put("notes", "Must be at most 1000 characters");
                if (notes.isEmpty()) notes = null;
            }
        }

        boolean reviewed = bool(body, "reviewed", errors);
        boolean applyToMerchant = bool(body, "applyToMerchant", errors);
        if (applyToMerchant && category.isEmpty()) errors.put("applyToMerchant", "Requires a category");

        if (!errors.isEmpty()) throw new ApiException(400, "VALIDATION_ERROR", "Request validation failed", errors);
        return new TransactionPatch(merchant, amount, type, direction, category, accountIdPresent, accountId, date, notesPresent, notes, reviewed, applyToMerchant);
    }

    private static Optional<String> text(JsonNode body, String field, Map<String, String> errors) {
        if (!body.has(field)) return Optional.empty();
        JsonNode node = body.get(field);
        if (!node.isTextual()) { errors.put(field, "Must be text"); return Optional.empty(); }
        return Optional.of(node.asText());
    }

    private static <E extends Enum<E>> Optional<E> enumValue(JsonNode body, String field, Class<E> type, Map<String, String> errors) {
        Optional<String> value = text(body, field, errors);
        if (value.isEmpty()) return Optional.empty();
        try { return Optional.of(Enum.valueOf(type, value.get())); } catch (IllegalArgumentException e) { errors.put(field, "Unknown value"); return Optional.empty(); }
    }

    private static boolean bool(JsonNode body, String field, Map<String, String> errors) {
        if (!body.has(field)) return false;
        if (!body.get(field).isBoolean()) { errors.put(field, "Must be true or false"); return false; }
        return body.get(field).asBoolean();
    }
}
