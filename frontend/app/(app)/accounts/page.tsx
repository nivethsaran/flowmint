"use client";

import Link from "next/link";
import { useState } from "react";
import { Icon, type IconName } from "@/components/Icon";
import { MetricCard } from "@/components/metrics";
import { EmptyState, ErrorBanner, Skeleton, useToast } from "@/components/ui";
import { api, ApiError } from "@/lib/api";
import { ago, money, plural, shortDate } from "@/lib/format";
import { notifyDataChanged, useApi, useDataChanged } from "@/lib/useApi";
import type { Account, AccountType } from "@/lib/types";

const GROUPS: { types: AccountType[]; title: string; icon: IconName }[] = [
  { types: ["BANK_ACCOUNT"], title: "Bank accounts", icon: "bank" },
  { types: ["CREDIT_CARD"], title: "Credit cards", icon: "card" },
  { types: ["WALLET"], title: "Wallets", icon: "wallet" },
  { types: ["UNKNOWN"], title: "Other accounts", icon: "accounts" },
];

export default function AccountsPage() {
  const data = useApi<Account[]>("/accounts");
  useDataChanged(data.reload);
  const [toast, showToast] = useToast();
  const accounts = data.data ?? [];
  const withBalance = accounts.filter((a) => (a.type === "BANK_ACCOUNT" || a.type === "WALLET") && a.lastKnownBalance !== null);
  const totalBalance = withBalance.reduce((sum, a) => sum + (a.lastKnownBalance ?? 0), 0);
  const monthOut = accounts.reduce((sum, a) => sum + a.monthDebits, 0);
  const monthIn = accounts.reduce((sum, a) => sum + a.monthCredits, 0);

  return (
    <>
      <div className="page-head">
        <div><span className="eyebrow">Accounts</span><h2>Banks, cards & wallets.</h2><p>Discovered automatically from your messages. Rename them so they're easy to recognise.</p></div>
      </div>

      {data.error && data.error.status !== 401 && <ErrorBanner onRetry={data.reload} />}

      {!data.data ? <div className="grid-3">{[0, 1, 2].map((i) => <div key={i} className="panel"><Skeleton height={120} /></div>)}</div>
        : accounts.length === 0 ? (
          <div className="panel"><EmptyState icon="accounts" title="No accounts yet">Accounts appear automatically when bank and card messages mention them, like “A/c XX1234” or “Card ending 5678”.</EmptyState></div>
        ) : (
          <>
            <section className="summary-strip">
              <MetricCard feature label="Stated balance" value={withBalance.length ? money(totalBalance) : "—"}
                foot={<span>{withBalance.length ? `latest balance from ${plural(withBalance.length, "bank account or wallet", "bank accounts and wallets")}` : "No balance seen in messages yet"}</span>} />
              <MetricCard label="Money out this month" value={money(monthOut)} foot={<span>excludes duplicates</span>} />
              <MetricCard label="Money in this month" value={money(monthIn)} foot={<span>across {plural(accounts.length, "account")}</span>} />
            </section>
            <div className="stack">
              {GROUPS.map((group) => {
                const members = accounts.filter((a) => group.types.includes(a.type));
                if (members.length === 0) return null;
                return (
                  <section key={group.title}>
                    <p className="section-title" style={{ margin: "8px 0 10px" }}>{group.title} · {members.length}</p>
                    <div className="grid-3">
                      {members.map((account) => (
                        <AccountCard key={account.id} account={account} icon={group.icon}
                          onRenamed={(name) => { notifyDataChanged(); showToast(`Renamed to ${name}`); }} onError={(m) => showToast(m, "error")} />
                      ))}
                    </div>
                  </section>
                );
              })}
            </div>
          </>
        )}
      {toast}
    </>
  );
}

function AccountCard({ account: a, icon, onRenamed, onError }: { account: Account; icon: IconName; onRenamed: (name: string) => void; onError: (message: string) => void }) {
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(a.displayName);
  const [saving, setSaving] = useState(false);
  const showsBalance = a.type === "BANK_ACCOUNT" || a.type === "WALLET";

  async function save(event: React.FormEvent) {
    event.preventDefault();
    const trimmed = name.trim();
    if (!trimmed || trimmed === a.displayName) { setEditing(false); setName(a.displayName); return; }
    setSaving(true);
    try {
      await api.patch(`/accounts/${a.id}`, { displayName: trimmed });
      setEditing(false);
      onRenamed(trimmed);
    } catch (error) {
      onError(error instanceof ApiError ? Object.values(error.fields)[0] ?? error.message : "Could not rename account");
    } finally {
      setSaving(false);
    }
  }

  return (
    <article className="panel account-card">
      <div className="account-top">
        <span className="account-icon"><Icon name={icon} /></span>
        <div style={{ flex: 1, minWidth: 0 }}>
          {editing ? (
            <form onSubmit={save} style={{ display: "flex", gap: 6 }}>
              <input className="input" autoFocus value={name} maxLength={80} onChange={(e) => setName(e.target.value)} aria-label="Account name"
                onKeyDown={(e) => { if (e.key === "Escape") { setEditing(false); setName(a.displayName); } }} style={{ padding: "6px 9px" }} />
              <button className="btn small primary" disabled={saving || !name.trim()}>Save</button>
            </form>
          ) : (
            <div style={{ display: "flex", alignItems: "center", gap: 4 }}>
              <span className="account-name" style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{a.displayName}</span>
              <button className="icon-btn" style={{ width: 26, height: 26 }} aria-label={`Rename ${a.displayName}`} onClick={() => setEditing(true)}><Icon name="edit" width={14} height={14} /></button>
            </div>
          )}
          <div className="account-sub">{[a.institution, a.last4 ? `••${a.last4}` : null].filter(Boolean).join(" · ") || "Unknown institution"}</div>
        </div>
      </div>
      {showsBalance && (
        <div>
          <div className="account-balance">{a.lastKnownBalance !== null ? money(a.lastKnownBalance) : <span className="dim">—</span>}</div>
          <div className="account-sub">{a.balanceAsOf ? `balance as of ${ago(a.balanceAsOf)}` : "No balance in messages yet"}</div>
        </div>
      )}
      <div className="account-stats">
        <div>Out this month<b>{money(a.monthDebits)}</b></div>
        <div>In this month<b className={a.monthCredits > 0 ? "good" : ""}>{money(a.monthCredits)}</b></div>
        <div>Last activity<b>{a.lastTransactionDate ? shortDate(a.lastTransactionDate) : "—"}</b></div>
      </div>
      <Link className="link" href={`/transactions?accountId=${a.id}`}>{plural(a.transactionCount, "transaction")} →</Link>
    </article>
  );
}
