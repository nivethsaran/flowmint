"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { api, query as toQuery } from "@/lib/api";
import { shortDate } from "@/lib/format";
import type { Page, TransactionItem } from "@/lib/types";
import { Icon, type IconName } from "./Icon";
import { CategoryIcon, Portal, SignedAmount, useOverlay } from "./ui";
import { useTransactionDrawer } from "./TransactionDrawer";

type Command = { id: string; label: string; hint: string; icon: IconName; keywords: string; run: () => void };
type Entry = { kind: "command"; command: Command } | { kind: "transaction"; transaction: TransactionItem } | { kind: "all"; query: string };

export function CommandPalette({ onClose }: { onClose: () => void }) {
  const router = useRouter();
  const drawer = useTransactionDrawer();
  const [text, setText] = useState("");
  const [results, setResults] = useState<TransactionItem[]>([]);
  const [searching, setSearching] = useState(false);
  const [active, setActive] = useState(0);
  // Enter pressed before search results arrived: run the top entry once they do.
  const [pendingEnter, setPendingEnter] = useState(false);
  const listRef = useRef<HTMLDivElement>(null);
  useOverlay(onClose);

  const commands = useMemo<Command[]>(() => {
    const go = (path: string) => () => { onClose(); router.push(path); };
    return [
      { id: "overview", label: "Overview", hint: "Go to page", icon: "overview", keywords: "home dashboard overview", run: go("/") },
      { id: "transactions", label: "Transactions", hint: "Go to page", icon: "transactions", keywords: "transactions list history", run: go("/transactions") },
      { id: "analytics", label: "Analytics", hint: "Go to page", icon: "analytics", keywords: "analytics insights trends reports", run: go("/analytics") },
      { id: "budgets", label: "Budgets", hint: "Go to page", icon: "budgets", keywords: "budgets limits", run: go("/budgets") },
      { id: "recurring", label: "Recurring", hint: "Go to page", icon: "recurring", keywords: "recurring subscriptions bills emi", run: go("/recurring") },
      { id: "accounts", label: "Accounts", hint: "Go to page", icon: "accounts", keywords: "accounts cards banks balances", run: go("/accounts") },
      { id: "inbox", label: "Inbox", hint: "Go to page", icon: "inbox", keywords: "inbox messages sms notifications", run: go("/inbox") },
      { id: "add", label: "Add transaction", hint: "Action", icon: "plus", keywords: "add new create manual cash transaction", run: () => { onClose(); drawer.openAdd(); } },
      { id: "review", label: "Needs review", hint: "Filtered list", icon: "alert", keywords: "needs review flagged check", run: go("/transactions?review=true") },
    ];
  }, [router, drawer, onClose]);

  const trimmed = text.trim();
  useEffect(() => {
    if (trimmed.length < 2) { setResults([]); setSearching(false); return; }
    setSearching(true);
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      api.get<Page<TransactionItem>>(`/transactions${toQuery({ q: trimmed, size: 6 })}`, controller.signal)
        .then((page) => setResults(page.items))
        .catch(() => setResults([]))
        .finally(() => { if (!controller.signal.aborted) setSearching(false); });
    }, 200);
    return () => { window.clearTimeout(timer); controller.abort(); };
  }, [trimmed]);

  const entries = useMemo<Entry[]>(() => {
    const needle = trimmed.toLowerCase();
    const matching = needle ? commands.filter((c) => c.label.toLowerCase().includes(needle) || c.keywords.includes(needle)) : commands;
    const list: Entry[] = matching.map((command) => ({ kind: "command", command }));
    if (trimmed.length >= 2 && !searching) {
      list.push(...results.map((transaction) => ({ kind: "transaction" as const, transaction })));
      list.push({ kind: "all", query: trimmed });
    }
    return list;
  }, [commands, results, trimmed, searching]);

  useEffect(() => setActive(0), [trimmed, results]);
  useEffect(() => {
    listRef.current?.querySelector<HTMLElement>(`[data-index="${active}"]`)?.scrollIntoView({ block: "nearest" });
  }, [active]);

  function run(entry: Entry) {
    if (entry.kind === "command") entry.command.run();
    else if (entry.kind === "transaction") { onClose(); drawer.open(entry.transaction.id); }
    else { onClose(); router.push(`/transactions${toQuery({ q: entry.query })}`); }
  }

  function onKeyDown(event: React.KeyboardEvent) {
    if (event.key === "ArrowDown") { event.preventDefault(); setActive((i) => Math.min(entries.length - 1, i + 1)); }
    else if (event.key === "ArrowUp") { event.preventDefault(); setActive((i) => Math.max(0, i - 1)); }
    else if (event.key === "Enter") {
      event.preventDefault();
      if (searching && active === 0 && !entries.some((e) => e.kind === "command")) setPendingEnter(true);
      else if (entries[active]) run(entries[active]);
    }
  }

  useEffect(() => {
    if (pendingEnter && !searching && entries[0]) {
      setPendingEnter(false);
      run(entries[0]);
    }
  });

  const firstTransaction = entries.findIndex((e) => e.kind === "transaction");
  const commandCount = entries.filter((e) => e.kind === "command").length;
  return (
    <Portal>
      <div className="overlay" onClick={onClose} />
      <div className="palette" role="dialog" aria-modal="true" aria-label="Search">
        <div className="palette-input">
          <Icon name="search" />
          <input autoFocus value={text} onChange={(e) => setText(e.target.value)} onKeyDown={onKeyDown} placeholder="Search transactions or jump to…"
            role="combobox" aria-expanded="true" aria-controls="palette-list" aria-activedescendant={`palette-${active}`} />
          {searching && <span className="dim mono" style={{ fontSize: 11 }}>searching…</span>}
          <kbd>esc</kbd>
        </div>
        <div className="palette-list" id="palette-list" role="listbox" ref={listRef}>
          {commandCount > 0 && <div className="palette-group">{trimmed ? "Commands" : "Jump to"}</div>}
          {entries.map((entry, index) => (
            <div key={entry.kind === "command" ? entry.command.id : entry.kind === "transaction" ? entry.transaction.id : "all"}>
              {index === firstTransaction && <div className="palette-group">Transactions</div>}
              <button id={`palette-${index}`} data-index={index} role="option" aria-selected={index === active}
                className={`palette-item ${index === active ? "active" : ""}`} onMouseMove={() => setActive(index)} onClick={() => run(entry)}>
                {entry.kind === "command" && <>
                  <span className="icon"><Icon name={entry.command.icon} /></span>
                  <span>{entry.command.label}</span>
                  <small>{entry.command.hint}</small>
                </>}
                {entry.kind === "transaction" && <>
                  <CategoryIcon categoryKey={entry.transaction.category} label={entry.transaction.merchant} />
                  <span style={{ minWidth: 0 }}>
                    <span style={{ display: "block", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{entry.transaction.merchant}</span>
                    <small>{shortDate(entry.transaction.date)}{entry.transaction.accountName ? ` · ${entry.transaction.accountName}` : ""}</small>
                  </span>
                  <SignedAmount amount={entry.transaction.amount} type={entry.transaction.type} direction={entry.transaction.direction} />
                </>}
                {entry.kind === "all" && <>
                  <span className="icon"><Icon name="arrowRight" /></span>
                  <span>See all results for “{entry.query}”</span>
                  <small>Transactions</small>
                </>}
              </button>
            </div>
          ))}
          {trimmed.length >= 2 && !searching && results.length === 0 && commandCount === 0 && (
            <div className="palette-empty">No transactions match “{trimmed}”.</div>
          )}
        </div>
        <div className="palette-foot"><span><kbd>↑</kbd> <kbd>↓</kbd> move</span><span><kbd>↵</kbd> open</span><span><kbd>esc</kbd> close</span></div>
      </div>
    </Portal>
  );
}
