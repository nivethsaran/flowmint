"use client";

import { useEffect, useId, useMemo, useRef, useState, type ReactNode } from "react";
import { money, moneyCompact, percent, change, shortDate } from "@/lib/format";

export const SERIES_COLORS = { income: "#6ce3bd", spending: "#f5a86c", net: "#c7f36b" };

function useWidth<T extends HTMLElement>(): [React.RefObject<T | null>, number] {
  const ref = useRef<T>(null);
  const [width, setWidth] = useState(0);
  useEffect(() => {
    const element = ref.current;
    if (!element) return;
    const observer = new ResizeObserver(([entry]) => setWidth(Math.floor(entry.contentRect.width)));
    observer.observe(element);
    setWidth(Math.floor(element.getBoundingClientRect().width));
    return () => observer.disconnect();
  }, []);
  return [ref, width];
}

/** Round axis maximum and evenly spaced ticks (1, 2, 2.5, 5 × 10ⁿ steps). */
function niceTicks(min: number, max: number, count = 4): number[] {
  if (max === min) max = min + 1;
  const raw = (max - min) / count;
  const magnitude = 10 ** Math.floor(Math.log10(raw));
  const step = [1, 2, 2.5, 5, 10].map((m) => m * magnitude).find((s) => s >= raw) ?? raw;
  const start = Math.floor(min / step) * step;
  const ticks: number[] = [];
  for (let value = start; value <= max + step * 0.001; value += step) ticks.push(Math.round(value * 100) / 100);
  if (ticks[ticks.length - 1] < max) ticks.push(ticks[ticks.length - 1] + step);
  return ticks;
}

/** Tiny trend line for metric cards. Draws nothing for fewer than two points. */
export function Sparkline({ values, color = SERIES_COLORS.net, label }: { values: number[]; color?: string; label: string }) {
  const id = useId();
  if (values.length < 2) return null;
  const min = Math.min(...values);
  const max = Math.max(...values);
  const span = max - min || 1;
  const points = values.map((v, i) => [(i / (values.length - 1)) * 100, 36 - ((v - min) / span) * 32]);
  const line = points.map(([x, y], i) => `${i ? "L" : "M"}${x.toFixed(2)},${y.toFixed(2)}`).join("");
  return (
    <svg className="sparkline" viewBox="0 0 100 40" preserveAspectRatio="none" role="img" aria-label={label}>
      <defs>
        <linearGradient id={id} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor={color} stopOpacity=".28" />
          <stop offset="1" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={`${line}L100,40L0,40Z`} fill={`url(#${id})`} />
      <path d={line} fill="none" stroke={color} strokeWidth={2} vectorEffect="non-scaling-stroke" strokeLinejoin="round" />
    </svg>
  );
}

type TrendPoint = { start: string; end: string; income: number; spending: number };

function bucketLabel(point: TrendPoint, granularity: "DAY" | "WEEK" | "MONTH"): string {
  if (granularity === "DAY") return shortDate(point.start);
  if (granularity === "MONTH") return new Intl.DateTimeFormat("en-IN", { month: "short", year: "2-digit" }).format(new Date(point.start + "T00:00"));
  return `${shortDate(point.start)} – ${shortDate(point.end)}`;
}

/** Income vs spending over time, with a hover crosshair. */
export function TrendChart({ series, granularity, height = 240 }: { series: TrendPoint[]; granularity: "DAY" | "WEEK" | "MONTH"; height?: number }) {
  const [ref, width] = useWidth<HTMLDivElement>();
  const [hover, setHover] = useState<number | null>(null);
  const pad = { top: 12, right: 12, bottom: 26, left: 52 };
  const innerW = Math.max(0, width - pad.left - pad.right);
  const innerH = height - pad.top - pad.bottom;

  const { ticks, y } = useMemo(() => {
    const values = series.flatMap((p) => [p.income, p.spending]);
    const ticks = niceTicks(Math.min(0, ...values), Math.max(0, ...values));
    const lo = ticks[0], hi = ticks[ticks.length - 1];
    return { ticks, y: (v: number) => pad.top + innerH - ((v - lo) / (hi - lo || 1)) * innerH };
  }, [series, innerH, pad.top]);

  const x = (i: number) => pad.left + (series.length === 1 ? innerW / 2 : (i / (series.length - 1)) * innerW);
  const path = (key: "income" | "spending") => series.map((p, i) => `${i ? "L" : "M"}${x(i).toFixed(1)},${y(p[key]).toFixed(1)}`).join("");
  const labelEvery = Math.max(1, Math.ceil(series.length / Math.max(2, Math.floor(innerW / 90))));

  function onMove(event: React.MouseEvent<SVGSVGElement>) {
    const rect = event.currentTarget.getBoundingClientRect();
    const px = event.clientX - rect.left - pad.left;
    const index = series.length === 1 ? 0 : Math.round((px / innerW) * (series.length - 1));
    setHover(Math.min(series.length - 1, Math.max(0, index)));
  }

  const point = hover !== null ? series[hover] : null;
  return (
    <div>
      <div className="chart-legend">
        <span><i className="swatch" style={{ background: SERIES_COLORS.income }} />Income</span>
        <span><i className="swatch" style={{ background: SERIES_COLORS.spending }} />Spending</span>
      </div>
      <div ref={ref} className="chart-box">
        {width > 0 && series.length > 0 && (
          <svg className="chart" width={width} height={height} onMouseMove={onMove} onMouseLeave={() => setHover(null)} role="img"
            aria-label={`Income and spending across ${series.length} periods`}>
            {ticks.map((t) => (
              <g key={t}>
                <line className="grid-line" x1={pad.left} x2={width - pad.right} y1={y(t)} y2={y(t)} strokeDasharray={t === 0 ? undefined : "2 4"} />
                <text x={pad.left - 8} y={y(t) + 3} textAnchor="end">{t === 0 ? "0" : moneyCompact(t)}</text>
              </g>
            ))}
            {series.map((p, i) => i % labelEvery === 0 && (
              <text key={p.start} x={x(i)} y={height - 6} textAnchor={i === 0 && series.length > 1 ? "start" : "middle"}>{bucketLabel(p, granularity === "WEEK" ? "DAY" : granularity)}</text>
            ))}
            <path d={path("income")} fill="none" stroke={SERIES_COLORS.income} strokeWidth={2} strokeLinejoin="round" />
            <path d={path("spending")} fill="none" stroke={SERIES_COLORS.spending} strokeWidth={2} strokeLinejoin="round" />
            {series.length <= 40 && series.map((p, i) => (
              <g key={p.start}>
                <circle cx={x(i)} cy={y(p.income)} r={2.5} fill={SERIES_COLORS.income} />
                <circle cx={x(i)} cy={y(p.spending)} r={2.5} fill={SERIES_COLORS.spending} />
              </g>
            ))}
            {hover !== null && (
              <g>
                <line x1={x(hover)} x2={x(hover)} y1={pad.top} y2={pad.top + innerH} stroke="#e9ece9" strokeOpacity={0.25} />
                <circle cx={x(hover)} cy={y(series[hover].income)} r={4.5} fill={SERIES_COLORS.income} stroke="#171c18" strokeWidth={2} />
                <circle cx={x(hover)} cy={y(series[hover].spending)} r={4.5} fill={SERIES_COLORS.spending} stroke="#171c18" strokeWidth={2} />
              </g>
            )}
          </svg>
        )}
        {point && hover !== null && (
          <div className="chart-tooltip" style={{ left: Math.min(Math.max(x(hover) - 80, 0), Math.max(0, width - 180)), top: 0 }}>
            <div className="muted" style={{ marginBottom: 6 }}>{bucketLabel(point, granularity)}</div>
            <div className="row"><span><i className="swatch" style={{ background: SERIES_COLORS.income }} /> Income</span><b>{money(point.income)}</b></div>
            <div className="row"><span><i className="swatch" style={{ background: SERIES_COLORS.spending }} /> Spending</span><b>{money(point.spending)}</b></div>
            <div className="row" style={{ borderTop: "1px solid var(--line)", marginTop: 6, paddingTop: 6 }}><span>Net</span><b>{money(point.income - point.spending)}</b></div>
          </div>
        )}
      </div>
    </div>
  );
}

export type DonutSlice = { key: string; label: string; value: number; color: string };

/** Share-of-total ring with an interactive legend. */
export function Donut({ slices, total, centerLabel, legendExtra }: { slices: DonutSlice[]; total: number; centerLabel: string; legendExtra?: (slice: DonutSlice) => ReactNode }) {
  const [active, setActive] = useState<string | null>(null);
  const size = 168, stroke = 22, radius = (size - stroke) / 2, circumference = 2 * Math.PI * radius;
  const sum = slices.reduce((s, x) => s + x.value, 0) || 1;
  let offset = 0;
  const activeSlice = slices.find((s) => s.key === active);
  return (
    <div className="donut-wrap">
      <div className="donut">
        <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} role="img" aria-label={`${centerLabel}: ${money(total)}`}>
          <circle cx={size / 2} cy={size / 2} r={radius} fill="none" stroke="#222b23" strokeWidth={stroke} />
          <g transform={`rotate(-90 ${size / 2} ${size / 2})`}>
            {slices.map((slice) => {
              const length = (slice.value / sum) * circumference;
              const gap = slices.length > 1 ? Math.min(2, length / 2) : 0;
              const element = (
                <circle key={slice.key} cx={size / 2} cy={size / 2} r={radius} fill="none" stroke={slice.color}
                  strokeWidth={active === slice.key ? stroke + 4 : stroke} strokeDasharray={`${Math.max(0, length - gap)} ${circumference}`}
                  strokeDashoffset={-offset} opacity={active && active !== slice.key ? 0.35 : 1}
                  onMouseEnter={() => setActive(slice.key)} onMouseLeave={() => setActive(null)} style={{ transition: "opacity .15s" }} />
              );
              offset += length;
              return element;
            })}
          </g>
        </svg>
        <div className="donut-center">
          <strong>{money(activeSlice ? activeSlice.value : total)}</strong>
          <small>{activeSlice ? activeSlice.label : centerLabel}</small>
        </div>
      </div>
      <div className="legend">
        {slices.map((slice) => (
          <div key={slice.key} className={`legend-row ${active === slice.key ? "active" : ""}`} onMouseEnter={() => setActive(slice.key)} onMouseLeave={() => setActive(null)}>
            <span className="swatch" style={{ background: slice.color, borderRadius: "50%" }} />
            <span>{slice.label}</span>
            <b>{legendExtra ? legendExtra(slice) : money(slice.value)}</b>
            <small>{percent(slice.value / sum)}</small>
          </div>
        ))}
      </div>
    </div>
  );
}

export type RankedItem = { key: string; label: ReactNode; value: number; color: string; share?: number; previous?: number; onClick?: () => void; sub?: ReactNode };

/** Horizontal bars ranked by value; optional share and change versus previous. Spending going up is shown as unfavorable. */
export function RankedBars({ items, increaseIsBad = true }: { items: RankedItem[]; increaseIsBad?: boolean }) {
  const max = Math.max(...items.map((i) => i.value), 1);
  return (
    <div className="ranked">
      {items.map((item) => {
        const delta = item.previous !== undefined ? change(item.value, item.previous) : null;
        const tone = delta === null || Math.abs(delta) < 0.005 ? "" : (delta > 0) === increaseIsBad ? "bad" : "good";
        const content = (
          <>
            <div className="ranked-head">
              <span>{item.label}</span>
              <span className="values">
                {item.share !== undefined && <span className="dim">{percent(item.share)}</span>}
                {item.previous !== undefined && (
                  <span className={tone} title={`Previous period: ${money(item.previous)}`}>
                    {delta === null ? (item.previous === 0 && item.value > 0 ? "new" : "—") : `${delta > 0 ? "+" : ""}${percent(delta)}`}
                  </span>
                )}
                <b>{money(item.value)}</b>
              </span>
            </div>
            <div className="bar-track"><div className="bar-fill" style={{ width: `${Math.max(1.5, (item.value / max) * 100)}%`, background: item.color }} /></div>
            {item.sub}
          </>
        );
        return item.onClick
          ? <button key={item.key} className="ranked-row" onClick={item.onClick} style={{ border: 0, background: "transparent", padding: 0, textAlign: "left", color: "inherit" }}>{content}</button>
          : <div key={item.key} className="ranked-row">{content}</div>;
      })}
    </div>
  );
}

const STATUS_COLORS = { ON_TRACK: "#6ce3bd", AT_RISK: "#f5a86c", OVER: "#ff8a7a" };

/** Budget progress. `pace` marks how far through the month we are (0–1). */
export function ProgressBar({ value, limit, status, pace, label }: { value: number; limit: number; status: keyof typeof STATUS_COLORS; pace?: number; label: string }) {
  const ratio = limit > 0 ? value / limit : 0;
  return (
    <div className="progress" role="progressbar" aria-label={label} aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(ratio * 100)}>
      <i style={{ width: `${Math.min(100, Math.max(0, ratio * 100))}%`, background: STATUS_COLORS[status] }} />
      {pace !== undefined && pace > 0 && pace < 1 && <b style={{ left: `${pace * 100}%` }} title="Where spending would be at an even pace" />}
    </div>
  );
}

export const BUDGET_STATUS_LABELS = { ON_TRACK: "On track", AT_RISK: "At risk", OVER: "Over" } as const;
export const BUDGET_STATUS_TONES = { ON_TRACK: "good", AT_RISK: "warn", OVER: "bad" } as const;
