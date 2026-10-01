package com.flowmint.transactions;

import com.flowmint.events.RawEvent;

public interface LlmClient {
    FinanceExtraction extract(RawEvent event);
}
