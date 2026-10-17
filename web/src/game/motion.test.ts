import { afterEach, describe, expect, it, vi } from 'vitest';
import { COUNT_MS, countedValue, easeInOut, easeOut, isResourceJump, prefersReducedMotion } from './motion';

describe('motion', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('eases from 0 to 1 and stays inside', () => {
    for (const ease of [easeOut, easeInOut]) {
      expect(ease(0)).toBe(0);
      expect(ease(1)).toBe(1);
      expect(ease(-1)).toBe(0);
      expect(ease(2)).toBe(1);
    }
    expect(easeOut(0.5)).toBeGreaterThan(0.5); // fast start
    expect(easeInOut(0.5)).toBeCloseTo(0.5);
  });

  it('counts only a jump, not the growth with the production', () => {
    // 3,600 an hour is 1 a second: a tick of a second or two is growth
    expect(isResourceJump(500, 502, 3600)).toBe(false);
    expect(isResourceJump(500, 520, 3600)).toBe(true);
    // at 36,000 an hour (10 a second) 2 seconds' worth is growth, 3 seconds' worth a jump
    expect(isResourceJump(500, 520, 36000)).toBe(false);
    expect(isResourceJump(500, 530, 36000)).toBe(true);
    expect(isResourceJump(800, 300, 3600)).toBe(true); // a payment
  });

  it('counts from the old to the new amount in COUNT_MS', () => {
    expect(countedValue(100, 400, 0)).toBe(100);
    expect(countedValue(100, 400, COUNT_MS / 2)).toBeGreaterThan(250);
    expect(countedValue(100, 400, COUNT_MS)).toBe(400);
    expect(countedValue(100, 400, COUNT_MS * 3)).toBe(400);
    expect(countedValue(400, 100, COUNT_MS)).toBe(100);
  });

  it('asks the device about reduced motion', () => {
    vi.stubGlobal('window', {
      matchMedia: (query: string) => ({ matches: query === '(prefers-reduced-motion: reduce)' }),
    });
    expect(prefersReducedMotion()).toBe(true);
    vi.stubGlobal('window', {}); // no matchMedia, as in some test set-ups
    expect(prefersReducedMotion()).toBe(false);
  });
});
