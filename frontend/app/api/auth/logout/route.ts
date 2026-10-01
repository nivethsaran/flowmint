import { NextResponse } from "next/server";

export async function POST(request: Request) {
  const backendUrl = process.env.FLOWMINT_API_URL;
  if (!backendUrl) return NextResponse.json({ error: "CONFIGURATION_ERROR", message: "Authentication service is not configured" }, { status: 500 });
  try {
    const response = await fetch(`${backendUrl}/api/v1/auth/logout`, {
      method: "POST",
      headers: { ...(request.headers.get("cookie") ? { Cookie: request.headers.get("cookie")! } : {}), ...(request.headers.get("x-xsrf-token") ? { "X-XSRF-TOKEN": request.headers.get("x-xsrf-token")! } : {}) }
    });
    const result = new NextResponse(null, { status: response.status });
    const cookie = response.headers.get("set-cookie");
    if (cookie) result.headers.append("set-cookie", cookie);
    return result;
  } catch {
    return NextResponse.json({ error: "BACKEND_UNAVAILABLE", message: "Authentication service is unavailable" }, { status: 502 });
  }
}
