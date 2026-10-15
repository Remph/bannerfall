import { describe, expect, it } from 'vitest';
import { buildingTypes, byBuildingOrder, recruitingBuildings, type BuildingType } from './gameData';

describe('building order', () => {
  it('shows the Banner Hall between the Workshop and the Academy', () => {
    expect(buildingTypes.slice(3, 6)).toEqual(['WORKSHOP', 'BANNER_HALL', 'ACADEMY']);
    expect(recruitingBuildings).toEqual(['BARRACKS', 'STABLE', 'WORKSHOP', 'BANNER_HALL', 'ACADEMY']);
  });

  it('sorts a list in the server order into the UI order', () => {
    const server: BuildingType[] = ['HEADQUARTERS', 'WORKSHOP', 'ACADEMY', 'WALL', 'BANNER_HALL'];
    expect([...server].sort(byBuildingOrder)).toEqual(['HEADQUARTERS', 'WORKSHOP', 'BANNER_HALL', 'ACADEMY', 'WALL']);
  });

  it('lists every building once', () => {
    expect(new Set(buildingTypes).size).toBe(16);
  });
});
