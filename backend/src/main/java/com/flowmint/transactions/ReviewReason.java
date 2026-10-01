package com.flowmint.transactions;

/** Why a transaction needs the operator's attention. */
public enum ReviewReason {
    LOW_CONFIDENCE("The model was not confident"),
    UNKNOWN_MERCHANT("Merchant could not be identified"),
    UNCATEGORIZED("No category fit"),
    UNKNOWN_TYPE("Transaction type is unclear"),
    DIRECTION_MISMATCH("Debit/credit contradicted the type"),
    OTP_WORDING("Message mentions an OTP"),
    RULES_FALLBACK("Extracted by rules because the LLM was unavailable");

    private final String description;

    ReviewReason(String description) { this.description = description; }

    public String description() { return description; }
}
