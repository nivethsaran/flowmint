"use client";

import { useEffect, useState } from "react";
import styles from "./data-state.module.css";

type Transaction = { id: string; merchant: string; amount: number; category: string; date: string; requiresReview: boolean };
type LoadState = "loading" | "ready" | "empty" | "error";

const formatter = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 });

export default function Home() {
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [range, setRange] = useState("30D");

  useEffect(() => {
    fetch("/api/transactions")
      .then((response) => {
        if (response.status === 401) {
          window.location.assign("/login");
          throw new Error("session-expired");
        }
        if (!response.ok) {
          throw new Error(`Transaction request failed: ${response.status}`);
        }
        return response.json() as Promise<Transaction[]>;
      })
      .then((data) => { setTransactions(data); setLoadState(data.length ? "ready" : "empty"); })
      .catch(() => setLoadState("error"));
  }, []);

  const expenses = transactions.reduce((sum, transaction) => sum + transaction.amount, 0);
  const reviewCount = transactions.filter((transaction) => transaction.requiresReview).length;
  const categories = transactions.reduce<Record<string, number>>((result, transaction) => { result[transaction.category] = (result[transaction.category] ?? 0) + transaction.amount; return result; }, {});
  return <main className="shell">
    <aside className="sidebar"><div className="brand"><span className="brand-mark">F</span><span>flowmint</span></div><p className="eyebrow">Finance OS</p><nav><a className="active" href="#overview">Overview</a><a href="#transactions">Transactions</a><a href="#insights">Analytics</a><a href="#budgets">Budgets</a><a href="#recurring">Recurring</a><a href="#accounts">Accounts</a></nav><div className="privacy"><span className="status-dot" />Live data<br /><small>Connected to Flowmint API</small></div></aside>
    <section className="content"><header className="topbar"><div><p className="eyebrow">Thursday, 01 October 2026</p><h1>Good morning, <em>you.</em></h1></div><button className="command" type="button">⌘ K <span>Search anything</span></button><button className="avatar" type="button" aria-label="Sign out" onClick={async () => { await fetch("/api/auth/logout", { method: "POST", headers: { "X-XSRF-TOKEN": document.cookie.split("; ").find((cookie) => cookie.startsWith("XSRF-TOKEN="))?.split("=")[1] ?? "" } }); window.location.assign("/login"); }}>N</button></header>
      <div className="range-row"><div><span className="eyebrow">Overview</span><h2>Your money, in focus.</h2></div><div className="ranges">{["7D", "30D", "3M", "6M", "1Y"].map((option) => <button className={range === option ? "selected" : ""} onClick={() => setRange(option)} key={option}>{option}</button>)}</div></div>
      {loadState === "error" && <div className={`${styles.dataState} ${styles.errorState}`}><strong>Flowmint API unavailable</strong><span>Start the backend and refresh to load your financial data.</span></div>}
      {loadState === "loading" && <div className={styles.dataState}><strong>Connecting to Flowmint</strong><span>Loading transactions from the backend...</span></div>}
      {loadState === "empty" && <div className={styles.dataState}><strong>No transactions yet</strong><span>Send a IRIS event to begin building your financial picture.</span></div>}
      <section className="metrics" id="overview"><article className="metric feature"><span>Net cash flow</span><strong>—</strong><small>Available after income analytics are connected</small><div className="sparkline"><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /></div></article><article className="metric"><span>Income</span><strong>—</strong><small>Waiting for income transactions</small></article><article className="metric"><span>Expenses</span><strong>{transactions.length ? formatter.format(expenses) : "—"}</strong><small>{transactions.length ? `${transactions.length} transactions` : "No transaction data"}</small></article><article className="metric"><span>Savings rate</span><strong>—</strong><small>Available after income analytics are connected</small></article></section>
      <section className="grid"><article className="panel breakdown" id="insights"><div className="panel-head"><div><span className="eyebrow">Where it goes</span><h3>Spending breakdown</h3></div><button className="text-button" type="button">View analytics →</button></div>{transactions.length ? <div className="breakdown-body"><div className="donut"><div><strong>{formatter.format(expenses)}</strong><small>total spent</small></div></div><div className="legend">{Object.entries(categories).slice(0, 4).map(([category, amount], index) => <div key={category}><span className={`legend-dot dot-${index}`} /><span>{category}</span><b>{formatter.format(amount)}</b></div>)}</div></div> : <div className={styles.panelEmpty}>Spending categories will appear here after transactions arrive.</div>}</article><article className="panel opportunity"><div className="panel-head"><div><span className="eyebrow">Needs attention</span><h3>Review queue</h3></div><span className="count">{reviewCount}</span></div>{reviewCount ? <div className="review-card"><span className="review-icon">!</span><div><strong>{reviewCount} transaction{reviewCount === 1 ? "" : "s"} need context</strong><p>Review the classification before using it in analytics.</p></div><button type="button">Review</button></div> : <div className={styles.panelEmpty}>No transactions currently need review.</div>}</article></section>
      <section className="panel transactions" id="transactions"><div className="panel-head"><div><span className="eyebrow">Latest activity</span><h3>Transactions</h3></div><button className="text-button" type="button">See all →</button></div>{transactions.length ? <div className="transaction-list">{transactions.slice(0, 5).map((transaction) => <div className="transaction" key={transaction.id}><span className={`merchant-icon ${transaction.category.toLowerCase().replaceAll(" ", "-")}`}>{transaction.merchant.slice(0, 1)}</span><div className="transaction-name"><strong>{transaction.merchant}</strong><small>{transaction.category} · {transaction.date}</small></div>{transaction.requiresReview && <span className="review-pill">Review</span>}<strong className="amount">− {formatter.format(transaction.amount)}</strong></div>)}</div> : <div className={styles.panelEmpty}>Transactions from the API will appear here.</div>}</section>
    </section></main>;
}
