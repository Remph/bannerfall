/**
 * The orders UI lists follow: 02-game-rules §4.1 and §5.1, the same as the server enums BuildingType
 * and UnitType, except that the Banner Hall shows between the Workshop and the Academy (1.2.2; the
 * server keeps it last, so nothing stored or computed there changes). Lists the server sends in its
 * own order are sorted with `byBuildingOrder`. (The API sends buildings and units as maps keyed by
 * these names.)
 */
export const buildingTypes = [
  'HEADQUARTERS',
  'BARRACKS',
  'STABLE',
  'WORKSHOP',
  'BANNER_HALL',
  'ACADEMY',
  'SMITHY',
  'RALLY_POINT',
  'MARKET',
  'WOODCUTTER',
  'CLAY_PIT',
  'IRON_MINE',
  'FARM',
  'WAREHOUSE',
  'HIDING_PLACE',
  'WALL',
] as const;

/** 'HEADQUARTERS' | 'BARRACKS' | … – derived from the array, so both stay in sync. */
export type BuildingType = (typeof buildingTypes)[number];

/** Compares two buildings by the UI order above, for `sort`. */
export function byBuildingOrder(a: BuildingType, b: BuildingType): number {
  return buildingTypes.indexOf(a) - buildingTypes.indexOf(b);
}

export const unitTypes = [
  'SPEARMAN',
  'SWORDSMAN',
  'AXEMAN',
  'SCOUT',
  'LIGHT_CAVALRY',
  'HEAVY_CAVALRY',
  'RAM',
  'CATAPULT',
  'NOBLE',
  'PALADIN',
  'BANNER_THIEF',
] as const;

export type UnitType = (typeof unitTypes)[number];

/** The buildings that train units, in the UI's building order (server: GameData.RECRUITING_BUILDINGS). */
export const recruitingBuildings = [
  'BARRACKS',
  'STABLE',
  'WORKSHOP',
  'BANNER_HALL',
  'ACADEMY',
] as const satisfies readonly BuildingType[];

export type RecruitingBuilding = (typeof recruitingBuildings)[number];

/** The nine banner types in the order of 02-game-rules §21.1 (server: BannerType). */
export const bannerTypes = [
  'HARVEST',
  'BUILDERS',
  'DRILL',
  'WAR',
  'BULWARK',
  'HASTE',
  'TREASURY',
  'PLUNDER',
  'OATH',
] as const;

export type BannerType = (typeof bannerTypes)[number];
