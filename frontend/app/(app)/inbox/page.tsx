"use client";

import { Suspense, useCallback, useEffect, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Icon } from "@/components/Icon";
import { useTransactionDrawer } from "@/components/TransactionDrawer";
import { Badge, EmptyState, ErrorBanner, Skeleton, SkeletonRows, useToast } from "@/components/ui";
import { api, ApiError, query } from "@/lib/api";
import { KIND_LABELS, STATUS_LABELS } from "@/lib/categories";
import { ago, dateTime, plural } from "@/lib/format";
import { notifyDataChanged, useApi, useDataChanged } from "@/lib/useApi";
import type { EventStats, InboxItem, Page, ProcessingStatus } from "@/lib/types";

export default function InboxPage() {
  return <Suspense><Inbox /></Suspense>;
}

const PAGE_SIZE = 25;
const PENDING: ProcessingStatus[] = ["RECEIVED", "PROCESSING", "RETRY"];
const TABS: { key: string; label: string; statuses: ProcessingStatus[] }[] = [
  { key: "all", label: "All", statuses: [] },
  { key: "transactions", label: "Transactions", statuses: ["PROCESSED"] },
  { key: "ignored", label: "Ignored", statuses: ["IGNORED"] },
  { key: "pending", label: "Pending", statuses: PENDING },
  { key: "failed", label: "Failed", statuses: ["FAILED"] },
];
const STATUS_TONES: Partial<Record<ProcessingStatus, "good" | "bad" | "warn">> = { PROCESSED: "good", FAILED: "bad", RETRY: "warn", RECEIVED: "warn", PROCESSING: "warn" };

function Inbox() {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const [toast, showToast] = useToast();
  const tab = TABS.find((t) => t.key === params.get("tab")) ?? TABS[0];
  const q = params.get("q") ?? "";
  const page = Math.max(0, Number(params.get("page") ?? 0) || 0);
  const [search, setSearch] = useState(q);
  const [expanded, setExpanded] = useState<string | null>(null);
  const [bulkBusy, setBulkBusy] = useState(false);

  const update = useCallback((changes: Record<string, string | null>) => {
    const next = new URLSearchParams(params.toString());
    for (const [key, value] of Object.entries(changes)) { if (value) next.set(key, value); else next.delete(key); }
    if (!("page" in changes)) next.delete("page");
    const text = next.toString();
    router.replace(text ? `${pathname}?${text}` : pathname, { scroll: false });
  }, [params, pathname, router]);

  useEffect(() => setSearch(q), [q]);
  useEffect(() => {
    if (search.trim() === q) return;
    const timer = window.setTimeout(() => update({ q: search.trim() || null }), 300);
    return () => window.clearTimeout(timer);
  }, [search, q, update]);

  const stats = useApi<EventStats>("/events/stats");
  const list = useApi<Page<InboxItem>>(`/events${query({ status: tab.statuses.join(","), q, page, size: PAGE_SIZE })}`);
  const reloadAll = useCallback(() => { stats.reload(); list.reload(); }, [stats.reload, list.reload]);
  useDataChanged(reloadAll);

  const pendingCount = stats.data ? PENDING.reduce((sum, s) => sum + stats.data![s], 0) : 0;
  useEffect(() => {
    if (pendingCount === 0) return;
    const timer = window.setInterval(reloadAll, 10_000);
    return () => window.clearInterval(timer);
  }, [pendingCount, reloadAll]);

  const count = (statuses: ProcessingStatus[]) => {
    if (!stats.data) return null;
    const keys = statuses.length ? statuses : (Object.keys(stats.data) as ProcessingStatus[]);
    return keys.reduce((sum, s) => sum + (stats.data![s] ?? 0), 0);
  };

  async function bulk(scope: "RULES" | "FAILED") {
    setBulkBusy(true);
    try {
      const result = await api.post<{ scheduled: number }>(`/events/reprocess?scope=${scope}`);
      notifyDataChanged();
      showToast(result.scheduled ? `${plural(result.scheduled, "message")} queued to be read again` : scope === "FAILED" ? "No failed messages to retry" : "No rule-based transactions to re-extract");
    } catch (error) {
      showToast(error instanceof ApiError ? error.message : "Could not queue messages", "error");
    } finally {
      setBulkBusy(false);
    }
  }

  const data = list.data;
  return (
    <>
      <div className="page-head">
        <div><span className="eyebrow">Inbox</span><h2>Every message, explained.</h2><p>Everything your device forwarded and what Flowmint did with it.</p></div>
        <div className="page-actions">
          <button className="btn" disabled={bulkBusy} onClick={() => bulk("RULES")} title="Read transactions that the fallback rules extracted again with the AI. Edited transactions are kept.">
            <Icon name="sparkle" />Re-extract rule-based
          </button>
          <button className="btn" disabled={bulkBusy || (stats.data?.FAILED ?? 0) === 0} onClick={() => bulk("FAILED")}><Icon name="refresh" />Retry failed</button>
        </div>
      </div>

      {(list.error || stats.error) && (list.error ?? stats.error)!.status !== 401 && <ErrorBanner onRetry={reloadAll} />}
      {pendingCount > 0 && (
        <div className="banner info"><span className="status-dot pending" /><div><strong>Reading {plural(pendingCount, "message")}…</strong><br /><span>This page refreshes every 10 seconds until they're done.</span></div></div>
      )}

      <div className="tabs" role="tablist">
        {TABS.map((t) => (
          <button key={t.key} role="tab" aria-selected={t.key === tab.key} className={t.key === tab.key ? "selected" : ""} onClick={() => { setExpanded(null); update({ tab: t.key === "all" ? null : t.key }); }}>
            {t.label}<span className="count">{count(t.statuses) ?? "·"}</span>
          </button>
        ))}
      </div>

      <div className="panel">
        <div style={{ marginBottom: 12 }}>
          <span className="input-icon"><Icon name="search" /><input className="input" type="search" placeholder="Search message text or sender" value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Search messages" /></span>
        </div>
        {!data ? <SkeletonRows rows={6} />
          : data.items.length === 0 ? (
            q || tab.key !== "all"
              ? <EmptyState icon="search" title="No messages here">{q ? `Nothing matches “${q}”.` : `No ${tab.label.toLowerCase()} messages.`}</EmptyState>
              : <EmptyState icon="inbox" title="No messages yet">Install the Flowmint forwarder on your phone. Bank SMS and payment app notifications will appear here as they arrive.</EmptyState>
          ) : (
            <div style={{ opacity: list.loading ? 0.7 : 1 }}>
              {data.items.map((item) => (
                <InboxRow key={item.id} item={item} expanded={expanded === item.id} onToggle={() => setExpanded(expanded === item.id ? null : item.id)}
                  onReprocessed={(message, tone) => { showToast(message, tone); if (!tone) notifyDataChanged(); }} />
              ))}
            </div>
          )}
        {data && data.totalPages > 1 && (
          <div className="pager">
            <span>Page {data.page + 1} of {data.totalPages} · {data.totalItems} messages</span>
            <div className="pager-buttons">
              <button className="btn small" disabled={data.page === 0} onClick={() => update({ page: String(data.page - 1) })}><Icon name="chevronLeft" />Previous</button>
              <button className="btn small" disabled={data.page + 1 >= data.totalPages} onClick={() => update({ page: String(data.page + 1) })}>Next<Icon name="chevronRight" /></button>
            </div>
          </div>
        )}
      </div>
      {toast}
    </>
  );
}

function InboxRow({ item, expanded, onToggle, onReprocessed }: { item: InboxItem; expanded: boolean; onToggle: () => void; onReprocessed: (message: string, tone?: "error") => void }) {
  const drawer = useTransactionDrawer();
  const detail = useApi<InboxItem>(expanded ? `/events/${item.id}` : null);
  const [busy, setBusy] = useState(false);
  const pending = PENDING.includes(item.status);

  async function reprocess() {
    setBusy(true);
    try {
      await api.post(`/events/${item.id}/reprocess`);
      onReprocessed("Queued — the message will be read again in a moment");
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        onReprocessed("You edited the transaction from this message, so it's kept as is and not re-read.", "error");
      } else {
        onReprocessed(error instanceof ApiError ? error.message : "Could not reprocess", "error");
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="inbox-row">
      <button className="inbox-summary" onClick={onToggle} aria-expanded={expanded}>
        <span className="inbox-line">
          <strong>{item.sender ?? item.title ?? item.source}</strong>
          {item.kind && <Badge>{KIND_LABELS[item.kind] ?? item.kind}</Badge>}
          <Badge tone={STATUS_TONES[item.status]}>{STATUS_LABELS[item.status]}</Badge>
          {item.transactionId && <Badge tone="good">Transaction</Badge>}
        </span>
        <span className="tx-meta" title={dateTime(item.receivedAt)}>{ago(item.receivedAt)}</span>
        <span className="inbox-preview">{item.preview}</span>
        {item.reason && <span className="tx-meta" style={{ gridColumn: "1 / -1", whiteSpace: "normal" }}>{item.reason}</span>}
      </button>
      {expanded && (
        <div className="inbox-detail">
          <div className="message">{detail.data ? `${detail.data.title ? detail.data.title + "\n" : ""}${detail.data.body ?? ""}` : <Skeleton height={40} />}</div>
          <dl className="kv">
            <dt>Source</dt><dd>{item.source}{item.packageName ? ` · ${item.packageName}` : ""}</dd>
            <dt>Received</dt><dd>{dateTime(item.receivedAt)}</dd>
            <dt>Attempts</dt><dd>{item.attempts}{item.status === "RETRY" && item.nextAttemptAt ? ` · next try ${dateTime(item.nextAttemptAt)}` : ""}</dd>
            {item.lastError && <><dt>Last error</dt><dd className="bad">{item.lastError}</dd></>}
          </dl>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            {item.transactionId && <button className="btn small primary" onClick={() => drawer.open(item.transactionId!)}>Open transaction</button>}
            <button className="btn small" disabled={busy || pending} onClick={reprocess} title={pending ? "Already being read" : "Read this message again"}>
              <Icon name="refresh" />{pending ? "Processing…" : "Reprocess"}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
