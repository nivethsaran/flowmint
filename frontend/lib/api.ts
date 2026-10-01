export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public fields: Record<string, string> = {}) {
    super(message);
  }
}

function readCookie(name: string): string {
  return document.cookie.split("; ").find((cookie) => cookie.startsWith(`${name}=`))?.slice(name.length + 1) ?? "";
}

async function csrfToken(): Promise<string> {
  let token = readCookie("XSRF-TOKEN");
  if (!token) {
    await fetch("/api/auth/csrf", { credentials: "same-origin" });
    token = readCookie("XSRF-TOKEN");
  }
  return decodeURIComponent(token);
}

async function request<T>(method: string, path: string, body?: unknown, signal?: AbortSignal): Promise<T> {
  const headers: Record<string, string> = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (method !== "GET") headers["X-XSRF-TOKEN"] = await csrfToken();

  let response: Response;
  try {
    response = await fetch(`/api/v1${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), credentials: "same-origin", cache: "no-store", signal });
  } catch (error) {
    if ((error as Error).name === "AbortError") throw error;
    throw new ApiError(0, "NETWORK_ERROR", "Flowmint API unavailable");
  }

  if (response.status === 401) {
    window.location.assign("/login");
    throw new ApiError(401, "UNAUTHORIZED", "Session expired");
  }
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  const payload = text ? safeJson(text) : undefined;
  if (!response.ok) {
    const error = (payload ?? {}) as { error?: string; message?: string; fields?: Record<string, string> };
    throw new ApiError(response.status, error.error ?? "HTTP_" + response.status, error.message ?? "Request failed", error.fields ?? {});
  }
  return payload as T;
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

export const api = {
  get: <T>(path: string, signal?: AbortSignal) => request<T>("GET", path, undefined, signal),
  post: <T>(path: string, body?: unknown) => request<T>("POST", path, body),
  put: <T>(path: string, body?: unknown) => request<T>("PUT", path, body),
  patch: <T>(path: string, body?: unknown) => request<T>("PATCH", path, body),
  delete: <T>(path: string) => request<T>("DELETE", path),
};

/** Builds a query string, skipping empty values. */
export function query(params: Record<string, string | number | boolean | null | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === "") continue;
    search.set(key, String(value));
  }
  const text = search.toString();
  return text ? `?${text}` : "";
}

export async function signOut() {
  try {
    await fetch("/api/auth/logout", { method: "POST", headers: { "X-XSRF-TOKEN": await csrfToken() }, credentials: "same-origin" });
  } finally {
    window.location.assign("/login");
  }
}
