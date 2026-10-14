import { formatDuration } from '../../game/format';

/** Whole days and the rest as h:mm:ss: 190000 s → 2 days and 4:46:40. */
export function splitCountdown(totalSeconds: number): { days: number; rest: string } {
  const seconds = Math.max(0, Math.floor(totalSeconds));
  const days = Math.floor(seconds / 86_400);
  return { days, rest: formatDuration(seconds - days * 86_400) };
}

/**
 * The text of a `datetime-local` field (yyyy-MM-ddTHH:mm in the browser's time zone) for a moment,
 * or '' if there is none. The host sets the opening time in his own time zone.
 */
export function toLocalInput(iso: string | null | undefined): string {
  if (iso === null || iso === undefined) {
    return '';
  }
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** The moment a `datetime-local` text means in the browser's time zone, as an ISO string; null if it isn't a time. */
export function fromLocalInput(text: string): string | null {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(text)) {
    return null;
  }
  const date = new Date(text);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}
