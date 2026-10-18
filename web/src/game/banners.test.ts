import { describe, expect, it } from 'vitest';
import type { BannerType } from './gameData';
import {
  bannerHallAvailable,
  effectOfTier,
  effectiveTier,
  fusableByTier,
  fuseTypes,
  groupStash,
  percentPoints,
  stashOf,
  standardTier,
  tierOfEffect,
  toggleFuseChoice,
  type BannerLike,
} from './banners';

describe('bannerHallAvailable', () => {
  it('is there while banners or paladins are on (1.4)', () => {
    expect(bannerHallAvailable({ bannersEnabled: true, paladinMaxSlots: 0 })).toBe(true);
    expect(bannerHallAvailable({ bannersEnabled: false, paladinMaxSlots: 5 })).toBe(true);
    expect(bannerHallAvailable({ bannersEnabled: false, paladinMaxSlots: 0 })).toBe(false);
  });
});

describe('standardTier', () => {
  it('is capped by the paladin level and asleep without a hall (1.4)', () => {
    expect(standardTier(3, 1, 1)).toBe(1);
    expect(standardTier(2, 3, 1)).toBe(2);
    expect(standardTier(3, 3, 0)).toBe(0);
  });
});

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

  it('puts the paladin standards after the banners (1.4)', () => {
    const banners: BannerLike[] = [
      { id: 1, type: 'AXE_STANDARD', tier: 3, villageId: null },
      { id: 2, type: 'WAR', tier: 1, villageId: null },
      { id: 3, type: 'SPEAR_STANDARD', tier: 3, villageId: null },
    ];
    expect(stashOf(banners).map((banner) => banner.id)).toEqual([2, 3, 1]);
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
      { standard: false, tier: 1, banners: [stash[0]] },
      { standard: false, tier: 2, banners: [stash[1]] },
    ]);
  });

  it('groups the paladin standards apart once the stash has one (1.4)', () => {
    const stash = [banner(1, 'WAR', 1), banner(2, 'AXE_STANDARD', 1)];
    expect(fusableByTier(stash)).toEqual([
      { standard: false, tier: 1, banners: [stash[0]] },
      { standard: false, tier: 2, banners: [] },
      { standard: true, tier: 1, banners: [stash[1]] },
      { standard: true, tier: 2, banners: [] },
    ]);
  });

  it('starts over when a standard joins chosen banners', () => {
    const chosen = toggleFuseChoice(toggleFuseChoice([], banner(1, 'WAR', 1)), banner(2, 'HASTE', 1));
    expect(toggleFuseChoice(chosen, banner(3, 'RAM_STANDARD', 1)).map((entry) => entry.id)).toEqual([3]);
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

describe('groupStash', () => {
  it('puts banners of one type and tier in one row with their count, in stash order', () => {
    const stash: BannerLike[] = [
      { id: 1, type: 'WAR', tier: 3, villageId: null },
      { id: 2, type: 'BULWARK', tier: 1, villageId: null },
      { id: 3, type: 'BULWARK', tier: 1, villageId: null },
      { id: 4, type: 'BULWARK', tier: 2, villageId: null },
      { id: 5, type: 'BULWARK', tier: 1, villageId: null },
    ];

    expect(groupStash(stash).map(({ banner, count }) => [banner.id, count])).toEqual([
      [1, 1],
      [2, 3],
      [4, 1],
    ]);
  });
});
