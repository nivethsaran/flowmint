export type TransactionType = "EXPENSE" | "INCOME" | "REFUND" | "TRANSFER" | "CASH_WITHDRAWAL" | "INVESTMENT" | "UNKNOWN";
export type Direction = "DEBIT" | "CREDIT";
export type Channel = "UPI" | "DEBIT_CARD" | "CREDIT_CARD" | "NETBANKING" | "ATM" | "AUTOPAY" | "OTHER";
export type ExtractionMethod = "LLM" | "RULES" | "MANUAL";
export type AccountType = "BANK_ACCOUNT" | "CREDIT_CARD" | "WALLET" | "UNKNOWN";
export type MessageKind = "TRANSACTION" | "OTP" | "PAYMENT_REQUEST" | "FAILED_TRANSACTION" | "BILL_REMINDER" | "BALANCE_ALERT" | "PROMOTIONAL" | "OTHER";
export type ProcessingStatus = "RECEIVED" | "PROCESSING" | "RETRY" | "PROCESSED" | "FAILED" | "IGNORED";
export type EventFeedback = "FALSE_POSITIVE" | "FALSE_NEGATIVE" | "INCORRECT_TAG";

export type Page<T> = { items: T[]; page: number; size: number; totalItems: number; totalPages: number };

export type TransactionItem = {
  id: string;
  date: string;
  merchant: string;
  amount: number;
  currency: string;
  type: TransactionType;
  direction: Direction;
  category: string;
  accountId: string | null;
  accountName: string | null;
  channel: Channel;
  requiresReview: boolean;
  duplicateOfId: string | null;
  extractionMethod: ExtractionMethod;
  notes: string | null;
};

export type TransactionDetail = TransactionItem & {
  accountType: AccountType | null;
  reviewReasons: { code: string; description: string }[];
  confidence: number | null;
  occurredAt: string;
  userEdited: boolean;
  createdAt: string;
  updatedAt: string;
  source: { eventId: string; source: string; sender: string | null; title: string | null; body: string; receivedAt: string; kind: MessageKind | null; reason: string | null } | null;
};

export type Totals = { income: number; spending: number; refunds: number; invested: number; netCashFlow: number; savingsRate: number | null; transactionCount: number };

export type AnalyticsSummary = {
  range: { from: string; to: string };
  previousRange: { from: string; to: string };
  granularity: "DAY" | "WEEK" | "MONTH";
  totals: Totals;
  previous: Totals;
  series: { start: string; end: string; income: number; spending: number }[];
  categories: { category: string; amount: number; count: number; share: number; previousAmount: number }[];
  topMerchants: { merchant: string; amount: number; count: number }[];
  accounts: { accountId: string | null; name: string; spending: number; income: number }[];
  reviewCount: number;
};

export type BudgetStatusValue = "ON_TRACK" | "AT_RISK" | "OVER";
export type BudgetStatus = { category: string; limit: number; spent: number; remaining: number; percentUsed: number; projected: number; status: BudgetStatusValue };
export type BudgetMonth = { month: string; daysInMonth: number; daysElapsed: number; total: BudgetStatus | null; budgets: BudgetStatus[]; unbudgeted: { category: string; spent: number }[] };

export type Cadence = "WEEKLY" | "MONTHLY" | "QUARTERLY" | "YEARLY";
export type RecurringSeries = {
  key: string;
  merchant: string;
  category: string;
  cadence: Cadence;
  amount: number;
  lastAmount: number;
  lastDate: string;
  occurrences: number;
  accountName: string | null;
  nextExpectedDate: string;
  monthlyEquivalent: number;
  status: "ACTIVE" | "LAPSED";
  dismissed: boolean;
};
export type RecurringOverview = { monthlyTotal: number; activeCount: number; items: RecurringSeries[] };

export type Account = {
  id: string;
  type: AccountType;
  institution: string | null;
  last4: string | null;
  displayName: string;
  lastKnownBalance: number | null;
  balanceAsOf: string | null;
  transactionCount: number;
  lastTransactionDate: string | null;
  monthDebits: number;
  monthCredits: number;
};

export type InboxItem = {
  id: string;
  source: string;
  sender: string | null;
  packageName: string | null;
  title: string | null;
  preview: string;
  body: string | null;
  eventTimestamp: string;
  receivedAt: string;
  status: ProcessingStatus;
  kind: MessageKind | null;
  reason: string | null;
  attempts: number;
  lastError: string | null;
  nextAttemptAt: string | null;
  transactionId: string | null;
  feedback: EventFeedback | null;
};

export type EventStats = Record<ProcessingStatus, number>;
