import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

const FORWARDED_REQUEST_HEADERS = ["cookie", "content-type", "x-xsrf-token", "accept"];

async function proxy(request: Request, { params }: { params: Promise<{ path: string[] }> }) {
  const backendUrl = process.env.FLOWMINT_API_URL;
  if (!backendUrl) return NextResponse.json({ error: "CONFIGURATION_ERROR", message: "Flowmint proxy is not configured" }, { status: 500 });

  const { path } = await params;
  const target = new URL(`${backendUrl}/api/v1/${path.map(encodeURIComponent).join("/")}`);
  target.search = new URL(request.url).search;

  const headers = new Headers();
  for (const name of FORWARDED_REQUEST_HEADERS) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  const hasBody = request.method !== "GET" && request.method !== "HEAD";

  try {
    const response = await fetch(target, { method: request.method, headers, body: hasBody ? await request.text() : undefined, cache: "no-store", redirect: "manual" });
    const result = new NextResponse(response.status === 204 ? null : await response.text(), { status: response.status });
    const contentType = response.headers.get("content-type");
    if (contentType) result.headers.set("content-type", contentType);
    for (const cookie of response.headers.getSetCookie()) result.headers.append("set-cookie", cookie);
    return result;
  } catch {
    return NextResponse.json({ error: "BACKEND_UNAVAILABLE", message: "Flowmint backend is unavailable" }, { status: 502 });
  }
}

export { proxy as GET, proxy as POST, proxy as PUT, proxy as PATCH, proxy as DELETE };
