package com.flowmint.extraction;

import com.flowmint.accounts.AccountType;
import com.flowmint.events.MessageKind;
import com.flowmint.transactions.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedExtractorTest {
    private final RuleBasedExtractor rules = new RuleBasedExtractor();

    @Test
    void bankDebitBecomesAReviewFlaggedExpense() {
        ExtractionResult result = rules.extract(TestData.event("INR 1,299.00 debited from A/c XX1234 on 30-09-2026 at AMAZON via UPI"));

        TransactionDraft draft = result.transaction();
        assertThat(draft.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(draft.direction()).isEqualTo(TransactionDirection.DEBIT);
        assertThat(draft.amount()).isEqualByComparingTo("1299");
        assertThat(draft.merchant()).isEqualTo("AMAZON");
        assertThat(draft.accountType()).isEqualTo(AccountType.BANK_ACCOUNT);
        assertThat(draft.accountLast4()).isEqualTo("1234");
        assertThat(draft.channel()).isEqualTo(PaymentChannel.UPI);
        assertThat(draft.date()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(draft.requiresReview()).isTrue();
        assertThat(draft.method()).isEqualTo(ExtractionMethod.RULES);
    }

    @Test
    void findsTheMerchantAtTheEndOfANotificationTitle() {
        assertThat(rules.extract(TestData.event("Rs 1,250 paid to Zepto", "Paid successfully from ICICI XX9876")).transaction().merchant()).isEqualTo("Zepto");
    }

    @Test
    void otpIsIgnored() {
        ExtractionResult result = rules.extract(TestData.event("123456 is your OTP for txn of INR 2,499.00 at AMAZON. Do not share."));

        assertThat(result.isTransaction()).isFalse();
        assertThat(result.kind()).isEqualTo(MessageKind.OTP);
    }

    @Test
    void failedPaymentsAndRequestsMoveNoMoney() {
        ExtractionResult failed = rules.extract(TestData.event("Payment of Rs 899.00 to Airtel failed. Amount will not be debited."));
        assertThat(failed.transaction()).isNull();
        assertThat(failed.kind()).isEqualTo(MessageKind.FAILED_TRANSACTION);

        ExtractionResult declined = rules.extract(TestData.event("Your txn of INR 4,500.00 on HDFC Card XX5678 at FLIPKART was declined"));
        assertThat(declined.kind()).isEqualTo(MessageKind.FAILED_TRANSACTION);

        ExtractionResult request = rules.extract(TestData.event("Rohan has requested money of Rs 500.00 from you on Google Pay"));
        assertThat(request.transaction()).isNull();
        assertThat(request.kind()).isEqualTo(MessageKind.PAYMENT_REQUEST);
    }

    @Test
    void creditsRefundsAndAtmWithdrawals() {
        assertThat(rules.extract(TestData.event("Your A/c XX1234 is credited with INR 85,000.00 by NEFT")).transaction().type()).isEqualTo(TransactionType.INCOME);
        assertThat(rules.extract(TestData.event("Refund of Rs 799.00 credited to your Credit Card XX5678")).transaction().type()).isEqualTo(TransactionType.REFUND);
        TransactionDraft atm = rules.extract(TestData.event("Rs 2,000 withdrawn at ATM from A/c XX1234")).transaction();
        assertThat(atm.type()).isEqualTo(TransactionType.CASH_WITHDRAWAL);
        assertThat(atm.category()).isEqualTo(Category.CASH);
    }

    @Test
    void unrecognizableMessagesYieldNothing() {
        assertThat(rules.extract(TestData.event("Your statement is ready"))).isNull();
        assertThat(rules.extract(TestData.event("Get 10% cashback up to Rs 500 on your next order"))).isNull();
    }
}
