import { describe, expect, it } from 'vitest';
import buildingsYml from '../../../../../server/src/main/resources/gamedata/buildings.yml?raw';
import { buildingTypes } from '../../../game/gameData';
import { growthOf, maxLevels } from './growth';

describe('maxLevels', () => {
  it('matches the max-level of every building in the server game data', () => {
    // "    HEADQUARTERS:\n      max-level: 30" → { HEADQUARTERS: 30, … }
    const fromServer = Object.fromEntries(
      [...buildingsYml.matchAll(/^ {4}([A-Z_]+):\n {6}max-level: (\d+)/gm)].map((match) => [
        match[1],
        Number(match[2]),
      ]),
    );
    expect(Object.keys(fromServer).sort()).toEqual([...buildingTypes].sort());
    expect(maxLevels).toEqual(fromServer);
  });
});

describe('growthOf', () => {
  it('switches the drawing at a third and at two thirds of the maximum', () => {
    expect(growthOf('HEADQUARTERS', 1).look).toBe(0);
    expect(growthOf('HEADQUARTERS', 10).look).toBe(0);
    expect(growthOf('HEADQUARTERS', 11).look).toBe(1);
    expect(growthOf('HEADQUARTERS', 20).look).toBe(1);
    expect(growthOf('HEADQUARTERS', 21).look).toBe(2);
    expect(growthOf('HEADQUARTERS', 30).look).toBe(2);
  });

  it('adds a step every fifth of the maximum', () => {
    expect(growthOf('WAREHOUSE', 5).step).toBe(0);
    expect(growthOf('WAREHOUSE', 6).step).toBe(1);
    expect(growthOf('WAREHOUSE', 18).step).toBe(3);
    expect(growthOf('WAREHOUSE', 30).step).toBe(5);
    expect(growthOf('HIDING_PLACE', 2).step).toBe(1);
  });

  it('draws one-level buildings finished and gives the Banner Hall one look per level', () => {
    expect(growthOf('ACADEMY', 1)).toEqual({ look: 2, step: 5 });
    expect(growthOf('RALLY_POINT', 1)).toEqual({ look: 2, step: 5 });
    expect(growthOf('BANNER_HALL', 1).look).toBe(0);
    expect(growthOf('BANNER_HALL', 2).look).toBe(1);
    expect(growthOf('BANNER_HALL', 3).look).toBe(2);
  });

  it('never goes past the finished drawing', () => {
    expect(growthOf('WALL', 25)).toEqual({ look: 2, step: 5 });
  });
});
