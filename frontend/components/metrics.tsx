"use client";

import type { ReactNode } from "react";
import { change, percent } from "@/lib/format";
import { Skeleton } from "./ui";

/** Change versus the previous period, colored by whether the move is good news. */
export function Delta({ current, previous, increaseIsGood, points = false }: { current: number | null; previous: number | null; increaseIsGood: boolean; points?: boolean }) {
  if (current === null || previous === null) return <span className="delta">— vs prev.</span>;
  const value = points ? current - previous : change(current, previous);
  if (value === null) return <span className="delta">{current ? "new" : "—"} vs prev.</span>;
  const flat = Math.abs(value) < 0.005;
  const tone = flat ? "" : (value > 0) === increaseIsGood ? "good" : "bad";
  const arrow = flat ? "" : value > 0 ? "▲ " : "▼ ";
  const text = points ? `${(Math.abs(value) * 100).toFixed(1)} pts` : percent(Math.abs(value));
  return <span className={`delta ${tone}`} title="Compared with the previous period of the same length">{arrow}{flat ? "no change" : text}</span>;
}

export function MetricCard({ label, value, foot, feature = false, loading = false, children }: { label: string; value: ReactNode; foot?: ReactNode; feature?: boolean; loading?: boolean; children?: ReactNode }) {
  return (
    <article className={`metric ${feature ? "feature" : ""}`}>
      <span className="metric-label">{label}</span>
      {loading ? <Skeleton height={28} width="60%" style={{ margin: "14px 0 6px" }} /> : <strong className="metric-value">{value}</strong>}
      <span className="metric-foot">{loading ? <Skeleton height={12} width="45%" /> : foot}</span>
      {!loading && children}
    </article>
  );
}
