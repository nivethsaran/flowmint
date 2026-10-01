import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

const PUBLIC_PREFIXES = ["/login", "/api/auth", "/_next"];
// Devices authenticate with the bearer token, which the backend verifies; they never hold a browser session.
const DEVICE_ROUTES = ["/api/flowmint/events"];

export function middleware(request: NextRequest) {
  const path = request.nextUrl.pathname;
  if (PUBLIC_PREFIXES.some((prefix) => path.startsWith(prefix)) || DEVICE_ROUTES.includes(path) || path === "/manifest.webmanifest" || path === "/favicon.ico") {
    return NextResponse.next();
  }
  if (request.cookies.has("JSESSIONID")) return NextResponse.next();
  if (path.startsWith("/api/")) return NextResponse.json({ error: "UNAUTHORIZED", message: "Authentication required" }, { status: 401 });
  return NextResponse.redirect(new URL("/login", request.url));
}

export const config = { matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"] };
