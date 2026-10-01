"use client";

import Link from "next/link";
import { Suspense, useCallback, useMemo } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Donut, ProgressBar, Sparkline, BUDGET_STATUS_LABELS, BUDGET_STATUS_TONES, type DonutSlice } from "@/components/charts";
import { Icon } from "@/components/Icon";
import { Delta, MetricCard } from "@/components/metrics";
import { useTransactionDrawer } from "@/components/TransactionDrawer";
import { Badge, EmptyState, ErrorBanner, Skeleton, SkeletonRows, TransactionRow, SignedAmount } from "@/components/ui";
import { query } from "@/lib/api";
import { category, CADENCE_LABELS, OTHER_SLICE_COLOR } from "@/lib/categories";
import { money, percent, plural, relativeDay, shortDate } from "@/lib/format";
import { OVERVIEW_RANGES, RANGE_LABELS, currentMonth, isRangeKey, rangeDates, rangeDescription, type RangeKey } from "@/lib/ranges";
import { useApi, useDataChanged } from "@/lib/useApi";
import type { AnalyticsSummary, BudgetMonth, Page, RecurringOverview, TransactionItem } from "@/lib/types";

export default function OverviewPage() {
  return <Suspense><Overview /></Suspense>;
}

const DONUT_SLICES = 5;
const UPCOMING_DAYS = 14;

function Overview() {
  const router = useRouter();
  const params = useSearchParams();
  const drawer = useTransactionDrawer();
  const rangeParam = params.get("range");
  const range: RangeKey = isRangeKey(rangeParam, OVERVIEW_RANGES) ? rangeParam : "30D";
  const { from, to } = rangeDates(range);

  const summary = useApi<AnalyticsSummary>(`/analytics/summary${query({ from, to })}`);
  const recent = useApi<Page<TransactionItem>>("/transactions?size=6");
  const review = useApi<Page<TransactionItem>>("/transactions?review=true&size=3");
  const budgets = useApi<BudgetMonth>(`/budgets${query({ month: currentMonth() })}`);
  const recurring = useApi<RecurringOverview>("/recurring");

  const reloadAll = useCallback(() => {
    summary.reload(); recent.reload(); review.reload(); budgets.reload(); recurring.reload();
  }, [summary.reload, recent.reload, review.reload, budgets.reload, recurring.reload]);
  useDataChanged(reloadAll);

  const failed = [summary, recent, review, budgets, recurring].some((s) => s.error && s.error.status !== 401);
  const noTransactions = recent.data?.totalItems === 0;
  const totals = summary.data?.totals;
  const previous = summary.data?.previous;

  const slices = useMemo<DonutSlice[]>(() => {
    const categories = summary.data?.categories ?? [];
    const top = categories.slice(0, DONUT_SLICES).map((c) => ({ key: c.category, label: category(c.category).label, value: c.amount, color: category(c.category).color }));
    const rest = categories.slice(DONUT_SLICES).reduce((sum, c) => sum + c.amount, 0);
    return rest > 0 ? [...top, { key: "__other", label: "Other categories", value: rest, color: OTHER_SLICE_COLOR }] : top;
  }, [summary.data]);
  const grossSpending = slices.reduce((sum, s) => sum + s.value, 0);

  return (
    <>
      <div className="page-head">
        <div><span className="eyebrow">Overview</span><h2>Your money, in focus.</h2><p>Showing the {rangeDescription(range)} · {shortDate(from)} – {shortDate(to)}</p></div>
        <div className="segmented" role="group" aria-label="Date range">
          {OVERVIEW_RANGES.map((key) => (
            <button key={key} className={key === range ? "selected" : ""} aria-pressed={key === range}
              onClick={() => router.replace(key === "30D" ? "/" : `/?range=${key}`, { scroll: false })}>{RANGE_LABELS[key]}</button>
          ))}
        </div>
      </div>

      {failed && <ErrorBanner onRetry={reloadAll} />}

      {noTransactions && (
        <div className="panel" style={{ marginBottom: 14 }}>
          <EmptyState icon="inbox" title="No transactions yet" action={<button className="btn primary" onClick={drawer.openAdd}><Icon name="plus" />Add one manually</button>}>
            Bank and card messages forwarded from your device become transactions here automatically. You can also add cash spending by hand.
          </EmptyState>
        </div>
      )}

      <section className="metrics" aria-label="Key figures">
        <MetricCard feature label="Net cash flow" loading={!totals} value={totals && money(totals.netCashFlow)}
          foot={totals && previous && <><Delta current={totals.netCashFlow} previous={previous.netCashFlow} increaseIsGood /> <span>income − spending − invested</span></>}>
          {summary.data && <Sparkline values={summary.data.series.map((p) => p.income - p.spending)} label="Net cash flow trend" />}
        </MetricCard>
        <MetricCard label="Income" loading={!totals} value={totals && money(totals.income)}
          foot={totals && previous && <Delta current={totals.income} previous={previous.income} increaseIsGood />} />
        <MetricCard label="Spending" loading={!totals} value={totals && money(totals.spending)}
          foot={totals && previous && <><Delta current={totals.spending} previous={previous.spending} increaseIsGood={false} /><span>{plural(totals.transactionCount, "transaction")}</span></>} />
        <MetricCard label="Savings rate" loading={!totals} value={totals && percent(totals.savingsRate)}
          foot={totals && previous && (totals.savingsRate === null ? <span>No income in this period</span> : <Delta current={totals.savingsRate} previous={previous.savingsRate} increaseIsGood points />)} />
      </section>

      <section className="grid-main" style={{ marginBottom: 14 }}>
        <article className="panel">
          <div className="panel-head">
            <div><span className="eyebrow">Where it goes</span><h3>Spending breakdown</h3></div>
            <Link className="link" href={`/analytics?range=${range}`}>View analytics →</Link>
          </div>
          {!summary.data ? <div className="donut-wrap"><Skeleton width={168} height={168} style={{ borderRadius: "50%", flex: "none" }} /><div style={{ flex: 1 }}><SkeletonRows rows={3} /></div></div>
            : slices.length === 0 ? <EmptyState icon="budgets" title="No spending in this period">Expenses and cash withdrawals will be broken down by category here.</EmptyState>
            : <Donut slices={slices} total={grossSpending} centerLabel="spent" />}
        </article>

        <article className="panel">
          <div className="panel-head">
            <div><span className="eyebrow">Needs attention</span><h3>Review queue</h3></div>
            {review.data && review.data.totalItems > 0 && <Badge tone="review">{review.data.totalItems}</Badge>}
          </div>
          {!review.data ? <SkeletonRows rows={2} />
            : review.data.totalItems === 0 ? <EmptyState icon="check" title="All clear">No transactions need review.</EmptyState>
            : (
              <div className="stack">
                <div className="attention">
                  <span className="review-icon">!</span>
                  <div><strong>{plural(review.data.totalItems, "transaction")} need{review.data.totalItems === 1 ? "s" : ""} context</strong><p>Confirm or fix them so your totals stay accurate.</p></div>
                </div>
                <div>
                  {review.data.items.map((t) => (
                    <button key={t.id} className="review-item" onClick={() => drawer.open(t.id)}>
                      <span style={{ flex: 1, minWidth: 0 }}>
                        <strong style={{ fontSize: 13, display: "block", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{t.merchant}</strong>
                        <span className="tx-meta">{category(t.category).label} · {shortDate(t.date)}</span>
                      </span>
                      <SignedAmount amount={t.amount} type={t.type} direction={t.direction} />
                    </button>
                  ))}
                </div>
                <Link className="link" href="/transactions?review=true">Review all {review.data.totalItems} →</Link>
              </div>
            )}
        </article>
      </section>

      <section className="grid-main">
        <article className="panel">
          <div className="panel-head">
            <div><span className="eyebrow">Latest activity</span><h3>Recent transactions</h3></div>
            <Link className="link" href="/transactions">See all →</Link>
          </div>
          {!recent.data ? <SkeletonRows rows={6} />
            : recent.data.items.length === 0 ? <EmptyState icon="transactions" title="Nothing here yet">Your latest transactions will appear here.</EmptyState>
            : <div className="tx-list">{recent.data.items.map((t) => <TransactionRow key={t.id} transaction={t} onOpen={drawer.open} />)}</div>}
        </article>

        <div className="stack" style={{ alignContent: "start" }}>
          <BudgetHighlights data={budgets.data} />
          <UpcomingRecurring data={recurring.data} />
        </div>
      </section>
    </>
  );
}

function BudgetHighlights({ data }: { data: BudgetMonth | undefined }) {
  const rows = !data ? [] : data.total ? [data.total] : data.budgets.slice(0, 3);
  const pace = data && data.daysElapsed > 0 ? data.daysElapsed / data.daysInMonth : undefined;
  return (
    <article className="panel">
      <div className="panel-head">
        <div><span className="eyebrow">This month</span><h3>Budgets</h3></div>
        <Link className="link" href="/budgets">Manage →</Link>
      </div>
      {!data ? <div className="stack"><Skeleton height={14} width="60%" /><Skeleton height={8} /><Skeleton height={14} width="40%" /></div>
        : rows.length === 0 ? <EmptyState icon="budgets" title="No budgets yet" action={<Link className="btn primary small" href="/budgets">Set a budget</Link>}>Set monthly limits to see how your spending is pacing.</EmptyState>
        : rows.map((b) => (
          <div key={b.category} className="budget-row">
            <div className="budget-top">
              <span className="budget-name">{b.category === "TOTAL" ? "All spending" : category(b.category).label}</span>
              <Badge tone={BUDGET_STATUS_TONES[b.status]}>{BUDGET_STATUS_LABELS[b.status]}</Badge>
            </div>
            <ProgressBar value={b.spent} limit={b.limit} status={b.status} pace={pace} label={`${b.category} budget used`} />
            <div className="budget-figures"><span><b>{money(b.spent)}</b> of {money(b.limit)}</span><span>{b.remaining >= 0 ? `${money(b.remaining)} left` : `${money(-b.remaining)} over`}</span></div>
          </div>
        ))}
    </article>
  );
}

function UpcomingRecurring({ data }: { data: RecurringOverview | undefined }) {
  const upcoming = (data?.items ?? [])
    .filter((s) => s.status === "ACTIVE" && !s.dismissed && relativeDay(s.nextExpectedDate).days <= UPCOMING_DAYS)
    .slice(0, 5);
  const total = upcoming.reduce((sum, s) => sum + s.amount, 0);
  return (
    <article className="panel">
      <div className="panel-head">
        <div><span className="eyebrow">Next {UPCOMING_DAYS} days</span><h3>Upcoming payments</h3>{upcoming.length > 0 && <div className="sub">{money(total)} expected</div>}</div>
        <Link className="link" href="/recurring">All recurring →</Link>
      </div>
      {!data ? <SkeletonRows rows={2} />
        : upcoming.length === 0 ? <EmptyState icon="recurring" title="Nothing due soon" action={data.items.length === 0 ? <Link className="btn small" href="/recurring">How detection works</Link> : undefined}>
            {data.items.length === 0 ? "Subscriptions and EMIs are detected automatically once they repeat." : `No recurring payments are expected in the next ${UPCOMING_DAYS} days.`}
          </EmptyState>
        : upcoming.map((s) => {
          const when = relativeDay(s.nextExpectedDate);
          return (
            <div key={s.key} className="upcoming">
              <strong style={{ fontWeight: 600 }}>{s.merchant}</strong>
              <span className="amount">{money(s.amount)}</span>
              <small>{CADENCE_LABELS[s.cadence]}{s.accountName ? ` · ${s.accountName}` : ""}</small>
              <small className={when.days < 0 ? "warn" : ""} style={{ textAlign: "right" }}>{when.label}</small>
            </div>
          );
        })}
    </article>
  );
}
