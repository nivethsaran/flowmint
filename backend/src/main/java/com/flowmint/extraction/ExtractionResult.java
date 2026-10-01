package com.flowmint.extraction;

import com.flowmint.events.MessageKind;

/** A trusted outcome for one message: either a non-transaction classification or a validated transaction. */
public record ExtractionResult(MessageKind kind, String reason, TransactionDraft transaction) {
    public static ExtractionResult ignored(MessageKind kind, String reason) {
        return new ExtractionResult(kind, reason, null);
    }

    public static ExtractionResult transaction(String reason, TransactionDraft draft) {
        return new ExtractionResult(MessageKind.TRANSACTION, reason, draft);
    }

    public boolean isTransaction() { return transaction != null; }
}
