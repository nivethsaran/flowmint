"use client";

import Link from "next/link";
import { Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { RankedBars, TrendChart } from "@/components/charts";
import { Delta, MetricCard } from "@/components/metrics";
import { EmptyState, ErrorBanner, Skeleton, SkeletonRows } from "@/components/ui";
import { query } from "@/lib/api";
import { category } from "@/lib/categories";
import { money, percent, plural, shortDate } from "@/lib/format";
import { ANALYTICS_RANGES, RANGE_LABELS, isRangeKey, rangeDates, rangeDescription, type RangeKey } from "@/lib/ranges";
import { useApi, useDataChanged } from "@/lib/useApi";
import type { AnalyticsSummary } from "@/lib/types";

export default function AnalyticsPage() {
  return <Suspense><Analytics /></Suspense>;
}

const GRANULARITY_LABELS = { DAY: "daily", WEEK: "weekly", MONTH: "monthly" };
const MERCHANT_COLOR = "#7fb2ff";

function Analytics() {
  const router = useRouter();
  const params = useSearchParams();
  const rangeParam = params.get("range");
  const range: RangeKey = isRangeKey(rangeParam, ANALYTICS_RANGES) ? rangeParam : "30D";
  const { from, to } = rangeDates(range);
  const summary = useApi<AnalyticsSummary>(`/analytics/summary${query({ from, to })}`);
  useDataChanged(summary.reload);
  const s = summary.data;
  const t = s?.totals;
  const p = s?.previous;
  const txLink = (extra: Record<string, string>) => `/transactions${query({ from, to, ...extra })}`;

  return (
    <>
      <div className="page-head">
        <div><span className="eyebrow">Analytics</span><h2>Patterns in your money.</h2><p>{shortDate(from)} – {shortDate(to)}{s && <> · compared with {shortDate(s.previousRange.from)} – {shortDate(s.previousRange.to)}</>}</p></div>
        <div className="segmented" role="group" aria-label="Date range">
          {ANALYTICS_RANGES.map((key) => (
            <button key={key} className={key === range ? "selected" : ""} aria-pressed={key === range}
              onClick={() => router.replace(`/analytics?range=${key}`, { scroll: false })}>{RANGE_LABELS[key]}</button>
          ))}
        </div>
      </div>

      {summary.error && summary.error.status !== 401 && <ErrorBanner onRetry={summary.reload} />}

      <section className="metrics five" aria-label="Totals">
        <MetricCard label="Net cash flow" feature loading={!t} value={t && money(t.netCashFlow)}
          foot={t && p && <Delta current={t.netCashFlow} previous={p.netCashFlow} increaseIsGood />} />
        <MetricCard label="Income" loading={!t} value={t && money(t.income)} foot={t && p && <Delta current={t.income} previous={p.income} increaseIsGood />} />
        <MetricCard label="Spending" loading={!t} value={t && money(t.spending)}
          foot={t && p && <><Delta current={t.spending} previous={p.spending} increaseIsGood={false} />{t.refunds > 0 && <span>after {money(t.refunds)} refunds</span>}</>} />
        <MetricCard label="Savings rate" loading={!t} value={t && percent(t.savingsRate)}
          foot={t && p && (t.savingsRate === null ? <span>No income in this period</span> : <Delta current={t.savingsRate} previous={p.savingsRate} increaseIsGood points />)} />
        <MetricCard label="Invested" loading={!t} value={t && money(t.invested)} foot={t && p && <Delta current={t.invested} previous={p.invested} increaseIsGood />} />
      </section>

      <article className="panel" style={{ marginBottom: 14 }}>
        <div className="panel-head">
          <div><span className="eyebrow">Over time</span><h3>Income vs spending</h3>{s && <div className="sub">{GRANULARITY_LABELS[s.granularity]} totals · {rangeDescription(range)}</div>}</div>
        </div>
        {!s ? <Skeleton height={240} />
          : s.totals.transactionCount === 0 ? <EmptyState icon="analytics" title="No transactions in this period">Pick a longer range, or add transactions to see trends.</EmptyState>
          : <TrendChart series={s.series} granularity={s.granularity} />}
      </article>

      <section className="grid-2" style={{ marginBottom: 14 }}>
        <article className="panel">
          <div className="panel-head">
            <div><span className="eyebrow">Categories</span><h3>Where the money went</h3><div className="sub">Expenses and cash, with change vs previous period</div></div>
          </div>
          {!s ? <SkeletonRows rows={5} />
            : s.categories.length === 0 ? <EmptyState icon="budgets" title="No spending yet" />
            : <RankedBars items={s.categories.map((c) => ({
                key: c.category, value: c.amount, previous: c.previousAmount, share: c.share, color: category(c.category).color,
                label: <><i className="swatch" style={{ background: category(c.category).color, borderRadius: "50%", width: 8, height: 8 }} />{category(c.category).label} <span className="dim mono" style={{ fontSize: 11 }}>{c.count}×</span></>,
                onClick: () => router.push(txLink({ category: c.category })),
              }))} />}
        </article>

        <article className="panel">
          <div className="panel-head">
            <div><span className="eyebrow">Merchants</span><h3>Top merchants</h3><div className="sub">By total spent</div></div>
          </div>
          {!s ? <SkeletonRows rows={5} />
            : s.topMerchants.length === 0 ? <EmptyState icon="transactions" title="No merchants yet" />
            : <RankedBars items={s.topMerchants.map((m) => ({
                key: m.merchant, value: m.amount, color: MERCHANT_COLOR,
                label: <>{m.merchant} <span className="dim mono" style={{ fontSize: 11 }}>{plural(m.count, "payment")}</span></>,
                onClick: () => router.push(txLink({ q: m.merchant })),
              }))} />}
        </article>
      </section>

      <article className="panel">
        <div className="panel-head">
          <div><span className="eyebrow">Accounts</span><h3>By account</h3><div className="sub">Spending is net of refunds; transfers between your own accounts are excluded</div></div>
          <Link className="link" href="/accounts">All accounts →</Link>
        </div>
        {!s ? <SkeletonRows rows={3} />
          : s.accounts.length === 0 ? <EmptyState icon="accounts" title="No account activity in this period" />
          : (
            <div className="table-wrap">
              <table className="table">
                <thead><tr><th>Account</th><th className="num">Spending</th><th className="num">Income</th><th className="num hide-sm">Share of spending</th></tr></thead>
                <tbody>
                  {s.accounts.map((a) => (
                    <tr key={a.accountId ?? "none"}>
                      <td>{a.accountId ? <Link href={txLink({ accountId: a.accountId })} style={{ textDecoration: "none" }}>{a.name}</Link> : <span className="muted">{a.name}</span>}</td>
                      <td className="num">{money(a.spending)}</td>
                      <td className="num good">{a.income > 0 ? money(a.income) : <span className="dim">—</span>}</td>
                      <td className="num hide-sm dim">{s.totals.spending > 0 ? percent(a.spending / s.totals.spending) : "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
      </article>
    </>
  );
}
