import type { Metadata, Viewport } from "next";
import "./globals.css";

export const metadata: Metadata = { title: "Flowmint", description: "Private financial command center", manifest: "/manifest.webmanifest" };
export const viewport: Viewport = { width: "device-width", initialScale: 1, viewportFit: "cover", themeColor: "#101411" };

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en"><body>{children}</body></html>;
}
