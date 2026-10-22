import { describe, expect, it } from 'vitest';
import {
  initialRallyPart,
  mapFocusFromSearch,
  mapPath,
  rallyPath,
  simulatorPath,
  targetFromSearch,
} from './rallyPaths';

describe('rallyPath', () => {
  it('links to the rally point, with or without a target', () => {
    expect(rallyPath(7)).toBe('/v/7/rally');
    expect(rallyPath(7, { x: 48, y: 52 })).toBe('/v/7/rally?x=48&y=52');
  });
});

describe('mapPath', () => {
  it('zooms the map in on the field and outlines it', () => {
    expect(mapPath(7, { x: 0, y: 99 })).toBe('/v/7/map?x=0&y=99&focus=1');
  });
});

describe('mapFocusFromSearch', () => {
  const MAP = 100;

  it('reads the field of a village link', () => {
    expect(mapFocusFromSearch(new URLSearchParams('x=48&y=52&focus=1'), MAP)).toEqual({ x: 48, y: 52 });
    expect(mapFocusFromSearch(new URLSearchParams(mapPath(7, { x: 0, y: 99 }).split('?')[1]), MAP)).toEqual({
      x: 0,
      y: 99,
    });
  });

  it('is nothing for the map’s own address updates, a field off the map or a broken one', () => {
    expect(mapFocusFromSearch(new URLSearchParams('x=48&y=52'), MAP)).toBeUndefined();
    expect(mapFocusFromSearch(new URLSearchParams('x=48&y=52&focus=0'), MAP)).toBeUndefined();
    expect(mapFocusFromSearch(new URLSearchParams('x=100&y=52&focus=1'), MAP)).toBeUndefined();
    expect(mapFocusFromSearch(new URLSearchParams('x=a&y=52&focus=1'), MAP)).toBeUndefined();
    expect(mapFocusFromSearch(new URLSearchParams('focus=1'), MAP)).toBeUndefined();
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

describe('initialRallyPart', () => {
  const attack = { kind: 'ATTACK' as const, units: null };
  const support = { kind: 'SUPPORT' as const, units: { SPEARMAN: 10 } };
  const ownWave = { kind: 'ATTACK' as const, units: { AXEMAN: 50 } };
  const none = new URLSearchParams();

  it('opens on the form without attacks coming in', () => {
    expect(initialRallyPart([], none, '')).toBe('send');
    expect(initialRallyPart([support, ownWave], none, '')).toBe('send');
  });

  it('opens on the movements while an attack is coming in, or for #incoming and #outgoing', () => {
    expect(initialRallyPart([support, attack], none, '')).toBe('moves');
    expect(initialRallyPart([], none, '#incoming')).toBe('moves');
    expect(initialRallyPart([], none, '#outgoing')).toBe('moves');
  });

  it('opens on the form when the address names a target, attacks or not', () => {
    expect(initialRallyPart([attack], new URLSearchParams('x=48&y=52'), '')).toBe('send');
  });
});
