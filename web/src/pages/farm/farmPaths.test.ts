import { describe, expect, it } from 'vitest';
import { farmPath, farmUnitsState, unitsFromState } from './farmPaths';

describe('farmPath', () => {
  it('opens the list of a village', () => {
    expect(farmPath(7)).toBe('/v/7/farm');
  });

  it('can fill in a target to add', () => {
    expect(farmPath(7, { x: 48, y: 52 })).toBe('/v/7/farm?x=48&y=52');
  });
});

describe('unitsFromState', () => {
  it('reads the units of farmUnitsState', () => {
    expect(unitsFromState(farmUnitsState({ AXEMAN: 50, LIGHT_CAVALRY: 10 }))).toEqual({
      AXEMAN: 50,
      LIGHT_CAVALRY: 10,
    });
  });

  it('keeps only template types and sensible counts', () => {
    expect(
      unitsFromState({ farmUnits: { NOBLE: 1, PALADIN: 1, AXEMAN: 0, SPEARMAN: -3, SWORDSMAN: 2.5, SCOUT: 99_999 } }),
    ).toEqual({ SCOUT: 30_000 });
  });

  it('is empty for any other state', () => {
    expect(unitsFromState(undefined)).toEqual({});
    expect(unitsFromState(null)).toEqual({});
    expect(unitsFromState({ from: '/reports' })).toEqual({});
    expect(unitsFromState({ farmUnits: 'AXEMAN' })).toEqual({});
  });
});
