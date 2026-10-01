import { NextResponse } from "next/server";

export async function POST(request: Request) {
  const backendUrl = process.env.FLOWMINT_API_URL;
  if (!backendUrl) return NextResponse.json({ error: "CONFIGURATION_ERROR", message: "Ingestion service is not configured" }, { status: 500 });
  const authorization = request.headers.get("authorization");
  if (!authorization?.startsWith("Bearer ")) return NextResponse.json({ error: "UNAUTHORIZED", message: "Authentication required" }, { status: 401 });
  try {
    const response = await fetch(`${backendUrl}/api/v1/events`, { method: "POST", headers: { Authorization: authorization, "Content-Type": "application/json" }, body: await request.text() });
    return new NextResponse(await response.text(), { status: response.status, headers: { "Content-Type": response.headers.get("Content-Type") ?? "application/json" } });
  } catch {
    return NextResponse.json({ error: "BACKEND_UNAVAILABLE", message: "Ingestion service is unavailable" }, { status: 502 });
  }
}
