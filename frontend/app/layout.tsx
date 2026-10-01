import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = { title: "Flowmint / Finance OS", description: "Private financial command center", manifest: "/manifest.webmanifest" };
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) { return <html lang="en"><body>{children}</body></html>; }
