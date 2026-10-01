"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useCallback, useEffect, useState, type ReactNode } from "react";
import { signOut } from "@/lib/api";
import { greeting, todayLong } from "@/lib/format";
import { useApi, useDataChanged } from "@/lib/useApi";
import type { EventStats } from "@/lib/types";
import { CommandPalette } from "./CommandPalette";
import { Icon, type IconName } from "./Icon";
import { TransactionDrawerProvider, useTransactionDrawer } from "./TransactionDrawer";
import { Sheet } from "./ui";

const NAV: { href: string; label: string; icon: IconName }[] = [
  { href: "/", label: "Overview", icon: "overview" },
  { href: "/transactions", label: "Transactions", icon: "transactions" },
  { href: "/analytics", label: "Analytics", icon: "analytics" },
  { href: "/budgets", label: "Budgets", icon: "budgets" },
  { href: "/recurring", label: "Recurring", icon: "recurring" },
  { href: "/accounts", label: "Accounts", icon: "accounts" },
  { href: "/inbox", label: "Inbox", icon: "inbox" },
];
const PRIMARY_MOBILE = NAV.slice(0, 4);
const MORE_MOBILE = NAV.slice(4);

function isActive(pathname: string, href: string) {
  return href === "/" ? pathname === "/" : pathname === href || pathname.startsWith(`${href}/`);
}

export function Shell({ children }: { children: ReactNode }) {
  return (
    <TransactionDrawerProvider>
      <ShellFrame>{children}</ShellFrame>
    </TransactionDrawerProvider>
  );
}

function ShellFrame({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const drawer = useTransactionDrawer();
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [moreOpen, setMoreOpen] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const me = useApi<{ authenticated: boolean; username: string }>("/auth/me");
  const stats = useApi<EventStats>("/events/stats");
  const pending = stats.data ? stats.data.RECEIVED + stats.data.PROCESSING + stats.data.RETRY : 0;
  const failed = stats.data?.FAILED ?? 0;
  const reloadStats = stats.reload;
  useDataChanged(reloadStats);

  useEffect(() => {
    const timer = window.setInterval(reloadStats, pending > 0 ? 10_000 : 60_000);
    return () => window.clearInterval(timer);
  }, [pending, reloadStats]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        setPaletteOpen((open) => !open);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  useEffect(() => {
    if (!menuOpen) return;
    const close = () => setMenuOpen(false);
    window.addEventListener("click", close);
    return () => window.removeEventListener("click", close);
  }, [menuOpen]);

  useEffect(() => { setMoreOpen(false); setMenuOpen(false); }, [pathname]);

  // Time-of-day text depends on the browser clock, so render it after mount to avoid a server/client mismatch.
  const [now, setNow] = useState<Date | null>(null);
  useEffect(() => {
    setNow(new Date());
    const timer = window.setInterval(() => setNow(new Date()), 60_000);
    return () => window.clearInterval(timer);
  }, []);

  const closePalette = useCallback(() => setPaletteOpen(false), []);
  const username = me.data?.username ?? "";
  const inboxBadge = pending + failed;

  return (
    <>
      <aside className="sidebar" aria-label="Main navigation">
        <Link href="/" className="brand"><span className="brand-mark">F</span><span>flowmint</span></Link>
        <p className="eyebrow">Finance OS</p>
        <nav className="nav">
          {NAV.map((item) => (
            <Link key={item.href} href={item.href} className={`nav-link ${isActive(pathname, item.href) ? "active" : ""}`} aria-current={isActive(pathname, item.href) ? "page" : undefined}>
              <Icon name={item.icon} />{item.label}
              {item.href === "/inbox" && inboxBadge > 0 && <span className="nav-badge" title={`${pending} pending, ${failed} failed`}>{inboxBadge}</span>}
            </Link>
          ))}
        </nav>
        <div className="sidebar-foot">
          {stats.error ? <><span className="status-dot" style={{ background: "var(--red)" }} />API unreachable</>
            : pending > 0 ? <><span className="status-dot pending" />Reading {pending} message{pending === 1 ? "" : "s"}…</>
            : <><span className="status-dot" />Live data</>}
          <small>{failed > 0 ? `${failed} message${failed === 1 ? "" : "s"} failed — see Inbox` : "Connected to Flowmint API"}</small>
        </div>
      </aside>

      <div className="main">
        <div className="container">
          <header className="topbar">
            <div style={{ minWidth: 0 }}>
              <p className="eyebrow">{now ? todayLong(now) : "\u00a0"}</p>
              <h1>{now ? greeting(now) : "Welcome"}{username && <>, <em>{username}.</em></>}</h1>
            </div>
            <button className="search-trigger" onClick={() => setPaletteOpen(true)} aria-label="Search">
              <Icon name="search" /><span>Search anything</span><kbd>⌘K</kbd>
            </button>
            <div className="menu-wrap">
              <button className="avatar" aria-label="Account menu" aria-haspopup="menu" aria-expanded={menuOpen}
                onClick={(event) => { event.stopPropagation(); setMenuOpen((open) => !open); }}>
                {(username[0] ?? "·").toUpperCase()}
              </button>
              {menuOpen && (
                <div className="menu" role="menu" onClick={(event) => event.stopPropagation()}>
                  <div className="menu-header">Signed in as<strong>{username || "…"}</strong></div>
                  <button role="menuitem" onClick={() => { setMenuOpen(false); drawer.openAdd(); }}>Add transaction</button>
                  <button role="menuitem" onClick={() => signOut()}>Sign out</button>
                </div>
              )}
            </div>
          </header>
          {children}
        </div>
      </div>

      <nav className="bottom-nav" aria-label="Main navigation">
        {PRIMARY_MOBILE.map((item) => (
          <Link key={item.href} href={item.href} className={isActive(pathname, item.href) ? "active" : ""}><Icon name={item.icon} />{item.label}</Link>
        ))}
        <button className={MORE_MOBILE.some((item) => isActive(pathname, item.href)) ? "active" : ""} onClick={() => setMoreOpen(true)}>
          <Icon name="more" />More{inboxBadge > 0 && <i className="dot" />}
        </button>
      </nav>

      {moreOpen && (
        <Sheet onClose={() => setMoreOpen(false)}>
          <nav className="nav">
            {MORE_MOBILE.map((item) => (
              <Link key={item.href} href={item.href} className={`nav-link ${isActive(pathname, item.href) ? "active" : ""}`}>
                <Icon name={item.icon} />{item.label}
                {item.href === "/inbox" && inboxBadge > 0 && <span className="nav-badge">{inboxBadge}</span>}
              </Link>
            ))}
            <button className="nav-link" style={{ border: 0, background: "transparent", width: "100%" }} onClick={() => { setMoreOpen(false); drawer.openAdd(); }}><Icon name="plus" />Add transaction</button>
            <button className="nav-link" style={{ border: 0, background: "transparent", width: "100%" }} onClick={() => signOut()}><Icon name="logout" />Sign out</button>
          </nav>
        </Sheet>
      )}
      {paletteOpen && <CommandPalette onClose={closePalette} />}
    </>
  );
}
