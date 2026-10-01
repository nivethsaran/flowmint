"use client";

import Link from "next/link";
import { useState } from "react";
import { Icon } from "@/components/Icon";
import { MetricCard } from "@/components/metrics";
import { Badge, CategoryDot, EmptyState, ErrorBanner, SkeletonRows, useToast } from "@/components/ui";
import { api, ApiError, query } from "@/lib/api";
import { CADENCE_LABELS, category } from "@/lib/categories";
import { money, plural, relativeDay, shortDate } from "@/lib/format";
import { notifyDataChanged, useApi, useDataChanged } from "@/lib/useApi";
import type { RecurringOverview, RecurringSeries } from "@/lib/types";

export default function RecurringPage() {
  const [showDismissed, setShowDismissed] = useState(false);
  const data = useApi<RecurringOverview>(`/recurring${query({ includeDismissed: showDismissed || null })}`);
  useDataChanged(data.reload);
  const [toast, showToast] = useToast();
  const [busy, setBusy] = useState<string | null>(null);
  const items = data.data?.items ?? [];
  const next = items.filter((s) => s.status === "ACTIVE" && !s.dismissed).sort((a, b) => a.nextExpectedDate.localeCompare(b.nextExpectedDate))[0];
  const active = items.filter((s) => !s.dismissed && s.status === "ACTIVE");
  const lapsed = items.filter((s) => !s.dismissed && s.status === "LAPSED");
  const dismissed = items.filter((s) => s.dismissed);

  async function toggle(series: RecurringSeries) {
    setBusy(series.key);
    try {
      if (series.dismissed) await api.delete(`/recurring/${series.key}/dismiss`);
      else await api.post(`/recurring/${series.key}/dismiss`);
      notifyDataChanged();
      showToast(series.dismissed ? `${series.merchant} restored` : `${series.merchant} hidden from recurring`);
    } catch (error) {
      showToast(error instanceof ApiError ? error.message : "Something went wrong", "error");
    } finally {
      setBusy(null);
    }
  }

  return (
    <>
      <div className="page-head">
        <div><span className="eyebrow">Recurring</span><h2>Subscriptions & commitments.</h2><p>Detected automatically from payments that repeat on a steady schedule.</p></div>
        <div className="page-actions"><label className="checkbox"><input type="checkbox" checked={showDismissed} onChange={(e) => setShowDismissed(e.target.checked)} />Show dismissed</label></div>
      </div>

      {data.error && data.error.status !== 401 && <ErrorBanner onRetry={data.reload} />}

      <section className="summary-strip">
        <MetricCard feature label="Monthly total" loading={!data.data} value={data.data && money(data.data.monthlyTotal)} foot={<span>active series, as a monthly equivalent</span>} />
        <MetricCard label="Active" loading={!data.data} value={data.data && data.data.activeCount} foot={lapsed.length > 0 ? <span className="warn">{lapsed.length} lapsed</span> : <span>series</span>} />
        <MetricCard label="Next payment" loading={!data.data} value={next ? money(next.amount) : "—"}
          foot={next ? <span>{next.merchant} · {relativeDay(next.nextExpectedDate).label}</span> : <span>Nothing scheduled</span>} />
      </section>

      {!data.data ? <div className="panel"><SkeletonRows rows={5} /></div>
        : items.length === 0 ? (
          <div className="panel">
            <EmptyState icon="recurring" title="No recurring payments detected yet">
              A merchant shows up here once it charges you on a steady schedule (weekly, monthly, quarterly, or yearly) for a similar amount: three times,
              or twice for autopay, subscriptions, EMIs, and insurance.
            </EmptyState>
          </div>
        ) : (
          <div className="stack">
            <SeriesPanel title="Active" sub="Sorted by next expected date" items={active} busy={busy} onToggle={toggle} empty="No active series." />
            {lapsed.length > 0 && <SeriesPanel title="Lapsed" sub="Expected but not seen recently — cancelled, or paid another way?" items={lapsed} busy={busy} onToggle={toggle} />}
            {showDismissed && <SeriesPanel title="Dismissed" sub="Hidden from totals" items={dismissed} busy={busy} onToggle={toggle} empty="Nothing dismissed." />}
          </div>
        )}
      {toast}
    </>
  );
}

function SeriesPanel({ title, sub, items, busy, onToggle, empty }: { title: string; sub: string; items: RecurringSeries[]; busy: string | null; onToggle: (s: RecurringSeries) => void; empty?: string }) {
  return (
    <article className="panel">
      <div className="panel-head"><div><h3 style={{ marginTop: 0 }}>{title} <span className="dim mono" style={{ fontSize: 12 }}>{items.length}</span></h3><div className="sub">{sub}</div></div></div>
      {items.length === 0 ? <p className="dim" style={{ margin: 0, fontSize: 13 }}>{empty}</p> : (
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>Merchant</th><th>Schedule</th><th className="num">Amount</th><th>Next</th><th className="hide-sm">Account</th><th /></tr></thead>
            <tbody>
              {items.map((s) => {
                const when = relativeDay(s.nextExpectedDate);
                return (
                  <tr key={s.key} style={{ opacity: s.dismissed ? 0.6 : 1 }}>
                    <td>
                      <Link href={`/transactions${query({ q: s.merchant })}`} style={{ textDecoration: "none", display: "inline-flex", alignItems: "center", gap: 8, fontWeight: 600 }}>
                        <CategoryDot categoryKey={s.category} />{s.merchant}
                      </Link>
                      <div className="tx-meta">{category(s.category).label} · {plural(s.occurrences, "payment")} · last {shortDate(s.lastDate)}</div>
                    </td>
                    <td>{CADENCE_LABELS[s.cadence]}{s.status === "LAPSED" && <> <Badge tone="warn">Lapsed</Badge></>}</td>
                    <td className="num">
                      {money(s.amount)}
                      {s.cadence !== "MONTHLY" && <div className="tx-meta">{money(s.monthlyEquivalent)}/mo</div>}
                      {Math.abs(s.lastAmount - s.amount) >= 1 && <div className="tx-meta">last {money(s.lastAmount)}</div>}
                    </td>
                    <td className={when.days < 0 ? "warn" : ""}><span className="nowrap">{when.label}</span><div className="tx-meta">{shortDate(s.nextExpectedDate)}</div></td>
                    <td className="hide-sm muted">{s.accountName ?? "—"}</td>
                    <td style={{ textAlign: "right" }}>
                      <button className="btn small ghost" disabled={busy === s.key} onClick={() => onToggle(s)} title={s.dismissed ? "Show this series again" : "Not a recurring payment"}>
                        <Icon name={s.dismissed ? "eye" : "eyeOff"} />{s.dismissed ? "Restore" : "Dismiss"}
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </article>
  );
}
