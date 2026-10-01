package com.flowmint.transactions;

import java.util.Locale;

/** Closed category set shared by extraction, analytics, and budgets. Stored by key; labels are display-only. */
public enum Category {
    FOOD_DINING("Food & Dining", Kind.SPENDING),
    GROCERIES("Groceries", Kind.SPENDING),
    SHOPPING("Shopping", Kind.SPENDING),
    TRANSPORT("Transport", Kind.SPENDING),
    FUEL("Fuel", Kind.SPENDING),
    TRAVEL("Travel", Kind.SPENDING),
    BILLS_UTILITIES("Bills & Utilities", Kind.SPENDING),
    RENT_HOUSING("Rent & Housing", Kind.SPENDING),
    SUBSCRIPTIONS("Subscriptions", Kind.SPENDING),
    ENTERTAINMENT("Entertainment", Kind.SPENDING),
    HEALTH("Health", Kind.SPENDING),
    EDUCATION("Education", Kind.SPENDING),
    PERSONAL_CARE("Personal Care", Kind.SPENDING),
    INSURANCE("Insurance", Kind.SPENDING),
    LOANS_EMI("Loans & EMI", Kind.SPENDING),
    FEES_CHARGES("Fees & Charges", Kind.SPENDING),
    TAXES("Taxes", Kind.SPENDING),
    GIFTS_DONATIONS("Gifts & Donations", Kind.SPENDING),
    INVESTMENTS("Investments", Kind.INVESTMENT),
    SALARY("Salary", Kind.INCOME),
    INTEREST("Interest", Kind.INCOME),
    REFUNDS("Refunds", Kind.INCOME),
    CASH("Cash", Kind.SPENDING),
    TRANSFERS("Transfers", Kind.TRANSFER),
    CREDIT_CARD_PAYMENT("Credit Card Payment", Kind.TRANSFER),
    OTHER_INCOME("Other Income", Kind.INCOME),
    OTHER("Other", Kind.SPENDING);

    public enum Kind { SPENDING, INCOME, TRANSFER, INVESTMENT }

    private final String label;
    private final Kind kind;

    Category(String label, Kind kind) { this.label = label; this.kind = kind; }

    public String label() { return label; }
    public Kind kind() { return kind; }

    public boolean labelContains(String query) {
        return label.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }
}
