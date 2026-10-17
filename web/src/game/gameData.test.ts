import { describe, expect, expectTypeOf, it } from 'vitest';
import type { components } from '../api/schema';
import {
  bannerTypes,
  buildingTypes,
  byBuildingOrder,
  recruitingBuildings,
  unitTypes,
  type BannerType,
  type BuildingType,
  type UnitType,
} from './gameData';

type Schemas = components['schemas'];

// The lists are kept by hand for their UI order; these checks fail `tsc` (pnpm build) as soon as the
// server adds, renames or drops a building, unit or banner type and `pnpm gen:api` has run.
describe('the lists match the server enums', () => {
  it('buildings', () => {
    expectTypeOf<BuildingType>().toEqualTypeOf<Schemas['BuildOrderRequest']['building']>();
    expect(new Set(buildingTypes).size).toBe(buildingTypes.length);
  });

  it('units', () => {
    expectTypeOf<UnitType>().toEqualTypeOf<Schemas['RecruitOrderRequest']['unit']>();
    expect(new Set(unitTypes).size).toBe(unitTypes.length);
  });

  it('banners', () => {
    expectTypeOf<BannerType>().toEqualTypeOf<Schemas['BannerDto']['type']>();
    expect(new Set(bannerTypes).size).toBe(bannerTypes.length);
  });
});

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
