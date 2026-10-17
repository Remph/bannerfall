import { describe, expect, it } from 'vitest';
import {
  exchangeCost,
  maxAcceptable,
  maxExchange,
  maxOfferCount,
  maxSendable,
  merchantsForOffer,
  merchantsNeeded,
  nextMarketChangeMs,
  offerValue,
  ratioAllowed,
  resourceKey,
  totalOf,
  travelSeconds,
  wantRange,
} from './market';

describe('merchants', () => {
  it('carry 1000 each, rounded up', () => {
    expect(merchantsNeeded(0)).toBe(0);
    expect(merchantsNeeded(1)).toBe(1);
    expect(merchantsNeeded(1000)).toBe(1);
    expect(merchantsNeeded(1001)).toBe(2);
  });

  it('are counted per unit of an offer', () => {
    // 3 × 1500 wood: 2 merchants each, not ceil(4500 / 1000) = 5
    expect(merchantsForOffer(1500, 3)).toBe(6);
  });

  it('take 6 minutes per field, divided by the world speeds (like the server)', () => {
    expect(travelSeconds(10, { speed: 1, unitSpeed: 1 })).toBe(3600);
    expect(travelSeconds(10, { speed: 3, unitSpeed: 1 })).toBe(1200);
    expect(travelSeconds(0.001, { speed: 1000, unitSpeed: 1 })).toBe(1);
  });
});

describe('offer ratios', () => {
  it('allow 1/max to max', () => {
    expect(ratioAllowed(100, 300, 3)).toBe(true);
    expect(ratioAllowed(100, 301, 3)).toBe(false);
    expect(ratioAllowed(300, 100, 3)).toBe(true);
    expect(ratioAllowed(301, 100, 3)).toBe(false);
    expect(ratioAllowed(0, 100, 3)).toBe(false);
  });

  it('name the range of the wanted amount', () => {
    expect(wantRange(100, 3)).toEqual({ min: 34, max: 300 });
    expect(wantRange(1, 3)).toEqual({ min: 1, max: 3 });
    const { min, max } = wantRange(250, 3);
    expect(ratioAllowed(250, min, 3)).toBe(true);
    expect(ratioAllowed(250, min - 1, 3)).toBe(false);
    expect(ratioAllowed(250, max, 3)).toBe(true);
    expect(ratioAllowed(250, max + 1, 3)).toBe(false);
  });

  it('rates an offer by what one gets per paid resource', () => {
    expect(offerValue({ offerAmount: 300, wantAmount: 100 })).toBe(3);
  });
});

describe('limits', () => {
  it('cap a transport by stock and by what the free merchants can still carry', () => {
    expect(maxSendable(5000.9, 5, 0)).toBe(5000);
    expect(maxSendable(5000, 3, 0)).toBe(3000);
    expect(maxSendable(5000, 3, 2500)).toBe(500);
    expect(maxSendable(5000, 3, 4000)).toBe(0);
  });

  it('cap the units of an own offer by stock, merchants and 1000', () => {
    expect(maxOfferCount(5000, 5, 1500)).toBe(2); // 2 merchants per unit: 5 / 2
    expect(maxOfferCount(5000, 50, 1500)).toBe(3); // stock: 5000 / 1500
    expect(maxOfferCount(5_000_000, 5000, 1)).toBe(1000);
    expect(maxOfferCount(5000, 5, 0)).toBe(0);
  });

  it('cap an acceptance by what is left, the stock and the merchants', () => {
    expect(maxAcceptable(10, 100, 5000, 5)).toBe(10);
    expect(maxAcceptable(10, 100, 450, 5)).toBe(4);
    expect(maxAcceptable(10, 1500, 90000, 2)).toBe(1); // 2 merchants carry 2000
    expect(maxAcceptable(10, 100, 5000, 0)).toBe(0);
  });
});

describe('helpers', () => {
  it('map the API names to the amounts keys', () => {
    expect(resourceKey('WOOD')).toBe('wood');
    expect(resourceKey('CLAY')).toBe('clay');
    expect(resourceKey('IRON')).toBe('iron');
    expect(totalOf({ wood: 1, clay: 20, iron: 300 })).toBe(321);
  });

  it('find the next change: an arrival, or the merchants coming home once it arrived', () => {
    const on = { arriveAt: '2026-10-07T10:00:00Z', returnAt: '2026-10-07T12:00:00Z', arrived: false };
    const done = { arriveAt: '2026-10-07T09:00:00Z', returnAt: '2026-10-07T11:00:00Z', arrived: true };
    expect(nextMarketChangeMs([])).toBeUndefined();
    expect(nextMarketChangeMs([on, done])).toBe(Date.parse('2026-10-07T10:00:00Z'));
    expect(nextMarketChangeMs([done])).toBe(Date.parse('2026-10-07T11:00:00Z'));
    // a new day of trading with the market counts too, whichever comes first
    expect(nextMarketChangeMs([], '2026-10-07T22:00:00Z')).toBe(Date.parse('2026-10-07T22:00:00Z'));
    expect(nextMarketChangeMs([done], '2026-10-07T22:00:00Z')).toBe(Date.parse('2026-10-07T11:00:00Z'));
    expect(nextMarketChangeMs([done], '2026-10-07T10:30:00Z')).toBe(Date.parse('2026-10-07T10:30:00Z'));
  });
});

describe('trading with the market', () => {
  it('rounds the cost up, counting in decimals like the server', () => {
    expect(exchangeCost(1000, 1.5)).toBe(1500);
    expect(exchangeCost(1, 1.5)).toBe(2);
    expect(exchangeCost(1000, 1.1)).toBe(1100);
    expect(exchangeCost(7, 3.3)).toBe(24);
    expect(exchangeCost(0, 1.5)).toBe(0);
  });

  it('gets at most what is left today, what the stock pays for and what fits', () => {
    expect(maxExchange(5000, 3000, 1.5, 10_000)).toBe(2000);
    expect(maxExchange(800, 3000, 1.5, 10_000)).toBe(800);
    expect(maxExchange(5000, 3000, 1.5, 120.7)).toBe(120);
    // 3 pays for 2 at 1.5 (cost 3), not for floor(3 / 1.5) + rounding
    expect(maxExchange(5000, 3.9, 1.5, 100)).toBe(2);
    expect(maxExchange(5000, 10, 3.3, 100)).toBe(3);
    expect(maxExchange(0, 3000, 1.5, 100)).toBe(0);
    expect(maxExchange(-10, 3000, 1.5, 100)).toBe(0);
    expect(maxExchange(5000, 3000, 0, 100)).toBe(0);
  });
});
