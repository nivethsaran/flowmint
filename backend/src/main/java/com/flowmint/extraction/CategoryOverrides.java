package com.flowmint.extraction;

import com.flowmint.common.MerchantKey;
import com.flowmint.rules.MerchantRuleRepository;
import org.springframework.stereotype.Component;

/** Applies the operator's merchant category rules to freshly extracted transactions. */
@Component
public class CategoryOverrides {
    private final MerchantRuleRepository rules;

    public CategoryOverrides(MerchantRuleRepository rules) { this.rules = rules; }

    public TransactionDraft apply(TransactionDraft draft) {
        String key = MerchantKey.of(draft.merchant());
        if (key.isEmpty()) return draft;
        return rules.findById(key).map(rule -> draft.withRuleCategory(rule.getCategory())).orElse(draft);
    }
}
