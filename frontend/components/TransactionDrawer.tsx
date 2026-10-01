"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { api, ApiError } from "@/lib/api";
import { useApi, notifyDataChanged } from "@/lib/useApi";
import { CATEGORIES, CHANNEL_LABELS, DEFAULT_CATEGORY_FOR_TYPE, EDITABLE_TYPES, TYPE_LABELS, category } from "@/lib/categories";
import { dateTime, fullDate, isoDate } from "@/lib/format";
import type { Account, Direction, TransactionDetail } from "@/lib/types";
import { Icon } from "./Icon";
import { Badge, CategoryIcon, Drawer, ErrorBanner, Modal, SignedAmount, Skeleton, useToast } from "./ui";

type DrawerApi = { open: (id: string) => void; openAdd: () => void };
const DrawerContext = createContext<DrawerApi>({ open: () => {}, openAdd: () => {} });

export function useTransactionDrawer() {
  return useContext(DrawerContext);
}

export function TransactionDrawerProvider({ children }: { children: ReactNode }) {
  const [openId, setOpenId] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [toast, showToast] = useToast();
  const value = useMemo<DrawerApi>(() => ({ open: setOpenId, openAdd: () => setAdding(true) }), []);
  return (
    <DrawerContext.Provider value={value}>
      {children}
      {openId && <TransactionDrawer key={openId} id={openId} onClose={() => setOpenId(null)} onNavigate={setOpenId} toast={showToast} />}
      {adding && <AddTransactionModal onClose={() => setAdding(false)} onCreated={(id) => { setAdding(false); showToast("Transaction added"); setOpenId(id); }} />}
      {toast}
    </DrawerContext.Provider>
  );
}

type FormState = { merchant: string; amount: string; type: string; direction: Direction; category: string; accountId: string; date: string; notes: string };

function TransactionFields({ form, setForm, accounts, errors }: { form: FormState; setForm: (f: FormState) => void; accounts: Account[]; errors: Record<string, string> }) {
  const set = (patch: Partial<FormState>) => setForm({ ...form, ...patch });
  const categoryOptions = useMemo(() => {
    const groups: Record<string, typeof CATEGORIES> = { Spending: [], Income: [], "Transfers & investing": [] };
    for (const c of CATEGORIES) groups[c.kind === "SPENDING" ? "Spending" : c.kind === "INCOME" ? "Income" : "Transfers & investing"].push(c);
    return groups;
  }, []);
  return (
    <div className="form-grid">
      <label className="field span-2">Merchant
        <input className={`input ${errors.merchant ? "invalid" : ""}`} value={form.merchant} maxLength={200} onChange={(e) => set({ merchant: e.target.value })} required />
        {errors.merchant && <span className="error">{errors.merchant}</span>}
      </label>
      <label className="field">Amount (₹)
        <input className={`input ${errors.amount ? "invalid" : ""}`} inputMode="decimal" value={form.amount} onChange={(e) => set({ amount: e.target.value.replace(/[^\d.]/g, "") })} required />
        {errors.amount && <span className="error">{errors.amount}</span>}
      </label>
      <label className="field">Date
        <input className={`input ${errors.date ? "invalid" : ""}`} type="date" value={form.date} max={isoDate(new Date())} onChange={(e) => set({ date: e.target.value })} required />
        {errors.date && <span className="error">{errors.date}</span>}
      </label>
      <label className="field">Type
        <select className="select" value={form.type} onChange={(e) => set({ type: e.target.value, category: DEFAULT_CATEGORY_FOR_TYPE[e.target.value] ?? form.category })}>
          {form.type === "UNKNOWN" && <option value="UNKNOWN" disabled>Unknown — choose one</option>}
          {EDITABLE_TYPES.map((t) => <option key={t} value={t}>{TYPE_LABELS[t]}</option>)}
        </select>
        {errors.type && <span className="error">{errors.type}</span>}
      </label>
      {form.type === "TRANSFER" ? (
        <label className="field">Direction
          <select className="select" value={form.direction} onChange={(e) => set({ direction: e.target.value as Direction })}>
            <option value="DEBIT">Money out of this account</option>
            <option value="CREDIT">Money into this account</option>
          </select>
          {errors.direction && <span className="error">{errors.direction}</span>}
        </label>
      ) : (
        <label className="field">Category
          <select className="select" value={form.category} onChange={(e) => set({ category: e.target.value })}>
            {Object.entries(categoryOptions).map(([group, items]) => (
              <optgroup key={group} label={group}>{items.map((c) => <option key={c.key} value={c.key}>{c.label}</option>)}</optgroup>
            ))}
          </select>
        </label>
      )}
      {form.type === "TRANSFER" && (
        <label className="field">Category
          <select className="select" value={form.category} onChange={(e) => set({ category: e.target.value })}>
            {CATEGORIES.map((c) => <option key={c.key} value={c.key}>{c.label}</option>)}
          </select>
        </label>
      )}
      <label className={`field ${form.type === "TRANSFER" ? "" : "span-2"}`}>Account
        <select className="select" value={form.accountId} onChange={(e) => set({ accountId: e.target.value })}>
          <option value="">No account</option>
          {accounts.map((a) => <option key={a.id} value={a.id}>{a.displayName}</option>)}
        </select>
        {errors.accountId && <span className="error">{errors.accountId}</span>}
      </label>
      <label className="field span-2">Notes <span className="hint">Optional — searchable</span>
        <textarea className="textarea" value={form.notes} maxLength={1000} onChange={(e) => set({ notes: e.target.value })} />
        {errors.notes && <span className="error">{errors.notes}</span>}
      </label>
    </div>
  );
}

function validate(form: FormState): Record<string, string> {
  const errors: Record<string, string> = {};
  if (!form.merchant.trim()) errors.merchant = "Enter a merchant";
  const amount = Number(form.amount);
  if (!form.amount || !Number.isFinite(amount) || amount <= 0) errors.amount = "Enter an amount above zero";
  if (!form.date) errors.date = "Choose a date";
  if (form.type === "UNKNOWN") errors.type = "Choose a type";
  return errors;
}

function fieldErrors(error: unknown): Record<string, string> {
  return error instanceof ApiError ? error.fields : {};
}

function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : "Something went wrong";
}

function AddTransactionModal({ onClose, onCreated }: { onClose: () => void; onCreated: (id: string) => void }) {
  const accounts = useApi<Account[]>("/accounts");
  const [form, setForm] = useState<FormState>({ merchant: "", amount: "", type: "EXPENSE", direction: "DEBIT", category: "OTHER", accountId: "", date: isoDate(new Date()), notes: "" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [failure, setFailure] = useState("");
  const [saving, setSaving] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    const local = validate(form);
    setErrors(local);
    if (Object.keys(local).length) return;
    setSaving(true);
    setFailure("");
    try {
      const created = await api.post<TransactionDetail>("/transactions", {
        merchant: form.merchant.trim(), amount: Number(form.amount), type: form.type, category: form.category, date: form.date,
        accountId: form.accountId || null, direction: form.type === "TRANSFER" ? form.direction : null, notes: form.notes.trim() || null,
      });
      notifyDataChanged();
      onCreated(created.id);
    } catch (error) {
      setErrors(fieldErrors(error));
      setFailure(errorMessage(error));
      setSaving(false);
    }
  }

  return (
    <Modal title="Add transaction" subtitle="For cash and anything your device didn't capture." onClose={onClose}>
      <form onSubmit={submit} className="stack" noValidate>
        <TransactionFields form={form} setForm={setForm} accounts={accounts.data ?? []} errors={errors} />
        {failure && <p className="error" style={{ color: "var(--red)", margin: 0, fontSize: 13 }}>{failure}</p>}
        <div className="form-actions">
          <button type="button" className="btn ghost" onClick={onClose}>Cancel</button>
          <button className="btn primary" disabled={saving}>{saving ? "Adding…" : "Add transaction"}</button>
        </div>
      </form>
    </Modal>
  );
}

function TransactionDrawer({ id, onClose, onNavigate, toast }: { id: string; onClose: () => void; onNavigate: (id: string) => void; toast: (message: string, tone?: "error") => void }) {
  const { data, error, loading, reload } = useApi<TransactionDetail>(`/transactions/${id}`);
  const [transaction, setTransaction] = useState<TransactionDetail>();
  const [editing, setEditing] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => { if (data) setTransaction(data); }, [data]);

  const mutate = useCallback(async (action: () => Promise<TransactionDetail | void>, message: string) => {
    setBusy(true);
    try {
      const updated = await action();
      if (updated) setTransaction(updated);
      notifyDataChanged();
      toast(message);
      return true;
    } catch (cause) {
      toast(errorMessage(cause), "error");
      return false;
    } finally {
      setBusy(false);
    }
  }, [toast]);

  if (!transaction) {
    return (
      <Drawer label="Transaction" onClose={onClose}>
        <div className="drawer-head"><div style={{ flex: 1 }} /><button className="icon-btn" onClick={onClose} aria-label="Close"><Icon name="close" /></button></div>
        <div className="drawer-body">
          {error ? (error.status === 404 ? <p className="muted">This transaction no longer exists.</p> : <ErrorBanner onRetry={reload} />)
            : loading && <div className="stack"><Skeleton height={34} width="50%" /><Skeleton height={14} width="70%" /><Skeleton height={120} /></div>}
        </div>
      </Drawer>
    );
  }

  const t = transaction;
  const info = category(t.category);
  return (
    <Drawer label={`Transaction: ${t.merchant}`} onClose={onClose}>
      <div className="drawer-head">
        <CategoryIcon categoryKey={t.category} label={t.merchant} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <strong style={{ fontSize: 16, display: "block", overflowWrap: "anywhere" }}>{t.merchant}</strong>
          <span className="tx-meta">{fullDate(t.date)} · {info.label}</span>
        </div>
        <button className="icon-btn" onClick={onClose} aria-label="Close"><Icon name="close" /></button>
      </div>

      {editing ? (
        <EditForm transaction={t} busy={busy} onCancel={() => setEditing(false)} onSave={async (body) => {
          setBusy(true);
          try {
            const updated = await api.patch<TransactionDetail>(`/transactions/${t.id}`, body);
            setTransaction(updated);
            setEditing(false);
            notifyDataChanged();
            toast(body.applyToMerchant ? `Saved — ${updated.merchant} will use this category from now on` : "Saved");
          } finally {
            setBusy(false);
          }
        }} />
      ) : (
        <>
          <div className="drawer-body">
            <div>
              <SignedAmount amount={t.amount} type={t.type} direction={t.direction} className="detail-amount" />
              <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 10 }}>
                <Badge>{TYPE_LABELS[t.type]}</Badge>
                {t.requiresReview && <Badge tone="review">Needs review</Badge>}
                {t.duplicateOfId && <Badge tone="duplicate">Duplicate</Badge>}
                {t.extractionMethod === "MANUAL" && <Badge tone="manual">Manual</Badge>}
                {t.userEdited && <Badge>Edited</Badge>}
              </div>
            </div>

            {t.duplicateOfId && (
              <div className="callout" style={{ borderColor: "#2f4466", background: "#161d27" }}>
                <strong style={{ color: "var(--blue)" }}>Likely a duplicate.</strong> Another message reported the same payment, so this one is excluded from totals.
                <div style={{ display: "flex", gap: 8, marginTop: 10, flexWrap: "wrap" }}>
                  <button className="btn small" onClick={() => onNavigate(t.duplicateOfId!)}>View original</button>
                  <button className="btn small" disabled={busy} onClick={() => mutate(() => api.post<TransactionDetail>(`/transactions/${t.id}/not-duplicate`), "Marked as a separate payment")}>Not a duplicate</button>
                </div>
              </div>
            )}

            {t.requiresReview && (
              <div className="callout">
                <strong>Needs a quick check.</strong>
                <ul>{t.reviewReasons.map((r) => <li key={r.code}>{r.description}</li>)}{t.reviewReasons.length === 0 && <li>Flagged for review</li>}</ul>
                <div style={{ display: "flex", gap: 8, marginTop: 10, flexWrap: "wrap" }}>
                  <button className="btn small primary" disabled={busy} onClick={() => mutate(() => api.patch<TransactionDetail>(`/transactions/${t.id}`, { reviewed: true }), "Marked as reviewed")}><Icon name="check" />Looks right</button>
                  <button className="btn small" onClick={() => setEditing(true)}><Icon name="edit" />Fix details</button>
                </div>
              </div>
            )}

            <div>
              <p className="section-title">Details</p>
              <dl className="kv">
                <dt>Category</dt><dd><span style={{ display: "inline-flex", alignItems: "center", gap: 7 }}><i className="swatch" style={{ background: info.color, borderRadius: "50%", width: 8, height: 8 }} />{info.label}</span></dd>
                <dt>Account</dt><dd>{t.accountName ?? <span className="dim">Not linked</span>}</dd>
                <dt>Paid via</dt><dd>{CHANNEL_LABELS[t.channel] ?? t.channel}</dd>
                <dt>Date</dt><dd>{fullDate(t.date)}{t.extractionMethod !== "MANUAL" && <span className="dim"> · received {dateTime(t.occurredAt)}</span>}</dd>
                <dt>Source</dt><dd>{t.extractionMethod === "LLM" ? `Read by AI${t.confidence !== null ? ` · ${Math.round(t.confidence * 100)}% confident` : ""}` : t.extractionMethod === "RULES" ? "Read by fallback rules" : "Added manually"}</dd>
                {t.notes && <><dt>Notes</dt><dd style={{ whiteSpace: "pre-wrap" }}>{t.notes}</dd></>}
              </dl>
            </div>

            {t.source && (
              <div>
                <p className="section-title">Original message</p>
                <div className="message-meta">
                  <span>{t.source.sender ?? t.source.source}</span><span>{dateTime(t.source.receivedAt)}</span>
                </div>
                <div className="message">{t.source.title ? `${t.source.title}\n` : ""}{t.source.body}</div>
                {t.source.reason && <p className="dim" style={{ fontSize: 12, margin: "8px 0 0" }}>{t.source.reason}</p>}
              </div>
            )}
          </div>
          <div className="drawer-foot">
            {confirmDelete ? (
              <>
                <span style={{ fontSize: 13, alignSelf: "center", marginRight: "auto" }}>Delete this transaction?{t.source ? " The message stays in the inbox." : ""}</span>
                <button className="btn ghost" onClick={() => setConfirmDelete(false)}>Keep</button>
                <button className="btn danger" disabled={busy} onClick={async () => {
                  const ok = await mutate(() => api.delete(`/transactions/${t.id}`), "Transaction deleted");
                  if (ok) onClose();
                }}><Icon name="trash" />Delete</button>
              </>
            ) : (
              <>
                <button className="btn primary" onClick={() => setEditing(true)}><Icon name="edit" />Edit</button>
                <button className="btn danger" style={{ marginLeft: "auto" }} onClick={() => setConfirmDelete(true)}><Icon name="trash" />Delete</button>
              </>
            )}
          </div>
        </>
      )}
    </Drawer>
  );
}

type PatchBody = Record<string, unknown> & { applyToMerchant?: boolean };

function EditForm({ transaction: t, busy, onCancel, onSave }: { transaction: TransactionDetail; busy: boolean; onCancel: () => void; onSave: (body: PatchBody) => Promise<void> }) {
  const accounts = useApi<Account[]>("/accounts");
  const initial: FormState = { merchant: t.merchant, amount: String(t.amount), type: t.type, direction: t.direction, category: t.category, accountId: t.accountId ?? "", date: t.date, notes: t.notes ?? "" };
  const [form, setForm] = useState<FormState>(initial);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [applyToMerchant, setApplyToMerchant] = useState(false);
  const [markReviewed, setMarkReviewed] = useState(t.requiresReview);
  const [failure, setFailure] = useState("");
  const categoryChanged = form.category !== t.category;
  const canApplyRule = categoryChanged && form.merchant.trim() !== "" && form.merchant.trim().toLowerCase() !== "unknown merchant";

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    const local = validate(form);
    setErrors(local);
    if (Object.keys(local).length) return;
    const body: PatchBody = {};
    if (form.merchant.trim() !== t.merchant) body.merchant = form.merchant.trim();
    if (Number(form.amount) !== t.amount) body.amount = Number(form.amount);
    if (form.type !== t.type) body.type = form.type;
    if (form.type === "TRANSFER" && form.direction !== t.direction) body.direction = form.direction;
    if (categoryChanged) body.category = form.category;
    if (form.accountId !== (t.accountId ?? "")) body.accountId = form.accountId || null;
    if (form.date !== t.date) body.date = form.date;
    if (form.notes.trim() !== (t.notes ?? "")) body.notes = form.notes.trim() || null;
    if (canApplyRule && applyToMerchant) body.applyToMerchant = true;
    if (t.requiresReview && markReviewed) body.reviewed = true;
    if (Object.keys(body).length === 0) { onCancel(); return; }
    setFailure("");
    try {
      await onSave(body);
    } catch (error) {
      setErrors(fieldErrors(error));
      setFailure(errorMessage(error));
    }
  }

  return (
    <form onSubmit={submit} noValidate style={{ display: "contents" }}>
      <div className="drawer-body">
        <TransactionFields form={form} setForm={setForm} accounts={accounts.data ?? []} errors={errors} />
        {canApplyRule && (
          <label className="checkbox"><input type="checkbox" checked={applyToMerchant} onChange={(e) => setApplyToMerchant(e.target.checked)} />
            Use {category(form.category).label} for all {form.merchant.trim()} transactions</label>
        )}
        {t.requiresReview && (
          <label className="checkbox"><input type="checkbox" checked={markReviewed} onChange={(e) => setMarkReviewed(e.target.checked)} />Mark as reviewed</label>
        )}
        {failure && <p style={{ color: "var(--red)", margin: 0, fontSize: 13 }} role="alert">{failure}</p>}
      </div>
      <div className="drawer-foot">
        <button type="button" className="btn ghost" onClick={onCancel}>Cancel</button>
        <button className="btn primary" style={{ marginLeft: "auto" }} disabled={busy}>{busy ? "Saving…" : "Save changes"}</button>
      </div>
    </form>
  );
}
