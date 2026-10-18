import { describe, expect, it } from 'vitest';
import { formatNightHours, hourIn, isNight, nightBonusAt, type NightBonus } from './nightBonus';

const night: NightBonus = { startHour: 23, endHour: 7, factor: 2, zone: 'Europe/Berlin' };

describe('nightBonus', () => {
  it('reads the hour in the world’s zone, in summer and in winter time', () => {
    expect(hourIn(Date.parse('2026-10-18T21:00:00Z'), 'Europe/Berlin')).toBe(23);
    expect(hourIn(Date.parse('2026-12-01T21:00:00Z'), 'Europe/Berlin')).toBe(22);
    expect(hourIn(Date.parse('2026-10-18T22:30:00Z'), 'Europe/Berlin')).toBe(0);
  });

  it('runs across midnight with the start hour in and the end hour out', () => {
    expect(isNight(night, Date.parse('2026-10-18T20:59:59Z'))).toBe(false); // 22:59:59
    expect(isNight(night, Date.parse('2026-10-18T21:00:00Z'))).toBe(true); // 23:00
    expect(isNight(night, Date.parse('2026-10-19T04:59:59Z'))).toBe(true); // 06:59:59
    expect(isNight(night, Date.parse('2026-10-19T05:00:00Z'))).toBe(false); // 07:00
  });

  it('knows a window within one day', () => {
    const early: NightBonus = { ...night, startHour: 1, endHour: 6 };
    expect(isNight(early, Date.parse('2026-10-18T23:00:00Z'))).toBe(true); // 01:00
    expect(isNight(early, Date.parse('2026-10-19T04:00:00Z'))).toBe(false); // 06:00
    expect(isNight(early, Date.parse('2026-10-18T22:00:00Z'))).toBe(false); // 00:00
  });

  it('gives the factor only at night, for owned villages and in a world that has it', () => {
    const midnight = Date.parse('2026-10-18T22:00:00Z');
    const noon = Date.parse('2026-10-19T10:00:00Z');
    expect(nightBonusAt(night, midnight, true)).toBe(2);
    expect(nightBonusAt(night, noon, true)).toBeNull();
    expect(nightBonusAt(night, midnight, false)).toBeNull();
    expect(nightBonusAt(null, midnight, true)).toBeNull();
    expect(nightBonusAt(undefined, midnight, true)).toBeNull();
  });

  it('writes the window as hours', () => {
    expect(formatNightHours(night)).toBe('23:00–07:00');
    expect(formatNightHours({ startHour: 0, endHour: 6 })).toBe('00:00–06:00');
  });
});
