import { NextResponse } from "next/server";

export async function POST(request: Request) {
  const backendUrl = process.env.FLOWMINT_API_URL;
  if (!backendUrl) return NextResponse.json({ error: "CONFIGURATION_ERROR", message: "Authentication service is not configured" }, { status: 500 });
  try {
    const response = await fetch(`${backendUrl}/api/v1/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...(request.headers.get("cookie") ? { Cookie: request.headers.get("cookie")! } : {}), ...(request.headers.get("x-xsrf-token") ? { "X-XSRF-TOKEN": request.headers.get("x-xsrf-token")! } : {}) },
      body: await request.text()
    });
    const body = await response.text();
    const result = new NextResponse(body, { status: response.status, headers: { "Content-Type": response.headers.get("Content-Type") ?? "application/json" } });
    const cookie = response.headers.get("set-cookie");
    if (cookie) result.headers.append("set-cookie", cookie);
    return result;
  } catch {
    return NextResponse.json({ error: "BACKEND_UNAVAILABLE", message: "Authentication service is unavailable" }, { status: 502 });
  }
}
