import { describe, expect, it } from 'vitest';
import { amountsFromSearch, marketPath, marketSendPath } from './marketPaths';

describe('marketPath', () => {
  it('opens the market of a village', () => {
    expect(marketPath(7)).toBe('/v/7/market');
  });

  it('fills in the target of "Send resources" on the map', () => {
    expect(marketPath(7, { x: 48, y: 52 })).toBe('/v/7/market?x=48&y=52');
  });
});

describe('marketSendPath (2.1 S4)', () => {
  it('fills in the target and the amounts a lord asks for', () => {
    const path = marketSendPath(7, { x: 48, y: 52 }, { wood: 400, clay: 0, iron: 200 });
    expect(path).toBe('/v/7/market?x=48&y=52&wood=400&iron=200');
    expect(amountsFromSearch(new URL(path, 'http://x').searchParams)).toEqual({ wood: 400, clay: 0, iron: 200 });
  });

  it('ignores amounts that are no whole numbers above 0', () => {
    expect(amountsFromSearch(new URLSearchParams('wood=-5&clay=1.5&iron=abc'))).toEqual({ wood: 0, clay: 0, iron: 0 });
  });
});
