import { describe, expect, it } from 'vitest';
import { cappedBySlots, freePaladinSlots } from './paladin';

describe('freePaladinSlots', () => {
  it('is what the paladins leave of the slots', () => {
    expect(freePaladinSlots({ slots: 2, used: 0 })).toBe(2);
    expect(freePaladinSlots({ slots: 2, used: 1 })).toBe(1);
    expect(freePaladinSlots({ slots: 2, used: 2 })).toBe(0);
  });

  it('is never negative: a lost village can leave more paladins than slots', () => {
    expect(freePaladinSlots({ slots: 1, used: 2 })).toBe(0);
    expect(freePaladinSlots({ slots: 0, used: 0 })).toBe(0);
  });
});

describe('cappedBySlots', () => {
  it('lowers a count cap to the free slots', () => {
    expect(cappedBySlots(7, 2)).toBe(2);
    expect(cappedBySlots(1, 3)).toBe(1);
    expect(cappedBySlots(7, 0)).toBe(0);
  });

  it('leaves the cap alone for other units and while the slots load', () => {
    expect(cappedBySlots(7, undefined)).toBe(7);
  });
});
