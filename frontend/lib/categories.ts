/** Mirrors the backend Category enum for labels and colors. The backend remains the source of truth for validation. */
export type CategoryKind = "SPENDING" | "INCOME" | "TRANSFER" | "INVESTMENT";
export type CategoryInfo = { key: string; label: string; kind: CategoryKind; color: string };

const C = {
  lime: "#c7f36b", teal: "#6ce3bd", orange: "#f5a86c", blue: "#7fb2ff", violet: "#b79cff", pink: "#f28dc0",
  yellow: "#f2d36b", cyan: "#66d1f0", coral: "#ff8a7a", green: "#8fd694", sand: "#d9b98c", rose: "#e7849c",
  indigo: "#9ea7ff", mint: "#a6e3c6", slate: "#8d9aa6", stone: "#a89f91",
};

export const CATEGORIES: CategoryInfo[] = [
  { key: "FOOD_DINING", label: "Food & Dining", kind: "SPENDING", color: C.orange },
  { key: "GROCERIES", label: "Groceries", kind: "SPENDING", color: C.green },
  { key: "SHOPPING", label: "Shopping", kind: "SPENDING", color: C.pink },
  { key: "TRANSPORT", label: "Transport", kind: "SPENDING", color: C.cyan },
  { key: "FUEL", label: "Fuel", kind: "SPENDING", color: C.yellow },
  { key: "TRAVEL", label: "Travel", kind: "SPENDING", color: C.blue },
  { key: "BILLS_UTILITIES", label: "Bills & Utilities", kind: "SPENDING", color: C.teal },
  { key: "RENT_HOUSING", label: "Rent & Housing", kind: "SPENDING", color: C.sand },
  { key: "SUBSCRIPTIONS", label: "Subscriptions", kind: "SPENDING", color: C.violet },
  { key: "ENTERTAINMENT", label: "Entertainment", kind: "SPENDING", color: C.coral },
  { key: "HEALTH", label: "Health", kind: "SPENDING", color: C.rose },
  { key: "EDUCATION", label: "Education", kind: "SPENDING", color: C.indigo },
  { key: "PERSONAL_CARE", label: "Personal Care", kind: "SPENDING", color: C.mint },
  { key: "INSURANCE", label: "Insurance", kind: "SPENDING", color: C.stone },
  { key: "LOANS_EMI", label: "Loans & EMI", kind: "SPENDING", color: C.lime },
  { key: "FEES_CHARGES", label: "Fees & Charges", kind: "SPENDING", color: C.coral },
  { key: "TAXES", label: "Taxes", kind: "SPENDING", color: C.stone },
  { key: "GIFTS_DONATIONS", label: "Gifts & Donations", kind: "SPENDING", color: C.pink },
  { key: "CASH", label: "Cash", kind: "SPENDING", color: C.mint },
  { key: "OTHER", label: "Other", kind: "SPENDING", color: C.slate },
  { key: "INVESTMENTS", label: "Investments", kind: "INVESTMENT", color: C.indigo },
  { key: "SALARY", label: "Salary", kind: "INCOME", color: C.teal },
  { key: "INTEREST", label: "Interest", kind: "INCOME", color: C.green },
  { key: "REFUNDS", label: "Refunds", kind: "INCOME", color: C.cyan },
  { key: "OTHER_INCOME", label: "Other Income", kind: "INCOME", color: C.mint },
  { key: "TRANSFERS", label: "Transfers", kind: "TRANSFER", color: C.slate },
  { key: "CREDIT_CARD_PAYMENT", label: "Credit Card Payment", kind: "TRANSFER", color: C.slate },
];

const BY_KEY = new Map(CATEGORIES.map((category) => [category.key, category]));

export function category(key: string): CategoryInfo {
  return BY_KEY.get(key) ?? { key, label: key, kind: "SPENDING", color: C.slate };
}

export const SPENDING_CATEGORIES = CATEGORIES.filter((c) => c.kind === "SPENDING");

/** Neutral color for "Other categories" slices. */
export const OTHER_SLICE_COLOR = "#56615a";

export const TYPE_LABELS: Record<string, string> = {
  EXPENSE: "Expense", INCOME: "Income", REFUND: "Refund", TRANSFER: "Transfer", CASH_WITHDRAWAL: "Cash withdrawal", INVESTMENT: "Investment", UNKNOWN: "Unknown",
};
export const EDITABLE_TYPES = ["EXPENSE", "INCOME", "REFUND", "TRANSFER", "CASH_WITHDRAWAL", "INVESTMENT"] as const;

/** A sensible default category when the type changes in a form. */
export const DEFAULT_CATEGORY_FOR_TYPE: Record<string, string> = {
  EXPENSE: "OTHER", INCOME: "OTHER_INCOME", REFUND: "REFUNDS", TRANSFER: "TRANSFERS", CASH_WITHDRAWAL: "CASH", INVESTMENT: "INVESTMENTS",
};

export const CHANNEL_LABELS: Record<string, string> = {
  UPI: "UPI", DEBIT_CARD: "Debit card", CREDIT_CARD: "Credit card", NETBANKING: "Net banking", ATM: "ATM", AUTOPAY: "Autopay", OTHER: "Other",
};

export const ACCOUNT_TYPE_LABELS: Record<string, string> = {
  BANK_ACCOUNT: "Bank account", CREDIT_CARD: "Credit card", WALLET: "Wallet", UNKNOWN: "Account",
};

export const KIND_LABELS: Record<string, string> = {
  TRANSACTION: "Transaction", OTP: "OTP", PAYMENT_REQUEST: "Payment request", FAILED_TRANSACTION: "Failed payment", BILL_REMINDER: "Bill reminder",
  BALANCE_ALERT: "Balance alert", PROMOTIONAL: "Promotion", OTHER: "Other",
};

export const STATUS_LABELS: Record<string, string> = {
  RECEIVED: "Queued", PROCESSING: "Processing", RETRY: "Retrying", PROCESSED: "Processed", FAILED: "Failed", IGNORED: "Ignored",
};

export const CADENCE_LABELS: Record<string, string> = { WEEKLY: "Weekly", MONTHLY: "Monthly", QUARTERLY: "Quarterly", YEARLY: "Yearly" };
