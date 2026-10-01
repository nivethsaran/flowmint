package com.flowmint.accounts;

public enum AccountType {
    BANK_ACCOUNT("Bank account"), CREDIT_CARD("Credit card"), WALLET("Wallet"), UNKNOWN("Account");

    private final String label;

    AccountType(String label) { this.label = label; }

    public String label() { return label; }
}
