"use client";

import Link from "next/link";
import { Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { BUDGET_STATUS_LABELS, BUDGET_STATUS_TONES, ProgressBar } from "@/components/charts";
import { Icon } from "@/components/Icon";
import { Badge, CategoryDot, EmptyState, ErrorBanner, Modal, Skeleton, SkeletonRows, useToast } from "@/components/ui";
import { api, ApiError, query } from "@/lib/api";
import { SPENDING_CATEGORIES, category } from "@/lib/categories";
import { money, monthLabel } from "@/lib/format";
import { currentMonth, shiftMonth } from "@/lib/ranges";
import { notifyDataChanged, useApi, useDataChanged } from "@/lib/useApi";
import type { BudgetMonth, BudgetStatus } from "@/lib/types";

export default function BudgetsPage() {
  return <Suspense><Budgets /></Suspense>;
}

type Editing = { category: string; amount: string; isNew: boolean };

/** Suggest a limit just above what was spent, rounded to a friendly number. */
function suggestLimit(spent: number): number {
  const step = spent >= 50000 ? 5000 : spent >= 10000 ? 1000 : 500;
  return Math.max(step, Math.ceil((spent * 1.1) / step) * step);
}

function Budgets() {
  const router = useRouter();
  const params = useSearchParams();
  const thisMonth = currentMonth();
  const monthParam = params.get("month");
  const month = monthParam && /^\d{4}-\d{2}$/.test(monthParam) ? monthParam : thisMonth;
  const data = useApi<BudgetMonth>(`/budgets${query({ month })}`);
  useDataChanged(data.reload);
  const [editing, setEditing] = useState<Editing | null>(null);
  const [toast, showToast] = useToast();
  const b = data.data;
  const pace = b && b.daysElapsed > 0 && b.daysElapsed < b.daysInMonth ? b.daysElapsed / b.daysInMonth : undefined;
  const isCurrent = month === thisMonth;
  const budgeted = new Set(b?.budgets.map((x) => x.category) ?? []);

  const setMonth = (value: string) => router.replace(value === thisMonth ? "/budgets" : `/budgets?month=${value}`, { scroll: false });

  async function remove(key: string) {
    try {
      await api.delete(`/budgets/${key}`);
      notifyDataChanged();
      showToast(`${key === "TOTAL" ? "Overall" : category(key).label} budget removed`);
    } catch (error) {
      showToast(error instanceof ApiError ? error.message : "Could not remove budget", "error");
    }
  }

  return (
    <>
      <div className="page-head">
        <div><span className="eyebrow">Budgets</span><h2>Spend with intent.</h2><p>Monthly limits apply to every month. {isCurrent ? "Projections assume you keep spending at this month's pace." : ""}</p></div>
        <div className="page-actions">
          <div className="segmented" style={{ alignItems: "center" }}>
            <button aria-label="Previous month" onClick={() => setMonth(shiftMonth(month, -1))}><Icon name="chevronLeft" width={14} height={14} /></button>
            <span style={{ padding: "0 10px", fontSize: 13, fontWeight: 600, minWidth: 130, textAlign: "center" }}>{monthLabel(month)}</span>
            <button aria-label="Next month" onClick={() => setMonth(shiftMonth(month, 1))}><Icon name="chevronRight" width={14} height={14} /></button>
          </div>
          {!isCurrent && <button className="btn ghost" onClick={() => setMonth(thisMonth)}>This month</button>}
          <button className="btn primary" onClick={() => setEditing({ category: SPENDING_CATEGORIES.find((c) => !budgeted.has(c.key))?.key ?? "TOTAL", amount: "", isNew: true })}><Icon name="plus" />Add budget</button>
        </div>
      </div>

      {data.error && data.error.status !== 401 && <ErrorBanner onRetry={data.reload} />}

      <article className="panel" style={{ marginBottom: 14 }}>
        <div className="panel-head">
          <div><span className="eyebrow">Overall</span><h3>All spending</h3><div className="sub">Expenses and cash withdrawals, minus refunds</div></div>
          {b?.total && <BudgetActions onEdit={() => setEditing({ category: "TOTAL", amount: String(b.total!.limit), isNew: false })} onDelete={() => remove("TOTAL")} />}
        </div>
        {!b ? <div className="stack"><Skeleton height={28} width="40%" /><Skeleton height={8} /></div>
          : b.total ? <BudgetDetail status={b.total} pace={pace} isCurrent={isCurrent} big />
          : <EmptyState icon="budgets" title="No overall budget" action={<button className="btn primary small" onClick={() => setEditing({ category: "TOTAL", amount: "", isNew: true })}>Set overall budget</button>}>
              A single monthly limit for everything you spend.
            </EmptyState>}
      </article>

      <section className="grid-main">
        <article className="panel">
          <div className="panel-head">
            <div><span className="eyebrow">By category</span><h3>Category budgets</h3><div className="sub">Most used first</div></div>
          </div>
          {!b ? <SkeletonRows rows={4} />
            : b.budgets.length === 0 ? <EmptyState icon="budgets" title="No category budgets" action={<button className="btn small" onClick={() => setEditing({ category: b.unbudgeted[0]?.category ?? "FOOD_DINING", amount: b.unbudgeted[0] ? String(suggestLimit(b.unbudgeted[0].spent)) : "", isNew: true })}>Add a category budget</button>}>
                Cap the categories you want to keep an eye on, like dining out or shopping.
              </EmptyState>
            : b.budgets.map((status) => (
              <div key={status.category} className="budget-row">
                <div className="budget-top">
                  <span className="budget-name"><CategoryDot categoryKey={status.category} />{category(status.category).label}</span>
                  <span style={{ display: "flex", alignItems: "center", gap: 8 }}>
                    <Badge tone={BUDGET_STATUS_TONES[status.status]}>{BUDGET_STATUS_LABELS[status.status]}</Badge>
                    <BudgetActions onEdit={() => setEditing({ category: status.category, amount: String(status.limit), isNew: false })} onDelete={() => remove(status.category)} />
                  </span>
                </div>
                <BudgetDetail status={status} pace={pace} isCurrent={isCurrent} />
                <Link className="link" style={{ justifySelf: "start", fontSize: 11.5 }} href={`/transactions${query({ category: status.category, from: `${month}-01`, to: `${month}-${String(b.daysInMonth).padStart(2, "0")}` })}`}>View transactions →</Link>
              </div>
            ))}
        </article>

        <article className="panel" style={{ alignSelf: "start" }}>
          <div className="panel-head">
            <div><span className="eyebrow">Suggestions</span><h3>Spending without a budget</h3><div className="sub">{monthLabel(month)}</div></div>
          </div>
          {!b ? <SkeletonRows rows={3} />
            : b.unbudgeted.length === 0 ? <EmptyState icon="check" title={b.budgets.length ? "Everything is covered" : "No spending yet"}>{b.budgets.length ? "Every category you spent in has a budget." : "Categories you spend in will show up here."}</EmptyState>
            : b.unbudgeted.map((u) => (
              <div key={u.category} className="upcoming" style={{ alignItems: "center" }}>
                <span className="budget-name" style={{ fontWeight: 500 }}><CategoryDot categoryKey={u.category} />{category(u.category).label}</span>
                <button className="btn small" onClick={() => setEditing({ category: u.category, amount: String(suggestLimit(u.spent)), isNew: true })}>Set budget</button>
                <small>{money(u.spent)} spent</small>
                <small style={{ textAlign: "right" }}>suggested {money(suggestLimit(u.spent))}</small>
              </div>
            ))}
        </article>
      </section>

      {editing && (
        <BudgetModal editing={editing} existing={new Set([...budgeted, ...(b?.total ? ["TOTAL"] : [])])} onClose={() => setEditing(null)}
          onSaved={(label) => { setEditing(null); notifyDataChanged(); showToast(`${label} budget saved`); }} />
      )}
      {toast}
    </>
  );
}

function BudgetActions({ onEdit, onDelete }: { onEdit: () => void; onDelete: () => void }) {
  const [confirming, setConfirming] = useState(false);
  if (confirming) {
    return (
      <span style={{ display: "flex", gap: 6, alignItems: "center" }}>
        <span className="dim" style={{ fontSize: 12 }}>Remove?</span>
        <button className="btn small ghost" onClick={() => setConfirming(false)}>No</button>
        <button className="btn small danger" onClick={() => { setConfirming(false); onDelete(); }}>Remove</button>
      </span>
    );
  }
  return (
    <span style={{ display: "flex", gap: 2 }}>
      <button className="icon-btn" aria-label="Edit budget" onClick={onEdit}><Icon name="edit" /></button>
      <button className="icon-btn" aria-label="Remove budget" onClick={() => setConfirming(true)}><Icon name="trash" /></button>
    </span>
  );
}

function BudgetDetail({ status, pace, isCurrent, big = false }: { status: BudgetStatus; pace?: number; isCurrent: boolean; big?: boolean }) {
  return (
    <div className="stack" style={{ gap: 8 }}>
      {big && (
        <div style={{ display: "flex", alignItems: "baseline", gap: 12, flexWrap: "wrap" }}>
          <span className="account-balance">{money(status.spent)}</span>
          <span className="muted">of {money(status.limit)}</span>
          <span style={{ marginLeft: "auto" }}><Badge tone={BUDGET_STATUS_TONES[status.status]}>{BUDGET_STATUS_LABELS[status.status]}</Badge></span>
        </div>
      )}
      <ProgressBar value={status.spent} limit={status.limit} status={status.status} pace={pace} label="Budget used" />
      <div className="budget-figures">
        {!big && <span><b>{money(status.spent)}</b> of {money(status.limit)}</span>}
        <span><b>{Math.round(status.percentUsed)}%</b> used</span>
        <span className={status.remaining < 0 ? "bad" : ""}>{status.remaining >= 0 ? `${money(status.remaining)} left` : `${money(-status.remaining)} over`}</span>
        {isCurrent && status.projected > 0 && <span className={status.projected > status.limit ? "warn" : ""} title="If spending continues at this month's pace">on pace for {money(status.projected)}</span>}
      </div>
    </div>
  );
}

function BudgetModal({ editing, existing, onClose, onSaved }: { editing: Editing; existing: Set<string>; onClose: () => void; onSaved: (label: string) => void }) {
  const [form, setForm] = useState(editing);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const label = form.category === "TOTAL" ? "Overall" : category(form.category).label;
  const replacing = editing.isNew && existing.has(form.category);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    const amount = Number(form.amount);
    if (!form.amount || !Number.isFinite(amount) || amount <= 0) { setError("Enter a monthly limit above zero"); return; }
    setSaving(true);
    try {
      await api.put(`/budgets/${form.category}`, { amount });
      onSaved(label);
    } catch (cause) {
      setError(cause instanceof ApiError ? Object.values(cause.fields)[0] ?? cause.message : "Could not save budget");
      setSaving(false);
    }
  }

  return (
    <Modal title={editing.isNew ? "Add budget" : `Edit ${label.toLowerCase()} budget`} subtitle="A monthly limit, applied to every month." onClose={onClose}>
      <form className="stack" onSubmit={submit} noValidate>
        <label className="field">Budget for
          <select className="select" value={form.category} disabled={!editing.isNew} onChange={(e) => setForm({ ...form, category: e.target.value })}>
            <option value="TOTAL">All spending (overall)</option>
            {SPENDING_CATEGORIES.map((c) => <option key={c.key} value={c.key}>{c.label}{existing.has(c.key) ? " — has a budget" : ""}</option>)}
          </select>
          {replacing && <span className="hint">This replaces the existing {label.toLowerCase()} budget.</span>}
        </label>
        <label className="field">Monthly limit (₹)
          <input className={`input ${error ? "invalid" : ""}`} inputMode="decimal" autoFocus value={form.amount} onChange={(e) => { setError(""); setForm({ ...form, amount: e.target.value.replace(/[^\d.]/g, "") }); }} placeholder="e.g. 8000" />
          {error && <span className="error">{error}</span>}
        </label>
        <div className="form-actions">
          <button type="button" className="btn ghost" onClick={onClose}>Cancel</button>
          <button className="btn primary" disabled={saving}>{saving ? "Saving…" : "Save budget"}</button>
        </div>
      </form>
    </Modal>
  );
}
