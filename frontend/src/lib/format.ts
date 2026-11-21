import type { DateTime, OrderStatus } from '@/api/types';

const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 });

/** 249 → "₹249", 249.5 → "₹249.50" */
export function money(value: number | null | undefined): string {
  if (value == null) return '—';
  const s = inr.format(value);
  return s.endsWith('.00') ? s.slice(0, -3) : s;
}

/** Backend sends "2026-11-22T14:05:00" (server-local time, no zone) → Date */
export function toDate(value: DateTime | null | undefined): Date | null {
  if (!value) return null;
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? null : d;
}

export function time(value: DateTime | null | undefined): string {
  const d = toDate(value);
  return d ? d.toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' }) : '—';
}

export function dateTime(value: DateTime | null | undefined): string {
  const d = toDate(value);
  if (!d) return '—';
  return d.toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' });
}

/** "just now", "5 min ago", "3 h ago", then a date */
export function relative(value: DateTime | null | undefined): string {
  const d = toDate(value);
  if (!d) return '';
  const sec = Math.round((Date.now() - d.getTime()) / 1000);
  if (sec < 45) return 'just now';
  const min = Math.round(sec / 60);
  if (min < 60) return `${min} min ago`;
  const h = Math.round(min / 60);
  if (h < 24) return `${h} h ago`;
  return d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short' });
}

export function km(value: number | null | undefined): string {
  if (value == null) return '';
  return value < 1 ? `${Math.round(value * 1000)} m` : `${value.toFixed(1)} km`;
}

export const STATUS_LABEL: Record<OrderStatus, string> = {
  PLACED: 'Placed',
  ACCEPTED: 'Accepted',
  PREPARING: 'Preparing',
  READY: 'Ready for pickup',
  PICKED_UP: 'On the way',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
};

export const ACTIVE_STATUSES: OrderStatus[] = ['PLACED', 'ACCEPTED', 'PREPARING', 'READY', 'PICKED_UP'];

export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0]!.toUpperCase())
    .join('');
}

export function cn(...parts: Array<string | false | null | undefined>): string {
  return parts.filter(Boolean).join(' ');
}
