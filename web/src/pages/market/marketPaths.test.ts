import { describe, expect, it } from 'vitest';
import { marketPath } from './marketPaths';

describe('marketPath', () => {
  it('opens the market of a village', () => {
    expect(marketPath(7)).toBe('/v/7/market');
  });

  it('fills in the target of "Send resources" on the map', () => {
    expect(marketPath(7, { x: 48, y: 52 })).toBe('/v/7/market?x=48&y=52');
  });
});
