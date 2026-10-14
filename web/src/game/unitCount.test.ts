import { describe, expect, it } from 'vitest';
import { countBounds, parseCount } from './unitCount';

describe('parseCount', () => {
  it('reads whole numbers', () => {
    expect(parseCount('12', 100)).toBe(12);
    expect(parseCount('007', 100)).toBe(7);
  });

  it('treats an empty field as 0', () => {
    expect(parseCount('', 100)).toBe(0);
  });

  it('ignores everything but digits', () => {
    expect(parseCount('1.200', 5000)).toBe(1200);
    expect(parseCount(' 3a ', 100)).toBe(3);
    expect(parseCount('-5', 100)).toBe(5);
    expect(parseCount('abc', 100)).toBe(0);
  });

  it('stops at the limit', () => {
    expect(parseCount('30001', 30_000)).toBe(30_000);
    expect(parseCount('99999999999999999999999', 30_000)).toBe(30_000);
  });
});

describe('countBounds', () => {
  it('stops + and the chip at max by default', () => {
    expect(countBounds(20, 30_000)).toEqual({ chip: 20, plus: 20 });
  });

  it('never goes above the limit', () => {
    expect(countBounds(50_000, 30_000)).toEqual({ chip: 30_000, plus: 30_000 });
  });

  it('lets + count past max with stepMax while the chip stays at max (farm templates)', () => {
    expect(countBounds(20, 30_000, 30_000)).toEqual({ chip: 20, plus: 30_000 });
    expect(countBounds(0, 30_000, 30_000)).toEqual({ chip: 0, plus: 30_000 });
  });

  it('caps stepMax at the limit too', () => {
    expect(countBounds(20, 1000, 5000)).toEqual({ chip: 20, plus: 1000 });
  });
});
