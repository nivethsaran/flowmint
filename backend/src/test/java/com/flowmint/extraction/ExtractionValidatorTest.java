package com.flowmint.extraction;

import com.flowmint.accounts.AccountType;
import com.flowmint.events.MessageKind;
import com.flowmint.events.RawEvent;
import com.flowmint.transactions.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

class ExtractionValidatorTest {
    private static final String UPI_DEBIT = "INR 450.00 debited from A/c XX1234 on 01-10-26 to VPA swiggy@icici (UPI Ref 4321). Avl Bal INR 12,000.00";
    private final ExtractionValidator validator = new ExtractionValidator(TestData.properties());

    private static LlmExtraction transaction(LlmExtraction.Type type, LlmExtraction.Direction direction, double amount, String merchant, Category category,
                                             AccountType accountType, String last4, String date, String balance, double confidence) {
        return new LlmExtraction(MessageKind.TRANSACTION, "Money debited via UPI", type, direction, amount, "INR", merchant, category, accountType,
            PaymentChannel.UPI, last4, "HDFC Bank", date, balance, confidence);
    }

    private static LlmExtraction upiDebit() {
        return transaction(LlmExtraction.Type.EXPENSE, LlmExtraction.Direction.DEBIT, 450.0, "Swiggy", Category.FOOD_DINING, AccountType.BANK_ACCOUNT, "1234", "2026-10-01", "12000.00", 0.95);
    }

    @Test
    void acceptsAValidTransaction() {
        ExtractionResult result = validator.validate(upiDebit(), TestData.event(UPI_DEBIT));

        assertThat(result.isTransaction()).isTrue();
        TransactionDraft draft = result.transaction();
        assertThat(draft.amount()).isEqualByComparingTo("450");
        assertThat(draft.direction()).isEqualTo(TransactionDirection.DEBIT);
        assertThat(draft.accountLast4()).isEqualTo("1234");
        assertThat(draft.date()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(draft.availableBalance()).isEqualByComparingTo("12000");
        assertThat(draft.requiresReview()).isFalse();
        assertThat(draft.method()).isEqualTo(ExtractionMethod.LLM);
    }

    @Test
    void nonTransactionKindsAreIgnored() {
        LlmExtraction otp = new LlmExtraction(MessageKind.OTP, "One time password for card payment", LlmExtraction.Type.NONE, LlmExtraction.Direction.NONE,
            0, "", "", Category.OTHER, AccountType.UNKNOWN, PaymentChannel.OTHER, "", "", "", "", 1.0);

        ExtractionResult result = validator.validate(otp, TestData.event("123456 is your OTP for txn of INR 2,499.00 at AMAZON. Do not share."));

        assertThat(result.isTransaction()).isFalse();
        assertThat(result.kind()).isEqualTo(MessageKind.OTP);
    }

    @Test
    void rejectsAnAmountThatIsNotInTheMessage() {
        LlmExtraction hallucinated = transaction(LlmExtraction.Type.EXPENSE, LlmExtraction.Direction.DEBIT, 1500, "Swiggy", Category.FOOD_DINING, AccountType.BANK_ACCOUNT, "1234", "", "", 0.9);

        assertThatThrownBy(() -> validator.validate(hallucinated, TestData.event(UPI_DEBIT)))
            .isInstanceOf(InvalidExtractionException.class)
            .hasMessage("amount_not_in_message");
    }

    @Test
    void matchesIndianDigitGroupingAndAmountsInTheTitle() {
        RawEvent event = TestData.event("₹1,29,999 paid to Croma", "Payment successful via UPI from A/c XX1234");
        LlmExtraction extraction = transaction(LlmExtraction.Type.EXPENSE, LlmExtraction.Direction.DEBIT, 129999, "Croma", Category.SHOPPING, AccountType.BANK_ACCOUNT, "1234", "", "", 0.9);

        assertThat(validator.validate(extraction, event).transaction().amount()).isEqualByComparingTo("129999");
    }

    @Test
    void discardsAccountDigitsThatAreNotInTheMessage() {
        LlmExtraction extraction = transaction(LlmExtraction.Type.EXPENSE, LlmExtraction.Direction.DEBIT, 450, "Swiggy", Category.FOOD_DINING, AccountType.BANK_ACCOUNT, "9999", "", "", 0.9);

        assertThat(validator.validate(extraction, TestData.event(UPI_DEBIT)).transaction().accountLast4()).isNull();
    }

    @Test
    void normalizesLongAccountNumbersToTheLastFourDigits() {
        assertThat(ExtractionValidator.lastFourDigits("XXXX1234", UPI_DEBIT)).isEqualTo("1234");
        assertThat(ExtractionValidator.lastFourDigits("12", UPI_DEBIT)).isNull();
    }

    @Test
    void fallsBackToTheEventDateWhenTheDateIsMissingOrImplausible() {
        LlmExtraction missing = transaction(LlmExtraction.Type.EXPENSE, LlmExtraction.Direction.DEBIT, 450, "Swiggy", Category.FOOD_DINING, AccountType.BANK_ACCOUNT, "1234", "", "", 0.9);
        LlmExtraction future = transaction(LlmExtraction.Type.EXPENSE, LlmExtraction.Direction.DEBIT, 450, "Swiggy", Category.FOOD_DINING, AccountType.BANK_ACCOUNT, "1234", "2026-12-25", "", 0.9);
        LlmExtraction ancient = transaction(LlmExtraction.Type.EXPENSE, LlmExtraction.Direction.DEBIT, 450, "Swiggy", Category.FOOD_DINING, AccountType.BANK_ACCOUNT, "1234", "2025-01-01", "", 0.9);

        for (LlmExtraction extraction : new LlmExtraction[] {missing, future, ancient}) {
            assertThat(validator.validate(extraction, TestData.event(UPI_DEBIT)).transaction().date()).isEqualTo(LocalDate.of(2026, 10, 1));
        }
    }

    @Test
    void dropsBalancesNotInTheMessageOrOnCreditCards() {
        LlmExtraction invented = transaction(LlmExtraction.Type.EXPENSE, LlmExtraction.Direction.DEBIT, 450, "Swiggy", Category.FOOD_DINING, AccountType.BANK_ACCOUNT, "1234", "", "55,000", 0.9);
        assertThat(validator.validate(invented, TestData.event(UPI_DEBIT)).transaction().availableBalance()).isNull();

        String cardSms = "Rs.1,299.00 spent on Credit Card XX5678 at AMAZON. Avl limit Rs 85,000";
        LlmExtraction card = transaction(LlmExtraction.Type.EXPENSE, LlmExtraction.Direction.DEBIT, 1299, "Amazon", Category.SHOPPING, AccountType.CREDIT_CARD, "5678", "", "85000", 0.9);
        assertThat(validator.validate(card, TestData.event(cardSms)).transaction().availableBalance()).isNull();
    }

    @Test
    void typeDecidesDirectionAndContradictionsAreFlagged() {
        LlmExtraction contradictory = transaction(LlmExtraction.Type.EXPENSE, LlmExtraction.Direction.CREDIT, 450, "Swiggy", Category.FOOD_DINING, AccountType.BANK_ACCOUNT, "1234", "", "", 0.95);

        TransactionDraft draft = validator.validate(contradictory, TestData.event(UPI_DEBIT)).transaction();

        assertThat(draft.direction()).isEqualTo(TransactionDirection.DEBIT);
        assertThat(draft.requiresReview()).isTrue();
    }

    @Test
    void transfersKeepTheirStatedDirection() {
        String sms = "Payment of Rs 15,000.00 received on your Credit Card XX5678. Thank you";
        LlmExtraction payment = transaction(LlmExtraction.Type.TRANSFER, LlmExtraction.Direction.CREDIT, 15000, "HDFC Bank", Category.CREDIT_CARD_PAYMENT, AccountType.CREDIT_CARD, "5678", "", "", 0.9);

        TransactionDraft draft = validator.validate(payment, TestData.event(sms)).transaction();

        assertThat(draft.type()).isEqualTo(TransactionType.TRANSFER);
        assertThat(draft.direction()).isEqualTo(TransactionDirection.CREDIT);
        assertThat(draft.requiresReview()).isFalse();
    }

    @Test
    void flagsLowConfidenceUnknownMerchantOtherCategoryAndOtpWording() {
        assertThat(validator.validate(transaction(LlmExtraction.Type.EXPENSE, LlmExtraction.Direction.DEBIT, 450, "Swiggy", Category.FOOD_DINING, AccountType.BANK_ACCOUNT, "1234", "", "", 0.5), TestData.event(UPI_DEBIT)).transaction().requiresReview()).isTrue();
        TransactionDraft noMerchant = validator.validate(transaction(LlmExtraction.Type.EXPENSE, LlmExtraction.Direction.DEBIT, 450, " ", Category.FOOD_DINING, AccountType.BANK_ACCOUNT, "1234", "", "", 0.9), TestData.event(UPI_DEBIT)).transaction();
        assertThat(noMerchant.merchant()).isEqualTo(TransactionDraft.UNKNOWN_MERCHANT);
        assertThat(noMerchant.requiresReview()).isTrue();
        assertThat(validator.validate(transaction(LlmExtraction.Type.EXPENSE, LlmExtraction.Direction.DEBIT, 450, "Swiggy", Category.OTHER, AccountType.BANK_ACCOUNT, "1234", "", "", 0.9), TestData.event(UPI_DEBIT)).transaction().requiresReview()).isTrue();
        assertThat(validator.validate(upiDebit(), TestData.event(UPI_DEBIT + " OTP 998877")).transaction().requiresReview()).isTrue();
    }

    @Test
    void defaultsInvalidCurrencyToInr() {
        LlmExtraction rupees = new LlmExtraction(MessageKind.TRANSACTION, "", LlmExtraction.Type.EXPENSE, LlmExtraction.Direction.DEBIT, 450, "Rs", "Swiggy",
            Category.FOOD_DINING, AccountType.BANK_ACCOUNT, PaymentChannel.UPI, "1234", "", "", "", 0.9);

        assertThat(validator.validate(rupees, TestData.event(UPI_DEBIT)).transaction().currency()).isEqualTo("INR");
    }

    @Test
    void numbersIgnoreSeparatorsAndTrailingZeros() {
        assertThat(MessageText.containsNumber("Rs.1,299.50 spent", new BigDecimal("1299.5"))).isTrue();
        assertThat(MessageText.containsNumber("Rs.1,299.50 spent", new BigDecimal("1299"))).isFalse();
    }
}
