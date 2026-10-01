import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

export function middleware(request: NextRequest) {
  const path = request.nextUrl.pathname;
  if (path.startsWith("/login") || path.startsWith("/api/auth") || path.startsWith("/_next") || path === "/manifest.webmanifest" || path === "/favicon.ico") return NextResponse.next();
  if (!request.cookies.has("JSESSIONID")) return NextResponse.redirect(new URL("/login", request.url));
  return NextResponse.next();
}

export const config = { matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"] };
