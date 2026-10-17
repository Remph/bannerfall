import type { Language } from '../i18n/language';

/**
 * Number, duration and date formats (05-frontend §2). Game numbers are always rounded down, like the
 * server does when it compares amounts.
 */

/** The locale per UI language: decides the thousands separator (1,234 / 1.234) and date formats. */
const locales: Record<Language, string> = { en: 'en-GB', de: 'de-DE' };

// Creating an Intl formatter is comparatively slow, so keep one per language.
const numberFormats = new Map<Language, Intl.NumberFormat>();

/** 12345.9 → "12,345" (en) / "12.345" (de). */
export function formatNumber(value: number, language: Language): string {
  let format = numberFormats.get(language);
  if (format === undefined) {
    format = new Intl.NumberFormat(locales[language], { maximumFractionDigits: 0 });
    numberFormats.set(language, format);
  }
  return format.format(Math.floor(value));
}

// One decimal: distances on the map.
const decimalFormats = new Map<Language, Intl.NumberFormat>();

/** 12.345 → "12.3" (en) / "12,3" (de); always one decimal, like a distance "12.0 fields". */
export function formatDecimal(value: number, language: Language): string {
  let format = decimalFormats.get(language);
  if (format === undefined) {
    format = new Intl.NumberFormat(locales[language], { minimumFractionDigits: 1, maximumFractionDigits: 1 });
    decimalFormats.set(language, format);
  }
  return format.format(value);
}

const ratioFormats = new Map<Language, Intl.NumberFormat>();

/** An exchange rate with up to two decimals: 1.5 → "1.5" (en) / "1,5" (de), 2 → "2", 1.25 → "1.25". */
export function formatRatio(value: number, language: Language): string {
  let format = ratioFormats.get(language);
  if (format === undefined) {
    format = new Intl.NumberFormat(locales[language], { maximumFractionDigits: 2 });
    ratioFormats.set(language, format);
  }
  return format.format(value);
}

/** Seconds as h:mm:ss – 1:02:03, 0:00:59, 27:00:00. Negative values show 0:00:00. */
export function formatDuration(totalSeconds: number): string {
  const seconds = Math.max(0, Math.floor(totalSeconds));
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  return `${hours}:${pad2(minutes)}:${pad2(seconds % 60)}`;
}

/**
 * Day and time, e.g. "Tue 6 Oct, 21:14" / "Di., 6. Okt., 21:14". `timeZone` is only for tests;
 * players see their device's time zone.
 */
export function formatDateTime(ms: number, language: Language, timeZone?: string): string {
  return dateTime(ms, language, false, timeZone);
}

/** Like formatDateTime, with seconds: "Tue 6 Oct, 21:14:05" (when a battle happened). */
export function formatDateTimeExact(ms: number, language: Language, timeZone?: string): string {
  return dateTime(ms, language, true, timeZone);
}

function dateTime(ms: number, language: Language, seconds: boolean, timeZone?: string): string {
  return new Intl.DateTimeFormat(locales[language], {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
    second: seconds ? '2-digit' : undefined,
    hourCycle: 'h23',
    timeZone,
  }).format(ms);
}

// Signed percentages with one decimal: the luck of a battle.
const percentFormats = new Map<Language, Intl.NumberFormat>();

/** -0.123 → "-12.3%" (en) / "-12,3 %" (de); a plus sign above zero, none for exactly zero. */
export function formatPercent(value: number, language: Language): string {
  let format = percentFormats.get(language);
  if (format === undefined) {
    format = new Intl.NumberFormat(locales[language], {
      style: 'percent',
      minimumFractionDigits: 1,
      maximumFractionDigits: 1,
      signDisplay: 'exceptZero',
    });
    percentFormats.set(language, format);
  }
  return format.format(value);
}

// Plain percentages of a rule value (a build time of 95.2% of the base), no sign.
const shareFormats = new Map<Language, Intl.NumberFormat>();

/** 95.2 → "95.2%" (en) / "95,2 %" (de); at most one decimal, none for whole numbers (25 → "25%"). */
export function formatShare(percent: number, language: Language): string {
  let format = shareFormats.get(language);
  if (format === undefined) {
    format = new Intl.NumberFormat(locales[language], {
      style: 'percent',
      minimumFractionDigits: 0,
      maximumFractionDigits: 1,
    });
    shareFormats.set(language, format);
  }
  return format.format(percent / 100);
}

/**
 * A moment in the next days, as short as possible: "21:14" today, "Tue 21:14" / "Di., 21:14" on
 * another day (end of a build order, when resources are enough).
 */
export function formatWhen(ms: number, nowMs: number, language: Language, timeZone?: string): string {
  return when(ms, nowMs, language, false, timeZone);
}

/**
 * Like formatWhen, with seconds: "21:14:05" / "Tue 21:14:05". For arrivals of troops, which players
 * time to the second.
 */
export function formatWhenExact(ms: number, nowMs: number, language: Language, timeZone?: string): string {
  return when(ms, nowMs, language, true, timeZone);
}

function when(ms: number, nowMs: number, language: Language, seconds: boolean, timeZone?: string): string {
  const sameDay = calendarDay(ms, timeZone) === calendarDay(nowMs, timeZone);
  return new Intl.DateTimeFormat(locales[language], {
    weekday: sameDay ? undefined : 'short',
    hour: '2-digit',
    minute: '2-digit',
    second: seconds ? '2-digit' : undefined,
    hourCycle: 'h23',
    timeZone,
  }).format(ms);
}

/**
 * A moment in the past days, as short as possible: "21:14" today, "Mon 21:14" within the last
 * six days, else with the date, "Tue 29 Sept, 21:14" (chat messages).
 */
export function formatRecent(ms: number, nowMs: number, language: Language, timeZone?: string): string {
  const sixDaysMs = 6 * 24 * 3_600_000;
  return nowMs - ms < sixDaysMs ? formatWhen(ms, nowMs, language, timeZone) : formatDateTime(ms, language, timeZone);
}

/**
 * A moment ahead, as short as possible: "21:14" today, "Wed 21:14" within the next six days, else with
 * the date. A weekday alone would be ambiguous from a week on ("Tue" – this one or the next?), which
 * happens when a big warehouse fills slowly (the warehouse page, M22).
 */
export function formatUpcoming(ms: number, nowMs: number, language: Language, timeZone?: string): string {
  const sixDaysMs = 6 * 24 * 3_600_000;
  return ms - nowMs < sixDaysMs ? formatWhen(ms, nowMs, language, timeZone) : formatDateTime(ms, language, timeZone);
}

/** The calendar day as "2026-10-03" (Swedish dates are ISO-like), in the device's or the given time zone. */
export function calendarDay(ms: number, timeZone?: string): string {
  return new Intl.DateTimeFormat('sv-SE', { year: 'numeric', month: '2-digit', day: '2-digit', timeZone }).format(ms);
}

/**
 * A day heading (the report list, 1.3): 'today', 'yesterday', or the day itself, "Wednesday 14
 * October" / "Mittwoch, 14. Oktober".
 */
export function formatDay(
  ms: number,
  nowMs: number,
  language: Language,
  timeZone?: string,
): 'today' | 'yesterday' | { date: string } {
  const day = calendarDay(ms, timeZone);
  const today = calendarDay(nowMs, timeZone);
  if (day === today) {
    return 'today';
  }
  // the calendar day before today (counted on the date, so a summer time change doesn't matter)
  const [year, month, date] = today.split('-').map(Number) as [number, number, number];
  if (day === new Date(Date.UTC(year, month - 1, date - 1)).toISOString().slice(0, 10)) {
    return 'yesterday';
  }
  return {
    date: new Intl.DateTimeFormat(locales[language], {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      timeZone,
    }).format(ms),
  };
}

/** Clock time without seconds, e.g. "21:14" (a line under a day heading). */
export function formatTime(ms: number, language: Language, timeZone?: string): string {
  return new Intl.DateTimeFormat(locales[language], {
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
    timeZone,
  }).format(ms);
}

/** Clock time with seconds, e.g. "21:14:07" (the server time display). */
export function formatClock(ms: number, language: Language, timeZone?: string): string {
  return new Intl.DateTimeFormat(locales[language], {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
    timeZone,
  }).format(ms);
}

/** Map coordinates as the game writes them: (48|52). */
export function formatCoords(x: number, y: number): string {
  return `(${x}|${y})`;
}

function pad2(value: number): string {
  return String(value).padStart(2, '0');
}
