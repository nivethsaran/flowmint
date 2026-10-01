import type { Direction, TransactionType } from "./types";

const rupees = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 });
const rupeesPrecise = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", minimumFractionDigits: 2, maximumFractionDigits: 2 });
const compact = new Intl.NumberFormat("en-IN", { notation: "compact", maximumFractionDigits: 1 });

export function money(value: number | null | undefined): string {
  if (value === null || value === undefined) return "—";
  return rupees.format(Math.round(value) === 0 ? 0 : value);
}

/** Exact amounts with paise, for single transactions. */
export function moneyExact(value: number): string {
  return Number.isInteger(value) ? rupees.format(value) : rupeesPrecise.format(value);
}

export function moneyCompact(value: number): string {
  return "₹" + compact.format(value);
}

export function percent(value: number | null | undefined, digits = 0): string {
  if (value === null || value === undefined || !Number.isFinite(value)) return "—";
  return `${(value * 100).toFixed(digits)}%`;
}

/** "−" for money out, "+" for money in, "⇄" for transfers between own accounts. */
export function signFor(type: TransactionType, direction: Direction): "−" | "+" | "⇄" {
  if (type === "TRANSFER") return "⇄";
  return direction === "CREDIT" ? "+" : "−";
}

const dayFormat = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short" });
const dayYearFormat = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" });
const timeFormat = new Intl.DateTimeFormat("en-IN", { hour: "numeric", minute: "2-digit" });
const monthFormat = new Intl.DateTimeFormat("en-IN", { month: "long", year: "numeric" });
const longDate = new Intl.DateTimeFormat("en-IN", { weekday: "long", day: "2-digit", month: "long", year: "numeric" });

/** Parses YYYY-MM-DD as a local date (not UTC midnight). */
export function parseDate(value: string): Date {
  const [year, month, day] = value.split("-").map(Number);
  return new Date(year, month - 1, day);
}

export function isoDate(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function shortDate(value: string): string {
  const date = parseDate(value);
  return (date.getFullYear() === new Date().getFullYear() ? dayFormat : dayYearFormat).format(date);
}

export function fullDate(value: string): string {
  return dayYearFormat.format(parseDate(value));
}

export function monthLabel(month: string): string {
  return monthFormat.format(parseDate(`${month}-01`));
}

export function todayLong(date = new Date()): string {
  return longDate.format(date);
}

export function dateTime(instant: string): string {
  const date = new Date(instant);
  return `${(date.getFullYear() === new Date().getFullYear() ? dayFormat : dayYearFormat).format(date)}, ${timeFormat.format(date)}`;
}

/** "just now", "5 min ago", "3 h ago", "2 days ago" */
export function ago(instant: string): string {
  const seconds = Math.max(0, (Date.now() - new Date(instant).getTime()) / 1000);
  if (seconds < 60) return "just now";
  if (seconds < 3600) return `${Math.floor(seconds / 60)} min ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)} h ago`;
  const days = Math.floor(seconds / 86400);
  return days === 1 ? "yesterday" : days < 30 ? `${days} days ago` : dateTime(instant);
}

/** Days from today to a date: "today", "tomorrow", "in 3 days", "4 days overdue". */
export function relativeDay(value: string): { label: string; days: number } {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const days = Math.round((parseDate(value).getTime() - today.getTime()) / 86400000);
  if (days === 0) return { label: "today", days };
  if (days === 1) return { label: "tomorrow", days };
  if (days === -1) return { label: "1 day overdue", days };
  return { label: days > 0 ? `in ${days} days` : `${-days} days overdue`, days };
}

export function greeting(date = new Date()): string {
  const hour = date.getHours();
  if (hour < 12) return "Good morning";
  if (hour < 17) return "Good afternoon";
  return "Good evening";
}

/** Relative change from previous to current, or null when there is no baseline. */
export function change(current: number, previous: number): number | null {
  if (!previous) return null;
  return (current - previous) / Math.abs(previous);
}

export function plural(count: number, word: string, pluralWord = `${word}s`): string {
  return `${count} ${count === 1 ? word : pluralWord}`;
}
