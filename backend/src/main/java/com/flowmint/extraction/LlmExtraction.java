package com.flowmint.extraction;

import com.flowmint.accounts.AccountType;
import com.flowmint.events.MessageKind;
import com.flowmint.transactions.Category;
import com.flowmint.transactions.PaymentChannel;
import dev.langchain4j.model.output.structured.Description;

/**
 * Structured output requested from the LLM. Kept flat with sentinel values ("" / NONE / 0) instead of optional
 * nested objects: small models fill flat schemas more reliably, and strict JSON schema requires every field.
 * Field order matters: the model commits to {@code kind} and {@code reason} before the details.
 */
public record LlmExtraction(
    @Description("What the message is. Only TRANSACTION when money has already moved.") MessageKind kind,
    @Description("At most 12 words explaining the kind. Never include OTP codes or full account numbers.") String reason,
    @Description("Transaction type, or NONE when kind is not TRANSACTION") Type type,
    @Description("DEBIT if money left the account or was charged to the card, CREDIT if money came in, NONE when kind is not TRANSACTION") Direction direction,
    @Description("Transaction amount exactly as written in the message, without commas. Not the balance or credit limit. 0 when kind is not TRANSACTION.") double amount,
    @Description("ISO currency code, e.g. INR") String currency,
    @Description("Short clean payee or payer name, e.g. Amazon, Swiggy, Rahul Sharma. Empty string if unknown.") String merchant,
    @Description("Best-fitting category") Category category,
    @Description("Where the money moved from or to") AccountType accountType,
    @Description("Payment rail used") PaymentChannel channel,
    @Description("Last 4 digits of the account or card number shown in the message, e.g. XX1234 -> 1234. Empty string if none.") String accountLast4,
    @Description("Bank or card issuer name, e.g. HDFC Bank. Empty string if unknown.") String institution,
    @Description("Transaction date as YYYY-MM-DD if the message states one, else empty string") String transactionDate,
    @Description("Bank account available balance after the transaction if stated, digits only, else empty string. Never a credit card limit.") String availableBalance,
    @Description("How sure you are, 0.0 to 1.0") double confidence
) {
    public enum Type { EXPENSE, INCOME, REFUND, TRANSFER, CASH_WITHDRAWAL, INVESTMENT, NONE }

    public enum Direction { DEBIT, CREDIT, NONE }
}
