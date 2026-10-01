"use client";

import { Suspense, useCallback, useEffect, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Icon } from "@/components/Icon";
import { useTransactionDrawer } from "@/components/TransactionDrawer";
import { EmptyState, ErrorBanner, SkeletonRows, TransactionRow } from "@/components/ui";
import { query } from "@/lib/api";
import { CATEGORIES, EDITABLE_TYPES, TYPE_LABELS, category } from "@/lib/categories";
import { money, plural, shortDate } from "@/lib/format";
import { useApi, useDataChanged } from "@/lib/useApi";
import type { Account, Page, TransactionItem } from "@/lib/types";

export default function TransactionsPage() {
  return <Suspense><Transactions /></Suspense>;
}

const PAGE_SIZE = 25;
const FILTER_KEYS = ["q", "type", "category", "accountId", "review", "from", "to", "sort", "includeDuplicates"] as const;
const SORTS: Record<string, string> = { date_desc: "Newest first", date_asc: "Oldest first", amount_desc: "Largest first", amount_asc: "Smallest first" };

function Transactions() {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const drawer = useTransactionDrawer();
  const accounts = useApi<Account[]>("/accounts");

  const filters = Object.fromEntries(FILTER_KEYS.map((key) => [key, params.get(key) ?? ""])) as Record<(typeof FILTER_KEYS)[number], string>;
  const page = Math.max(0, Number(params.get("page") ?? 0) || 0);

  const update = useCallback((changes: Record<string, string | null>, resetPage = true) => {
    const next = new URLSearchParams(params.toString());
    for (const [key, value] of Object.entries(changes)) {
      if (value === null || value === "") next.delete(key); else next.set(key, value);
    }
    if (resetPage) next.delete("page");
    const text = next.toString();
    router.replace(text ? `${pathname}?${text}` : pathname, { scroll: false });
  }, [params, pathname, router]);

  const [search, setSearch] = useState(filters.q);
  useEffect(() => setSearch(filters.q), [filters.q]);
  useEffect(() => {
    if (search.trim() === filters.q) return;
    const timer = window.setTimeout(() => update({ q: search.trim() || null }), 300);
    return () => window.clearTimeout(timer);
  }, [search, filters.q, update]);

  const path = `/transactions${query({
    q: filters.q, type: filters.type, category: filters.category, accountId: filters.accountId, review: filters.review === "true" ? "true" : "",
    from: filters.from, to: filters.to, sort: filters.sort, includeDuplicates: filters.includeDuplicates === "false" ? "false" : "", page, size: PAGE_SIZE,
  })}`;
  const list = useApi<Page<TransactionItem>>(path);
  useDataChanged(list.reload);

  const accountName = (id: string) => accounts.data?.find((a) => a.id === id)?.displayName ?? "Account";
  const chips: { key: string; label: string; clear: Record<string, null> }[] = [];
  if (filters.q) chips.push({ key: "q", label: `“${filters.q}”`, clear: { q: null } });
  if (filters.review === "true") chips.push({ key: "review", label: "Needs review", clear: { review: null } });
  if (filters.type) chips.push({ key: "type", label: TYPE_LABELS[filters.type] ?? filters.type, clear: { type: null } });
  if (filters.category) chips.push({ key: "category", label: category(filters.category).label, clear: { category: null } });
  if (filters.accountId) chips.push({ key: "accountId", label: accountName(filters.accountId), clear: { accountId: null } });
  if (filters.from || filters.to) chips.push({ key: "dates", label: `${filters.from ? shortDate(filters.from) : "…"} – ${filters.to ? shortDate(filters.to) : "today"}`, clear: { from: null, to: null } });
  if (filters.includeDuplicates === "false") chips.push({ key: "dups", label: "Duplicates hidden", clear: { includeDuplicates: null } });

  const data = list.data;
  const pageTotal = data?.items.reduce((sum, t) => sum + (t.type === "TRANSFER" || t.duplicateOfId ? 0 : t.direction === "DEBIT" ? -t.amount : t.amount), 0) ?? 0;

  return (
    <>
      <div className="page-head">
        <div><span className="eyebrow">Transactions</span><h2>Every rupee, accounted for.</h2><p>Search, filter, and fix anything the AI got wrong.</p></div>
        <div className="page-actions"><button className="btn primary" onClick={drawer.openAdd}><Icon name="plus" />Add transaction</button></div>
      </div>

      <div className="panel" style={{ marginBottom: 14 }}>
        <div className="filters">
          <label className="field filter-search"><span className="sr-only">Search</span>
            <span className="input-icon"><Icon name="search" /><input className="input" type="search" placeholder="Merchant, note, account, category or amount" value={search} onChange={(e) => setSearch(e.target.value)} /></span>
          </label>
          <label className="field"><span className="sr-only">Type</span>
            <select className="select" value={filters.type} onChange={(e) => update({ type: e.target.value })}>
              <option value="">All types</option>
              {[...EDITABLE_TYPES, "UNKNOWN"].map((t) => <option key={t} value={t}>{TYPE_LABELS[t]}</option>)}
            </select>
          </label>
          <label className="field"><span className="sr-only">Category</span>
            <select className="select" value={filters.category} onChange={(e) => update({ category: e.target.value })}>
              <option value="">All categories</option>
              {CATEGORIES.map((c) => <option key={c.key} value={c.key}>{c.label}</option>)}
            </select>
          </label>
          <label className="field"><span className="sr-only">Account</span>
            <select className="select" value={filters.accountId} onChange={(e) => update({ accountId: e.target.value })}>
              <option value="">All accounts</option>
              {accounts.data?.map((a) => <option key={a.id} value={a.id}>{a.displayName}</option>)}
            </select>
          </label>
          <label className="field"><span className="sr-only">Sort</span>
            <select className="select" value={filters.sort || "date_desc"} onChange={(e) => update({ sort: e.target.value === "date_desc" ? null : e.target.value })}>
              {Object.entries(SORTS).map(([key, label]) => <option key={key} value={key}>{label}</option>)}
            </select>
          </label>
        </div>
        <div style={{ display: "flex", gap: 14, flexWrap: "wrap", alignItems: "center" }}>
          <label className="field" style={{ gridAutoFlow: "column", alignItems: "center", gap: 8 }}>From
            <input className="input" type="date" value={filters.from} max={filters.to || undefined} onChange={(e) => update({ from: e.target.value })} style={{ width: 160 }} />
          </label>
          <label className="field" style={{ gridAutoFlow: "column", alignItems: "center", gap: 8 }}>To
            <input className="input" type="date" value={filters.to} min={filters.from || undefined} onChange={(e) => update({ to: e.target.value })} style={{ width: 160 }} />
          </label>
          <label className="checkbox"><input type="checkbox" checked={filters.review === "true"} onChange={(e) => update({ review: e.target.checked ? "true" : null })} />Needs review only</label>
          <label className="checkbox"><input type="checkbox" checked={filters.includeDuplicates === "false"} onChange={(e) => update({ includeDuplicates: e.target.checked ? "false" : null })} />Hide duplicates</label>
        </div>
      </div>

      {chips.length > 0 && (
        <div className="filter-chips">
          {chips.map((chip) => (
            <span key={chip.key} className="chip">{chip.label}<button aria-label={`Remove filter ${chip.label}`} onClick={() => update(chip.clear)}><Icon name="close" width={12} height={12} /></button></span>
          ))}
          <button className="link" onClick={() => router.replace(pathname, { scroll: false })}>Clear all</button>
        </div>
      )}

      {list.error && list.error.status !== 401 && (list.error.status === 400
        ? <ErrorBanner message="Those filters aren't valid" detail={list.error.message} />
        : <ErrorBanner onRetry={list.reload} />)}

      <article className="panel">
        <div className="panel-head" style={{ marginBottom: 6 }}>
          <div className="sub" style={{ marginTop: 0 }}>
            {data ? <>{plural(data.totalItems, "transaction")}{data.items.length > 0 && <> · net on this page <span className="mono">{money(pageTotal)}</span></>}</> : "Loading…"}
          </div>
        </div>
        {!data ? <SkeletonRows rows={8} />
          : data.items.length === 0 ? (
            chips.length > 0
              ? <EmptyState icon="search" title="No matches" action={<button className="btn small" onClick={() => router.replace(pathname, { scroll: false })}>Clear filters</button>}>Nothing matches these filters. Try a broader search.</EmptyState>
              : <EmptyState icon="inbox" title="No transactions yet" action={<button className="btn primary small" onClick={drawer.openAdd}><Icon name="plus" />Add transaction</button>}>Messages from your device will create transactions here automatically.</EmptyState>
          )
          : <div className="tx-list" style={{ opacity: list.loading ? 0.6 : 1, transition: "opacity .15s" }}>{data.items.map((t) => <TransactionRow key={t.id} transaction={t} onOpen={drawer.open} />)}</div>}
        {data && data.totalPages > 1 && (
          <div className="pager">
            <span>Page {data.page + 1} of {data.totalPages} · {data.page * data.size + 1}–{Math.min(data.totalItems, (data.page + 1) * data.size)} of {data.totalItems}</span>
            <div className="pager-buttons">
              <button className="btn small" disabled={data.page === 0} onClick={() => update({ page: String(data.page - 1) }, false)}><Icon name="chevronLeft" />Previous</button>
              <button className="btn small" disabled={data.page + 1 >= data.totalPages} onClick={() => update({ page: String(data.page + 1) }, false)}>Next<Icon name="chevronRight" /></button>
            </div>
          </div>
        )}
      </article>
    </>
  );
}
