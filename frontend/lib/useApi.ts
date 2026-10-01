"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { api, ApiError } from "./api";

export type ApiState<T> = { data: T | undefined; error: ApiError | undefined; loading: boolean; reload: () => void };

/** Loads `path` (skipped when null) and reloads when it changes or `reload()` is called. Keeps stale data while reloading. */
export function useApi<T>(path: string | null): ApiState<T> {
  const [data, setData] = useState<T>();
  const [error, setError] = useState<ApiError>();
  const [loading, setLoading] = useState(path !== null);
  const [version, setVersion] = useState(0);
  const lastPath = useRef<string | null>(null);

  useEffect(() => {
    if (path === null) {
      setLoading(false);
      return;
    }
    if (lastPath.current !== path) setData(undefined);
    lastPath.current = path;
    const controller = new AbortController();
    setLoading(true);
    api.get<T>(path, controller.signal)
      .then((result) => { setData(result); setError(undefined); })
      .catch((cause: unknown) => {
        if ((cause as Error).name === "AbortError") return;
        setError(cause instanceof ApiError ? cause : new ApiError(0, "UNKNOWN", "Request failed"));
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [path, version]);

  const reload = useCallback(() => setVersion((value) => value + 1), []);
  return { data, error, loading, reload };
}

/** Calls `onChange` whenever data anywhere in the app is modified (edits in the drawer, new transactions…). */
export function useDataChanged(onChange: () => void) {
  useEffect(() => {
    window.addEventListener("flowmint:data-changed", onChange);
    return () => window.removeEventListener("flowmint:data-changed", onChange);
  }, [onChange]);
}

export function notifyDataChanged() {
  window.dispatchEvent(new Event("flowmint:data-changed"));
}
