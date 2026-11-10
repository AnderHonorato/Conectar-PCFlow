import type { ReactNode } from "react";

interface IconProps {
  children: ReactNode;
}

function Frame({ children }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
      {children}
    </svg>
  );
}

const stroke = {
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.8,
  strokeLinecap: "round" as const,
  strokeLinejoin: "round" as const,
};

export function MenuIcon() {
  return (
    <Frame>
      <path d="M5 7h14M5 12h14M5 17h10" {...stroke} />
    </Frame>
  );
}

export function MediaIcon() {
  return (
    <Frame>
      <circle cx="12" cy="12" r="7" {...stroke} />
      <path d="M10 9.5v5l4.5-2.5z" fill="currentColor" stroke="none" />
    </Frame>
  );
}

export function AppsIcon() {
  return (
    <Frame>
      <rect x="4" y="4" width="6" height="6" rx="1.4" {...stroke} />
      <rect x="14" y="4" width="6" height="6" rx="1.4" {...stroke} />
      <rect x="4" y="14" width="6" height="6" rx="1.4" {...stroke} />
      <rect x="14" y="14" width="6" height="6" rx="1.4" {...stroke} />
    </Frame>
  );
}

export function BrowserIcon() {
  return (
    <Frame>
      <circle cx="12" cy="12" r="7" {...stroke} />
      <path d="M5 12h14M12 5c2.2 2.2 2.2 11.6 0 14M12 5c-2.2 2.2-2.2 11.6 0 14" {...stroke} />
    </Frame>
  );
}

export function KeyboardIcon() {
  return (
    <Frame>
      <rect x="3" y="6" width="18" height="12" rx="2" {...stroke} />
      <path d="M7 10h.01M11 10h.01M15 10h.01M8 14h8" {...stroke} />
    </Frame>
  );
}

export function MoreIcon() {
  return (
    <Frame>
      <path d="M9 6l6 6-6 6" {...stroke} />
    </Frame>
  );
}

export function BackIcon() {
  return (
    <Frame>
      <path d="M15 5L8 12l7 7" {...stroke} />
    </Frame>
  );
}
