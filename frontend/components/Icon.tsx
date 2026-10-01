import type { SVGProps } from "react";

const paths = {
  overview: "M3 11.5 12 4l9 7.5M5.5 9.5V20h13V9.5M10 20v-5.5h4V20",
  transactions: "M4 7h16M4 12h16M4 17h10",
  analytics: "M4 20V10M10 20V4M16 20v-7M22 20H2",
  budgets: "M12 3a9 9 0 1 0 9 9h-9V3ZM15 3.5A8 8 0 0 1 20.5 9H15V3.5Z",
  recurring: "M4 12a8 8 0 0 1 13.7-5.6L20 8.7M20 4v4.7h-4.7M20 12a8 8 0 0 1-13.7 5.6L4 15.3M4 20v-4.7h4.7",
  accounts: "M3 9.5 12 4l9 5.5M5 10v7M9.5 10v7M14.5 10v7M19 10v7M3 20h18",
  inbox: "M3 13h5l1.5 3h5L16 13h5M5 5h14l2 8v6H3v-6l2-8Z",
  more: "M5 12h.01M12 12h.01M19 12h.01",
  search: "m20 20-4.2-4.2M11 18a7 7 0 1 1 0-14 7 7 0 0 1 0 14Z",
  plus: "M12 5v14M5 12h14",
  close: "M6 6l12 12M18 6 6 18",
  chevronLeft: "M15 18l-6-6 6-6",
  chevronRight: "M9 18l6-6-6-6",
  chevronDown: "M6 9l6 6 6-6",
  edit: "M4 20h4L19 9l-4-4L4 16v4ZM14 6l4 4",
  trash: "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3",
  check: "M5 12.5 10 17.5 19 7",
  alert: "M12 8v5M12 16.5h.01M10.3 3.9 2.4 18a2 2 0 0 0 1.7 3h15.8a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z",
  card: "M3 6.5h18v11H3zM3 10h18M7 14.5h3",
  bank: "M3 9.5 12 4l9 5.5M5 10v7M9.5 10v7M14.5 10v7M19 10v7M3 20h18",
  wallet: "M4 7h14a2 2 0 0 1 2 2v9H4a1 1 0 0 1-1-1V6a2 2 0 0 1 2-2h11v3M16 13.5h.01",
  refresh: "M20 12a8 8 0 1 1-2.3-5.7L20 8.7M20 4v4.7h-4.7",
  logout: "M15 4h4v16h-4M10 8l-4 4 4 4M6 12h10",
  arrowRight: "M5 12h14M13 6l6 6-6 6",
  external: "M14 5h5v5M19 5l-8 8M18 14v5H5V6h5",
  eye: "M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12ZM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z",
  eyeOff: "M3 3l18 18M10.6 5.1A10 10 0 0 1 12 5c6.4 0 10 7 10 7a17 17 0 0 1-3 3.9M6.6 6.6C3.9 8.4 2 12 2 12s3.6 7 10 7a9.6 9.6 0 0 0 5.4-1.6M9.9 9.9a3 3 0 0 0 4.2 4.2",
  sparkle: "M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5 18 18M6 18l2.5-2.5M15.5 8.5 18 6",
  filter: "M4 5h16l-6 7.5V19l-4 1v-7.5L4 5Z",
};

export type IconName = keyof typeof paths;

export function Icon({ name, ...props }: { name: IconName } & SVGProps<SVGSVGElement>) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" {...props}>
      <path d={paths[name]} />
    </svg>
  );
}
