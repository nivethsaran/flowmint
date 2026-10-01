package com.flowmint.transactions;

public enum TransactionType {
    EXPENSE(TransactionDirection.DEBIT),
    INCOME(TransactionDirection.CREDIT),
    REFUND(TransactionDirection.CREDIT),
    TRANSFER(null),
    CASH_WITHDRAWAL(TransactionDirection.DEBIT),
    INVESTMENT(TransactionDirection.DEBIT),
    UNKNOWN(null);

    private final TransactionDirection impliedDirection;

    TransactionType(TransactionDirection impliedDirection) { this.impliedDirection = impliedDirection; }

    /** The only valid direction for this type, or null when either direction is possible. */
    public TransactionDirection impliedDirection() { return impliedDirection; }
}
