"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { Icon, type IconName } from "./Icon";
import { category } from "@/lib/categories";
import { moneyExact, shortDate, signFor } from "@/lib/format";
import type { Direction, TransactionItem, TransactionType } from "@/lib/types";

export function Portal({ children }: { children: ReactNode }) {
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);
  return mounted ? createPortal(children, document.body) : null;
}

/** Closes on Escape and locks page scroll while an overlay is open. */
export function useOverlay(onClose: () => void) {
  const close = useRef(onClose);
  close.current = onClose;
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => { if (event.key === "Escape") close.current(); };
    window.addEventListener("keydown", onKey);
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => { window.removeEventListener("keydown", onKey); document.body.style.overflow = previous; };
  }, []);
}

export function Modal({ title, subtitle, onClose, children }: { title: string; subtitle?: string; onClose: () => void; children: ReactNode }) {
  useOverlay(onClose);
  return (
    <Portal>
      <div className="overlay" onClick={onClose} />
      <div className="modal" role="dialog" aria-modal="true" aria-label={title}>
        <div className="modal-head">
          <div><h3>{title}</h3>{subtitle && <p>{subtitle}</p>}</div>
          <button className="icon-btn" onClick={onClose} aria-label="Close"><Icon name="close" /></button>
        </div>
        {children}
      </div>
    </Portal>
  );
}

export function Drawer({ label, onClose, children }: { label: string; onClose: () => void; children: ReactNode }) {
  useOverlay(onClose);
  return (
    <Portal>
      <div className="overlay" onClick={onClose} />
      <aside className="drawer" role="dialog" aria-modal="true" aria-label={label}>{children}</aside>
    </Portal>
  );
}

export function Sheet({ onClose, children }: { onClose: () => void; children: ReactNode }) {
  useOverlay(onClose);
  return (
    <Portal>
      <div className="overlay" onClick={onClose} />
      <div className="sheet" role="dialog" aria-modal="true"><div className="sheet-handle" />{children}</div>
    </Portal>
  );
}

export function EmptyState({ icon = "sparkle", title, children, action }: { icon?: IconName; title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="empty">
      <span className="empty-icon"><Icon name={icon} /></span>
      <strong>{title}</strong>
      {children && <p>{children}</p>}
      {action}
    </div>
  );
}

export function ErrorBanner({ message = "Flowmint API unavailable", detail = "Check that the backend is running, then retry.", onRetry }: { message?: string; detail?: string; onRetry?: () => void }) {
  return (
    <div className="banner" role="alert">
      <Icon name="alert" width={18} height={18} style={{ color: "var(--orange)" }} />
      <div><strong>{message}</strong><br /><span>{detail}</span></div>
      {onRetry && <button className="btn small" onClick={onRetry}><Icon name="refresh" />Retry</button>}
    </div>
  );
}

export function Skeleton({ height = 14, width = "100%", style }: { height?: number | string; width?: number | string; style?: React.CSSProperties }) {
  return <span className="skeleton" style={{ display: "block", height, width, ...style }} aria-hidden="true" />;
}

export function SkeletonRows({ rows = 4 }: { rows?: number }) {
  return (
    <div className="stack" aria-busy="true" aria-label="Loading">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} style={{ display: "flex", gap: 12, alignItems: "center" }}>
          <Skeleton width={36} height={36} style={{ borderRadius: 10, flex: "none" }} />
          <div style={{ flex: 1, display: "grid", gap: 6 }}><Skeleton width="45%" /><Skeleton width="30%" height={10} /></div>
          <Skeleton width={70} />
        </div>
      ))}
    </div>
  );
}

export function Badge({ tone, children, title }: { tone?: "review" | "duplicate" | "manual" | "good" | "bad" | "warn"; children: ReactNode; title?: string }) {
  return <span className={`badge ${tone ?? ""}`} title={title}>{children}</span>;
}

export function SignedAmount({ amount, type, direction, className = "" }: { amount: number; type: TransactionType; direction: Direction; className?: string }) {
  const sign = signFor(type, direction);
  const tone = sign === "+" ? "credit" : sign === "⇄" ? "transfer" : "";
  return <span className={`amount ${tone} ${className}`}>{sign} {moneyExact(amount)}</span>;
}

export function CategoryIcon({ categoryKey, label }: { categoryKey: string; label: string }) {
  const info = category(categoryKey);
  return (
    <span className="tx-icon" style={{ background: `${info.color}1f`, color: info.color }} aria-hidden="true">
      {(label.trim()[0] ?? "?").toUpperCase()}
    </span>
  );
}

export function TransactionBadges({ transaction }: { transaction: TransactionItem }) {
  return (
    <span className="tx-badges">
      {transaction.requiresReview && <Badge tone="review">Review</Badge>}
      {transaction.duplicateOfId && <Badge tone="duplicate" title="Excluded from totals">Duplicate</Badge>}
      {transaction.extractionMethod === "MANUAL" && <Badge tone="manual">Manual</Badge>}
    </span>
  );
}

export function TransactionRow({ transaction, onOpen, showAccount = true }: { transaction: TransactionItem; onOpen: (id: string) => void; showAccount?: boolean }) {
  const info = category(transaction.category);
  const meta = [info.label, showAccount ? transaction.accountName : null, shortDate(transaction.date)].filter(Boolean).join(" · ");
  return (
    <button className="tx-row" onClick={() => onOpen(transaction.id)}>
      <CategoryIcon categoryKey={transaction.category} label={transaction.merchant} />
      <span className="tx-main"><strong>{transaction.merchant}</strong><span className="tx-meta">{meta}</span></span>
      <TransactionBadges transaction={transaction} />
      <SignedAmount amount={transaction.amount} type={transaction.type} direction={transaction.direction} />
    </button>
  );
}

export function CategoryDot({ categoryKey }: { categoryKey: string }) {
  return <span className="swatch" style={{ background: category(categoryKey).color, borderRadius: "50%", width: 8, height: 8 }} />;
}

/** A small transient message, e.g. after saving. */
export function useToast(): [ReactNode, (message: string, tone?: "error") => void] {
  const [toast, setToast] = useState<{ message: string; tone?: "error"; key: number }>();
  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(undefined), 3200);
    return () => window.clearTimeout(timer);
  }, [toast]);
  const node = toast ? <Portal><div key={toast.key} className={`toast ${toast.tone ?? ""}`} role="status">{toast.message}</div></Portal> : null;
  return [node, (message, tone) => setToast({ message, tone, key: Date.now() })];
}
