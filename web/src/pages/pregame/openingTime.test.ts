import { describe, expect, it } from 'vitest';
import { fromLocalInput, splitCountdown, toLocalInput } from './openingTime';

describe('splitCountdown', () => {
  it('splits whole days from the rest', () => {
    expect(splitCountdown(2 * 86_400 + 4 * 3600 + 46 * 60 + 40)).toEqual({ days: 2, rest: '4:46:40' });
  });

  it('has no days below 24 hours and never goes negative', () => {
    expect(splitCountdown(3661)).toEqual({ days: 0, rest: '1:01:01' });
    expect(splitCountdown(-5)).toEqual({ days: 0, rest: '0:00:00' });
  });
});

describe('the datetime-local conversion', () => {
  it('round-trips a time in the browser time zone', () => {
    const iso = new Date(2026, 9, 17, 18, 30).toISOString();
    expect(toLocalInput(iso)).toBe('2026-10-17T18:30');
    expect(fromLocalInput('2026-10-17T18:30')).toBe(iso);
  });

  it('answers empty / null for nothing and for text that is no time', () => {
    expect(toLocalInput(null)).toBe('');
    expect(toLocalInput('nonsense')).toBe('');
    expect(fromLocalInput('')).toBeNull();
    expect(fromLocalInput('17.10.2026')).toBeNull();
  });
});
