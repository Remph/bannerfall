import { describe, expect, it } from 'vitest';
import { mapPath, rallyPath, simulatorPath, targetFromSearch } from './rallyPaths';

describe('rallyPath', () => {
  it('links to the rally point, with or without a target', () => {
    expect(rallyPath(7)).toBe('/v/7/rally');
    expect(rallyPath(7, { x: 48, y: 52 })).toBe('/v/7/rally?x=48&y=52');
  });
});

describe('mapPath', () => {
  it('centres the map on the field', () => {
    expect(mapPath(7, { x: 0, y: 99 })).toBe('/v/7/map?x=0&y=99');
  });
});

describe('targetFromSearch', () => {
  it('reads x and y', () => {
    expect(targetFromSearch(new URLSearchParams('x=48&y=52'))).toBe('48|52');
    expect(targetFromSearch(new URLSearchParams('y=5&x=007'))).toBe('7|5');
  });

  it('is empty without valid coordinates', () => {
    expect(targetFromSearch(new URLSearchParams(''))).toBe('');
    expect(targetFromSearch(new URLSearchParams('x=48'))).toBe('');
    expect(targetFromSearch(new URLSearchParams('x=-1&y=5'))).toBe('');
    expect(targetFromSearch(new URLSearchParams('x=a&y=5'))).toBe('');
  });
});

describe('simulatorPath', () => {
  it('links to the simulator, with or without a report to take the numbers from', () => {
    expect(simulatorPath(7)).toBe('/v/7/rally/simulator');
    expect(simulatorPath(7, 31)).toBe('/v/7/rally/simulator?report=31');
  });
});
