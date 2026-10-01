import { isoDate } from "./format";

export type RangeKey = "7D" | "30D" | "3M" | "6M" | "1Y" | "MTD" | "LM";

export const OVERVIEW_RANGES: RangeKey[] = ["7D", "30D", "3M", "6M", "1Y"];
export const ANALYTICS_RANGES: RangeKey[] = ["7D", "30D", "3M", "6M", "1Y", "MTD", "LM"];

export const RANGE_LABELS: Record<RangeKey, string> = {
  "7D": "7D", "30D": "30D", "3M": "3M", "6M": "6M", "1Y": "1Y", MTD: "This month", LM: "Last month",
};

const RANGE_DESCRIPTIONS: Record<RangeKey, string> = {
  "7D": "last 7 days", "30D": "last 30 days", "3M": "last 3 months", "6M": "last 6 months", "1Y": "last 12 months", MTD: "this month", LM: "last month",
};

export function isRangeKey(value: string | null, allowed: RangeKey[]): value is RangeKey {
  return value !== null && (allowed as string[]).includes(value);
}

/** Inclusive date range ending today in the browser's local date. */
export function rangeDates(key: RangeKey, today = new Date()): { from: string; to: string } {
  const end = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  const start = new Date(end);
  switch (key) {
    case "7D": start.setDate(end.getDate() - 6); break;
    case "30D": start.setDate(end.getDate() - 29); break;
    case "3M": start.setMonth(end.getMonth() - 3); start.setDate(start.getDate() + 1); break;
    case "6M": start.setMonth(end.getMonth() - 6); start.setDate(start.getDate() + 1); break;
    case "1Y": start.setFullYear(end.getFullYear() - 1); start.setDate(start.getDate() + 1); break;
    case "MTD": start.setDate(1); break;
    case "LM": {
      const first = new Date(end.getFullYear(), end.getMonth() - 1, 1);
      const last = new Date(end.getFullYear(), end.getMonth(), 0);
      return { from: isoDate(first), to: isoDate(last) };
    }
  }
  return { from: isoDate(start), to: isoDate(end) };
}

export function rangeDescription(key: RangeKey): string {
  return RANGE_DESCRIPTIONS[key];
}

export function currentMonth(today = new Date()): string {
  return isoDate(today).slice(0, 7);
}

export function shiftMonth(month: string, delta: number): string {
  const [year, m] = month.split("-").map(Number);
  const date = new Date(year, m - 1 + delta, 1);
  return isoDate(date).slice(0, 7);
}
