package com.flowmint.transactions;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Stores review reasons as a comma-separated list of names; unknown names from older versions are dropped. */
@Converter
public class ReviewReasonsConverter implements AttributeConverter<Set<ReviewReason>, String> {
    @Override
    public String convertToDatabaseColumn(Set<ReviewReason> reasons) {
        if (reasons == null || reasons.isEmpty()) return null;
        return reasons.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    @Override
    public Set<ReviewReason> convertToEntityAttribute(String value) {
        EnumSet<ReviewReason> reasons = EnumSet.noneOf(ReviewReason.class);
        if (value == null || value.isBlank()) return reasons;
        Arrays.stream(value.split(",")).map(String::trim).forEach(name -> {
            try { reasons.add(ReviewReason.valueOf(name)); } catch (IllegalArgumentException ignored) { }
        });
        return reasons;
    }
}
