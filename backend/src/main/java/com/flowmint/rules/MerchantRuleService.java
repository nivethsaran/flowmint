package com.flowmint.rules;

import com.flowmint.common.ApiException;
import com.flowmint.common.MerchantKey;
import com.flowmint.extraction.TransactionDraft;
import com.flowmint.transactions.Category;
import com.flowmint.transactions.ReviewReason;
import com.flowmint.transactions.Transaction;
import com.flowmint.transactions.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class MerchantRuleService {
    private static final String UNKNOWN_KEY = MerchantKey.of(TransactionDraft.UNKNOWN_MERCHANT);
    private final MerchantRuleRepository rules;
    private final TransactionRepository transactions;

    public MerchantRuleService(MerchantRuleRepository rules, TransactionRepository transactions) {
        this.rules = rules;
        this.transactions = transactions;
    }

    /**
     * Remembers {@code category} for the merchant and recategorizes its other transactions the operator has not edited.
     * Returns how many other transactions changed.
     */
    @Transactional
    public int apply(String merchant, Category category, UUID exceptTransactionId) {
        String key = MerchantKey.of(merchant);
        if (key.isEmpty() || key.equals(UNKNOWN_KEY)) throw ApiException.validation("applyToMerchant", "A rule needs a known merchant");
        rules.findById(key).ifPresentOrElse(rule -> rule.setCategory(category), () -> rules.save(new MerchantRule(key, category)));
        int changed = 0;
        for (Transaction transaction : transactions.findByMerchantNormalizedAndUserEditedFalse(key)) {
            if (transaction.getId().equals(exceptTransactionId)) continue;
            transaction.setCategory(category);
            transaction.removeReviewReason(ReviewReason.UNCATEGORIZED);
            changed++;
        }
        return changed;
    }
}
