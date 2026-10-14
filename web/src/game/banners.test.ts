import { describe, expect, it } from 'vitest';
import type { BannerType } from './gameData';
import {
  effectOfTier,
  effectiveTier,
  fusableByTier,
  fuseTypes,
  percentPoints,
  stashOf,
  tierOfEffect,
  toggleFuseChoice,
  type BannerLike,
} from './banners';

describe('effectiveTier', () => {
  it('is the lower of tier and hall level, 0 without a hall', () => {
    expect(effectiveTier(3, 1)).toBe(1);
    expect(effectiveTier(2, 3)).toBe(2);
    expect(effectiveTier(3, 0)).toBe(0);
  });
});

describe('effectOfTier', () => {
  const effects = { HARVEST: [0.05, 0.1, 0.15] };
  it('reads the table, 0 for tier 0 and for a type without numbers', () => {
    expect(effectOfTier(effects, 'HARVEST', 2)).toBe(0.1);
    expect(effectOfTier(effects, 'HARVEST', 0)).toBe(0);
    expect(effectOfTier(effects, 'WAR', 1)).toBe(0);
  });
});

describe('percentPoints', () => {
  it('rounds to one decimal', () => {
    expect(percentPoints(0.1)).toBe(10);
    expect(percentPoints(0.045)).toBe(4.5);
    expect(percentPoints(0.15)).toBe(15);
  });
});

describe('stashOf', () => {
  it('leaves out flying banners and sorts by tier, then type', () => {
    const banners: BannerLike[] = [
      { id: 1, type: 'WAR', tier: 1, villageId: null },
      { id: 2, type: 'HARVEST', tier: 1, villageId: null },
      { id: 3, type: 'OATH', tier: 3, villageId: null },
      { id: 4, type: 'DRILL', tier: 2, villageId: 7 },
    ];
    expect(stashOf(banners).map((banner) => banner.id)).toEqual([3, 2, 1]);
  });
});

describe('fusing', () => {
  const banner = (id: number, type: BannerType, tier: number, villageId: number | null = null): BannerLike => ({
    id,
    type,
    tier,
    villageId,
  });

  it('offers the stash banners of tier I and II, never tier III or flying ones', () => {
    const stash = [banner(1, 'WAR', 1), banner(2, 'HASTE', 2), banner(3, 'OATH', 3), banner(4, 'WAR', 1, 9)];
    expect(fusableByTier(stash)).toEqual([
      { tier: 1, banners: [stash[0]] },
      { tier: 2, banners: [stash[1]] },
    ]);
  });

  it('chooses up to three of one tier; another tier starts over; a second tap drops one', () => {
    const a = banner(1, 'WAR', 1);
    const b = banner(2, 'HASTE', 1);
    const c = banner(3, 'WAR', 1);
    const d = banner(4, 'DRILL', 1);
    const high = banner(5, 'OATH', 2);
    let chosen = toggleFuseChoice([], a);
    chosen = toggleFuseChoice(chosen, b);
    chosen = toggleFuseChoice(chosen, c);
    expect(chosen.map((entry) => entry.id)).toEqual([1, 2, 3]);
    expect(toggleFuseChoice(chosen, d).map((entry) => entry.id)).toEqual([1, 2, 3]);
    expect(toggleFuseChoice(chosen, b).map((entry) => entry.id)).toEqual([1, 3]);
    expect(toggleFuseChoice(chosen, high).map((entry) => entry.id)).toEqual([5]);
  });

  it('lets the new banner take one of the chosen types, each once', () => {
    expect(fuseTypes([banner(1, 'WAR', 1), banner(2, 'HARVEST', 1), banner(3, 'WAR', 1)])).toEqual(['HARVEST', 'WAR']);
  });
});

describe('tierOfEffect', () => {
  const effects = { WAR: [0.03, 0.06, 0.1] };
  it('finds the tier of a report’s percent, 0 for none or an unknown number', () => {
    expect(tierOfEffect(effects, 'WAR', 0.06)).toBe(2);
    expect(tierOfEffect(effects, 'WAR', 0.1)).toBe(3);
    expect(tierOfEffect(effects, 'WAR', null)).toBe(0);
    expect(tierOfEffect(effects, 'WAR', 0.07)).toBe(0);
    expect(tierOfEffect(effects, 'BULWARK', 0.03)).toBe(0);
  });
});
