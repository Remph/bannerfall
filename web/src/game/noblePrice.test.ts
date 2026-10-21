import { describe, expect, it } from 'vitest';
import {
  nobleFactor,
  nobleMaxAffordable,
  noblePrice,
  nobleRank,
  nobleUnitPrice,
  type NoblePricing,
} from './noblePrice';

const fresh: NoblePricing = {
  villages: 1,
  nobles: 0,
  freeRanks: 4,
  step: 0.25,
  maxFactor: 7,
  baseCost: { wood: 40_000, clay: 50_000, iron: 50_000 },
};

describe('the rising noble price (02-game-rules §5.7)', () => {
  it('counts villages and nobles beyond the first four', () => {
    expect(nobleRank(fresh, 4)).toBe(0);
    expect(nobleRank(fresh, 5)).toBe(1);
    expect(nobleRank(fresh, 0)).toBe(0);
  });

  it('adds the step per rank up to the maximum', () => {
    expect(nobleFactor(fresh, 0)).toBe(1);
    expect(nobleFactor(fresh, 4)).toBe(2);
    expect(nobleFactor(fresh, 100)).toBe(7);
    expect(nobleFactor({ ...fresh, step: 0 }, 50)).toBe(1);
    expect(noblePrice(fresh, 1)).toEqual({ wood: 50_000, clay: 62_500, iron: 62_500 });
  });

  it('charges an order the average of its nobles, like the server', () => {
    // the first four of a fresh player at the base price, a fifth in the same order at rank 1
    expect(nobleUnitPrice(fresh, 4)).toEqual(fresh.baseCost);
    expect(nobleUnitPrice(fresh, 5).clay).toBe(52_500);
    // five held: ranks 1 and 2
    expect(nobleUnitPrice({ ...fresh, nobles: 4 }, 2)).toEqual({ wood: 55_000, clay: 68_750, iron: 68_750 });
    expect(nobleUnitPrice({ ...fresh, nobles: 4 }, 0)).toEqual({ wood: 50_000, clay: 62_500, iron: 62_500 });
  });

  it('pays for as many as the rising price allows', () => {
    const stock = { wood: 200_000.7, clay: 200_000, iron: 200_000 };
    expect(nobleMaxAffordable(fresh, stock, 10_000, 100)).toBe(4);
    expect(nobleMaxAffordable({ ...fresh, nobles: 4 }, stock, 10_000, 100)).toBe(2);
    expect(nobleMaxAffordable(fresh, stock, 250, 100)).toBe(2);
    expect(nobleMaxAffordable(fresh, stock, -5, 100)).toBe(0);
  });
});
