"use client";

import { useState } from "react";
import styles from "./login.module.css";

function csrfCookie(): string {
  return document.cookie.split("; ").find((cookie) => cookie.startsWith("XSRF-TOKEN="))?.split("=")[1] ?? "";
}

export default function LoginPage() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [totp, setTotp] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function submit(event: { preventDefault: () => void }) {
    event.preventDefault();
    setLoading(true);
    setError("");
    try {
      const csrfResponse = await fetch("/api/auth/csrf", { credentials: "same-origin" });
      if (!csrfResponse.ok) throw new Error("csrf");
      const response = await fetch("/api/auth/login", { method: "POST", credentials: "same-origin", headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": csrfCookie() }, body: JSON.stringify({ username, password, totp }) });
      if (!response.ok) throw new Error("login");
      window.location.assign("/");
    } catch {
      setError("Invalid credentials.");
      setLoading(false);
    }
  }

  return <main className={styles.page}><div className={styles.card}><div className={styles.brand}><span>F</span> flowmint</div><p className="eyebrow">Private finance OS</p><h1>Welcome back.</h1><p className={styles.subtitle}>Verify your identity to access your financial command center.</p><form onSubmit={submit}><label>Username<input value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" required /></label><label>Password<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" required /></label><label>Authenticator code<input inputMode="numeric" pattern="[0-9]{6}" maxLength={6} value={totp} onChange={(event) => setTotp(event.target.value.replace(/\D/g, ""))} autoComplete="one-time-code" placeholder="000000" required /></label>{error && <p className={styles.error}>{error}</p>}<button disabled={loading} type="submit">{loading ? "Verifying..." : "Sign in"}</button></form><small className={styles.note}>Use the six-digit code from your authenticator app.</small></div></main>;
}
