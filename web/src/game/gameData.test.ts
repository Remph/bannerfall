import { describe, expect, expectTypeOf, it } from 'vitest';
import type { components } from '../api/schema';
import {
  bannerTypes,
  buildingTypes,
  byBuildingOrder,
  byUnitOrder,
  isArcher,
  isStandard,
  recruitingBuildings,
  standardTypes,
  unitTypes,
  unitTypesFor,
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
    expect(bannerTypes).toHaveLength(16);
    expect(bannerTypes.filter(isStandard)).toEqual([...standardTypes]);
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

describe('unit order (1.6)', () => {
  it('shows the archers next to their building’s other units', () => {
    expect(unitTypes.slice(0, 7)).toEqual([
      'SPEARMAN',
      'SWORDSMAN',
      'AXEMAN',
      'ARCHER',
      'SCOUT',
      'LIGHT_CAVALRY',
      'MOUNTED_ARCHER',
    ]);
  });

  it('sorts the server order, archers last, into the UI order', () => {
    const server: UnitType[] = ['SCOUT', 'LIGHT_CAVALRY', 'HEAVY_CAVALRY', 'MOUNTED_ARCHER'];
    expect([...server].sort(byUnitOrder)).toEqual(['SCOUT', 'LIGHT_CAVALRY', 'MOUNTED_ARCHER', 'HEAVY_CAVALRY']);
  });

  it('offers the archers only in a world with them, or when some are already there', () => {
    expect(unitTypesFor({ archersEnabled: false }).filter(isArcher)).toEqual([]);
    expect(unitTypesFor(undefined).filter(isArcher)).toEqual([]);
    expect(unitTypesFor({ archersEnabled: true }).filter(isArcher)).toEqual(['ARCHER', 'MOUNTED_ARCHER']);
    expect(unitTypesFor({ archersEnabled: false }, { MOUNTED_ARCHER: 3 }).filter(isArcher)).toEqual(['MOUNTED_ARCHER']);
    expect(unitTypesFor({ archersEnabled: false })).toHaveLength(unitTypes.length - 2);
  });
});
