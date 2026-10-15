import { describe, expect, it } from 'vitest';
import {
  bannerRollBackAt,
  farmSendEndsProtection,
  farmUnitTypes,
  fitsHome,
  hasUnits,
  sendAllNotes,
  templateOf,
} from './farm';

describe('farmUnitTypes', () => {
  it('has every type but the noble, the paladin and the banner thief, in unit order', () => {
    expect(farmUnitTypes).toEqual([
      'SPEARMAN',
      'SWORDSMAN',
      'AXEMAN',
      'SCOUT',
      'LIGHT_CAVALRY',
      'HEAVY_CAVALRY',
      'RAM',
      'CATAPULT',
    ]);
  });
});

describe('bannerRollBackAt', () => {
  const now = Date.parse('2026-10-15T08:00:00Z');

  it('is the time the roll comes back while that is ahead', () => {
    expect(bannerRollBackAt('2026-10-15T14:30:00Z', now)).toBe(Date.parse('2026-10-15T14:30:00Z'));
  });

  it('is undefined when the roll is free (null) or back already', () => {
    expect(bannerRollBackAt(null, now)).toBeUndefined();
    expect(bannerRollBackAt('2026-10-15T08:00:00Z', now)).toBeUndefined();
  });
});

describe('templateOf', () => {
  it('keeps only the types with units', () => {
    expect(templateOf({ AXEMAN: 10, SPEARMAN: 0, SCOUT: 1 })).toEqual({ AXEMAN: 10, SCOUT: 1 });
  });

  it('never sends a noble', () => {
    expect(templateOf({ NOBLE: 1, AXEMAN: 2 })).toEqual({ AXEMAN: 2 });
  });
});

describe('hasUnits', () => {
  it('is false for an empty or all-zero template', () => {
    expect(hasUnits({})).toBe(false);
    expect(hasUnits({ AXEMAN: 0 })).toBe(false);
    expect(hasUnits({ NOBLE: 1 })).toBe(false);
  });

  it('is true with a unit', () => {
    expect(hasUnits({ SCOUT: 1 })).toBe(true);
  });
});

describe('fitsHome', () => {
  it('needs every type at home', () => {
    expect(fitsHome({ AXEMAN: 10 }, { AXEMAN: 10, SPEARMAN: 5 })).toBe(true);
    expect(fitsHome({ AXEMAN: 11 }, { AXEMAN: 10 })).toBe(false);
    expect(fitsHome({ SCOUT: 1 }, {})).toBe(false);
  });

  it('fits an empty template anywhere', () => {
    expect(fitsHome({}, {})).toBe(true);
  });
});

describe('farmSendEndsProtection', () => {
  const nowMs = Date.parse('2026-10-12T12:00:00Z');
  const protectedUntil = nowMs + 3_600_000;

  it('is true for a protected player attacking a player', () => {
    expect(farmSendEndsProtection(protectedUntil, 42, nowMs)).toBe(true);
  });

  it('is false for a barbarian village (no player): farm templates carry no nobles', () => {
    expect(farmSendEndsProtection(protectedUntil, null, nowMs)).toBe(false);
  });

  it('is false once the protection is over, also at its very end', () => {
    expect(farmSendEndsProtection(nowMs - 1, 42, nowMs)).toBe(false);
    expect(farmSendEndsProtection(nowMs, 42, nowMs)).toBe(false);
  });

  it('is false for a player who never had protection (unparsable date)', () => {
    expect(farmSendEndsProtection(Number.NaN, 42, nowMs)).toBe(false);
  });
});

describe('sendAllNotes', () => {
  it('marks the sent, the skipped and the entry the run stopped at', () => {
    const notes = sendAllNotes({
      sent: [1, 3],
      skipped: [{ targetId: 2, reason: 'PROTECTED_TARGET' }],
      stop: 'NOT_ENOUGH_UNITS',
      stoppedAt: 4,
    });
    expect(notes.get(1)).toEqual({ kind: 'sent' });
    expect(notes.get(2)).toEqual({ kind: 'skipped', reason: 'PROTECTED_TARGET' });
    expect(notes.get(3)).toEqual({ kind: 'sent' });
    expect(notes.get(4)).toEqual({ kind: 'stopped' });
    // after the stop: untouched
    expect(notes.has(5)).toBe(false);
  });

  it('has no stop mark when the list was done', () => {
    const notes = sendAllNotes({ sent: [7], skipped: [], stop: 'DONE', stoppedAt: null });
    expect([...notes.keys()]).toEqual([7]);
  });
});
