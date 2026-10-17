import { describe, expect, it } from 'vitest';
import { percentPoints } from './banners';
import {
  calendarDay,
  formatClock,
  formatCoords,
  formatDateTime,
  formatDateTimeExact,
  formatDay,
  formatDecimal,
  formatRatio,
  formatDuration,
  formatNumber,
  formatPercent,
  formatRecent,
  formatShare,
  formatTime,
  formatUpcoming,
  formatWhen,
  formatWhenExact,
} from './format';

describe('formatNumber', () => {
  it('rounds down and groups thousands per language', () => {
    expect(formatNumber(812.99, 'en')).toBe('812');
    expect(formatNumber(12345.6, 'en')).toBe('12,345');
    expect(formatNumber(12345.6, 'de')).toBe('12.345');
    expect(formatNumber(1_000_000, 'de')).toBe('1.000.000');
  });
});

describe('formatDecimal', () => {
  it('always shows one decimal, per language', () => {
    expect(formatDecimal(12.345, 'en')).toBe('12.3');
    expect(formatDecimal(12.345, 'de')).toBe('12,3');
    expect(formatDecimal(5, 'en')).toBe('5.0');
    expect(formatDecimal(1234.56, 'de')).toBe('1.234,6');
  });
});

describe('formatRatio', () => {
  it('shows up to two decimals, per language', () => {
    expect(formatRatio(1.5, 'en')).toBe('1.5');
    expect(formatRatio(1.5, 'de')).toBe('1,5');
    expect(formatRatio(2, 'en')).toBe('2');
    expect(formatRatio(1.25, 'de')).toBe('1,25');
  });
});

describe('formatDuration', () => {
  it('formats h:mm:ss', () => {
    expect(formatDuration(3723)).toBe('1:02:03');
    expect(formatDuration(59)).toBe('0:00:59');
    expect(formatDuration(27 * 3600)).toBe('27:00:00');
  });

  it('rounds down and never goes below zero', () => {
    expect(formatDuration(59.9)).toBe('0:00:59');
    expect(formatDuration(-5)).toBe('0:00:00');
  });
});

// Exact date strings differ slightly between ICU versions, so only the important parts are checked.
describe('formatDateTime', () => {
  const ms = Date.parse('2026-10-06T21:14:00Z');

  it('shows day, month and 24-hour time', () => {
    const en = formatDateTime(ms, 'en', 'UTC');
    expect(en).toContain('6 Oct');
    expect(en).toContain('21:14');
    const de = formatDateTime(ms, 'de', 'UTC');
    expect(de).toContain('Okt');
    expect(de).toContain('21:14');
  });
});

describe('formatDateTimeExact', () => {
  it('adds the seconds', () => {
    const ms = Date.parse('2026-10-06T21:14:05Z');
    const en = formatDateTimeExact(ms, 'en', 'UTC');
    expect(en).toContain('6 Oct');
    expect(en).toContain('21:14:05');
    expect(formatDateTimeExact(ms, 'de', 'UTC')).toContain('21:14:05');
  });
});

describe('formatPercent', () => {
  it('shows one decimal and the sign', () => {
    expect(formatPercent(-0.123, 'en')).toBe('-12.3%');
    expect(formatPercent(0.05, 'en')).toBe('+5.0%');
    expect(formatPercent(0, 'en')).toBe('0.0%');
    // German puts a (non-breaking) space before the percent sign
    expect(formatPercent(-0.123, 'de').replace(/\s/g, ' ')).toBe('-12,3 %');
  });
});

describe('formatWhen', () => {
  const now = Date.parse('2026-10-06T08:00:00Z'); // a Tuesday

  it('shows only the time on the same day', () => {
    expect(formatWhen(Date.parse('2026-10-06T21:14:00Z'), now, 'en', 'UTC')).toBe('21:14');
    expect(formatWhen(Date.parse('2026-10-06T21:14:00Z'), now, 'de', 'UTC')).toBe('21:14');
  });

  it('adds the weekday on another day', () => {
    const en = formatWhen(Date.parse('2026-10-07T03:05:00Z'), now, 'en', 'UTC');
    expect(en).toContain('Wed');
    expect(en).toContain('03:05');
    expect(formatWhen(Date.parse('2026-10-07T03:05:00Z'), now, 'de', 'UTC')).toContain('Mi');
  });

  it('decides the day in the given time zone', () => {
    // 23:30 UTC is still Tuesday in UTC, but already Wednesday in Berlin
    const late = Date.parse('2026-10-06T23:30:00Z');
    expect(formatWhen(late, now, 'en', 'UTC')).toBe('23:30');
    expect(formatWhen(late, now, 'en', 'Europe/Berlin')).toContain('Wed');
  });
});

describe('formatWhenExact', () => {
  const now = Date.parse('2026-10-06T08:00:00Z'); // a Tuesday

  it('shows the time with seconds on the same day', () => {
    expect(formatWhenExact(Date.parse('2026-10-06T21:14:05Z'), now, 'en', 'UTC')).toBe('21:14:05');
    expect(formatWhenExact(Date.parse('2026-10-06T21:14:05Z'), now, 'de', 'UTC')).toBe('21:14:05');
  });

  it('adds the weekday on another day', () => {
    const en = formatWhenExact(Date.parse('2026-10-07T03:05:09Z'), now, 'en', 'UTC');
    expect(en).toContain('Wed');
    expect(en).toContain('03:05:09');
  });
});

describe('formatClock', () => {
  it('shows hh:mm:ss', () => {
    expect(formatClock(Date.parse('2026-10-06T09:04:07Z'), 'de', 'UTC')).toBe('09:04:07');
  });
});

describe('formatCoords', () => {
  it('writes x|y in brackets', () => {
    expect(formatCoords(48, 52)).toBe('(48|52)');
  });
});

describe('formatRecent', () => {
  const now = Date.parse('2026-10-06T20:00:00Z'); // a Tuesday

  it('shows the time today, the weekday within six days, else the date', () => {
    expect(formatRecent(Date.parse('2026-10-06T08:30:00Z'), now, 'en', 'UTC')).toBe('08:30');
    expect(formatRecent(Date.parse('2026-10-05T08:30:00Z'), now, 'en', 'UTC')).toContain('Mon');
    const old = formatRecent(Date.parse('2026-09-29T08:30:00Z'), now, 'en', 'UTC');
    expect(old).toContain('29');
    expect(old).toContain('08:30');
  });
});

describe('formatShare', () => {
  it('keeps one decimal and drops it for whole numbers', () => {
    expect(formatShare(95.2, 'en')).toBe('95.2%');
    expect(formatShare(25, 'en')).toBe('25%');
    expect(formatShare(4.5, 'de')).toBe('4,5\u00a0%');
  });

  it('shows a theft chance with its decimal instead of flooring it', () => {
    expect(formatShare(percentPoints(0.1234), 'en')).toBe('12.3%');
    expect(formatShare(percentPoints(0.1275), 'de')).toBe('12,8\u00a0%');
  });
});

describe('formatUpcoming', () => {
  const now = Date.parse('2026-10-06T20:00:00Z'); // a Tuesday

  it('shows the time today, the weekday within six days, else the date', () => {
    expect(formatUpcoming(Date.parse('2026-10-06T23:30:00Z'), now, 'en', 'UTC')).toBe('23:30');
    expect(formatUpcoming(Date.parse('2026-10-08T08:30:00Z'), now, 'en', 'UTC')).toContain('Thu');
    // a week ahead is also a Tuesday: the date says which one
    const far = formatUpcoming(Date.parse('2026-10-13T20:00:00Z'), now, 'en', 'UTC');
    expect(far).toContain('13');
    expect(far).toContain('20:00');
  });
});

describe('formatDay', () => {
  const now = Date.parse('2026-10-16T08:00:00Z'); // a Friday

  it('says today and yesterday, else the weekday and the date', () => {
    expect(formatDay(Date.parse('2026-10-16T00:10:00Z'), now, 'en', 'UTC')).toBe('today');
    expect(formatDay(Date.parse('2026-10-15T23:59:00Z'), now, 'en', 'UTC')).toBe('yesterday');
    expect(formatDay(Date.parse('2026-10-14T12:00:00Z'), now, 'en', 'UTC')).toEqual({ date: 'Wednesday 14 October' });
    expect(formatDay(Date.parse('2026-10-14T12:00:00Z'), now, 'de', 'UTC')).toEqual({ date: 'Mittwoch, 14. Oktober' });
  });

  it('counts yesterday on the calendar, also across a month and a summer time change', () => {
    // Berlin goes from summer to winter time on 25 October 2026: that day has 25 hours
    const morning = Date.parse('2026-10-26T00:30:00+01:00');
    expect(formatDay(Date.parse('2026-10-25T00:30:00+02:00'), morning, 'en', 'Europe/Berlin')).toBe('yesterday');
    expect(formatDay(Date.parse('2026-09-30T20:00:00Z'), Date.parse('2026-10-01T06:00:00Z'), 'en', 'UTC')).toBe(
      'yesterday',
    );
  });

  it('decides the day in the given time zone', () => {
    // 23:30 UTC on Thursday is already Friday in Berlin
    const late = Date.parse('2026-10-15T23:30:00Z');
    expect(formatDay(late, now, 'en', 'UTC')).toBe('yesterday');
    expect(formatDay(late, now, 'en', 'Europe/Berlin')).toBe('today');
    expect(calendarDay(late, 'Europe/Berlin')).toBe('2026-10-16');
  });
});

describe('formatTime', () => {
  it('is the clock time without seconds', () => {
    expect(formatTime(Date.parse('2026-10-16T07:05:09Z'), 'en', 'UTC')).toBe('07:05');
    expect(formatTime(Date.parse('2026-10-16T21:14:59Z'), 'de', 'UTC')).toBe('21:14');
  });
});
