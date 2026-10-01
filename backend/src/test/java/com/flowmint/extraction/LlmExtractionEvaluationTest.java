package com.flowmint.extraction;

import com.flowmint.accounts.AccountType;
import com.flowmint.events.MessageKind;
import com.flowmint.events.RawEvent;
import com.flowmint.transactions.Category;
import com.flowmint.transactions.TransactionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Measures the prompt against the real model. Runs only when FLOWMINT_LLM_API_KEY is set:
 * {@code FLOWMINT_LLM_BASE_URL=... FLOWMINT_LLM_API_KEY=... mvn test -Dtest=LlmExtractionEvaluationTest}
 */
@EnabledIfEnvironmentVariable(named = "FLOWMINT_LLM_API_KEY", matches = ".+")
class LlmExtractionEvaluationTest {
    private record Case(String body, MessageKind kind, TransactionType type, AccountType accountType, Category category) {}

    private static final List<Case> CASES = List.of(
        new Case("482913 is your OTP for txn of INR 2,499.00 at AMAZON on HDFC Bank card XX5678. Valid for 10 mins. Do not share with anyone.", MessageKind.OTP, null, null, null),
        new Case("INR 450.00 debited from A/c XX1234 on 01-10-26 to VPA swiggy@icici (UPI Ref 427381920). Avl Bal INR 12,000.00 -HDFC Bank", MessageKind.TRANSACTION, TransactionType.EXPENSE, AccountType.BANK_ACCOUNT, Category.FOOD_DINING),
        new Case("Rs.1,299.00 spent on your SBI Credit Card ending 5678 at AMAZON PAY INDIA on 01/10/26. Avl Lmt Rs.85,000.00", MessageKind.TRANSACTION, TransactionType.EXPENSE, AccountType.CREDIT_CARD, Category.SHOPPING),
        new Case("Rs 15,000.00 debited from A/c XX1234 towards your ICICI Bank Credit Card XX5678 payment on 01-Oct-26.", MessageKind.TRANSACTION, TransactionType.TRANSFER, AccountType.BANK_ACCOUNT, Category.CREDIT_CARD_PAYMENT),
        new Case("Payment of Rs 15,000.00 received on your ICICI Bank Credit Card XX5678 on 01-Oct-26. Thank you.", MessageKind.TRANSACTION, TransactionType.TRANSFER, AccountType.CREDIT_CARD, Category.CREDIT_CARD_PAYMENT),
        new Case("Your A/c XX1234 is credited with INR 85,000.00 on 01-10-26 by NEFT from ACME TECHNOLOGIES PVT LTD SALARY SEP. Avl Bal INR 97,000.00", MessageKind.TRANSACTION, TransactionType.INCOME, AccountType.BANK_ACCOUNT, Category.SALARY),
        new Case("Refund of Rs 799.00 from MYNTRA has been credited to your HDFC Bank Credit Card XX5678 on 01-10-26.", MessageKind.TRANSACTION, TransactionType.REFUND, AccountType.CREDIT_CARD, Category.REFUNDS),
        new Case("Rs 2,000.00 withdrawn at ATM SBI MG ROAD from A/c XX1234 on 01-10-26. Avl Bal Rs 10,000.00", MessageKind.TRANSACTION, TransactionType.CASH_WITHDRAWAL, AccountType.BANK_ACCOUNT, Category.CASH),
        new Case("Ramesh Kumar has requested Rs 500.00 from you via UPI. Approve the collect request in your UPI app.", MessageKind.PAYMENT_REQUEST, null, null, null),
        new Case("Your transaction of Rs 700.00 at ZOMATO on HDFC Bank Credit Card XX5678 was declined due to incorrect PIN.", MessageKind.FAILED_TRANSACTION, null, null, null),
        new Case("HDFC Bank Credit Card XX5678 statement: Total due Rs 18,240.00, Minimum due Rs 920.00. Pay by 15-10-26 to avoid charges.", MessageKind.BILL_REMINDER, null, null, null),
        new Case("Congratulations! You are pre-approved for a Personal Loan up to Rs 5,00,000 at 10.5% p.a. Apply now: hdfc.bank/pl", MessageKind.PROMOTIONAL, null, null, null)
    );

    @Test
    void classifiesLabelledMessages() {
        LlmProperties properties = new LlmProperties(
            System.getenv().getOrDefault("FLOWMINT_LLM_BASE_URL", "https://inferra.niveth.dev/v1"), System.getenv("FLOWMINT_LLM_API_KEY"),
            System.getenv().getOrDefault("FLOWMINT_LLM_MODEL", "default"), Duration.ofSeconds(180), 400, 1, List.of(Duration.ofMinutes(1)), 0.7, Duration.ofMinutes(5), 10);
        LlmConfig config = new LlmConfig();
        LlmClient client = new LangChainLlmClient(config.extractionAssistant(config.extractionChatModel(properties)));
        ExtractionValidator validator = new ExtractionValidator(properties);

        int correct = 0;
        for (Case c : CASES) {
            RawEvent event = TestData.event(c.body());
            long started = System.nanoTime();
            String outcome;
            boolean ok;
            try {
                LlmExtraction raw = client.extract(event);
                ExtractionResult result = validator.validate(raw, event);
                ok = result.kind() == c.kind();
                if (ok && c.type() != null) {
                    ok = result.transaction().type() == c.type() && raw.accountType() == c.accountType() && result.transaction().category() == c.category();
                }
                outcome = result.isTransaction()
                    ? "%s %s %s %s %s %s ••%s review=%s".formatted(result.kind(), result.transaction().type(), result.transaction().direction(), result.transaction().amount(),
                        result.transaction().merchant(), raw.accountType(), result.transaction().accountLast4(), result.transaction().requiresReview()) + " " + result.transaction().category()
                    : result.kind() + " (" + result.reason() + ")";
            } catch (RuntimeException e) {
                ok = false;
                outcome = "ERROR " + e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            if (ok) correct++;
            System.out.printf("%s %5.1fs expected=%s/%s/%s/%s got=%s%n", ok ? "PASS" : "FAIL", (System.nanoTime() - started) / 1e9, c.kind(), c.type(), c.accountType(), c.category(), outcome);
        }
        System.out.printf("%d/%d correct%n", correct, CASES.size());
        assertThat(correct).isGreaterThanOrEqualTo(10);
    }
}
