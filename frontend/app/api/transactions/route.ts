import { NextResponse } from "next/server";

export async function GET() {
  const backendUrl = process.env.FLOWMINT_API_URL;
  const deviceToken = process.env.FLOWMINT_DEVICE_TOKEN;

  if (!backendUrl || !deviceToken) {
    return NextResponse.json({ code: "CONFIGURATION_ERROR", message: "Flowmint proxy is not configured" }, { status: 500 });
  }

  try {
    const response = await fetch(`${backendUrl}/api/v1/transactions`, {
      headers: { Authorization: `Bearer ${deviceToken}` },
      cache: "no-store"
    });
    const body = await response.text();
    return new NextResponse(body, { status: response.status, headers: { "Content-Type": response.headers.get("Content-Type") ?? "application/json" } });
  } catch {
    return NextResponse.json({ code: "BACKEND_UNAVAILABLE", message: "Flowmint backend is unavailable" }, { status: 502 });
  }
}
