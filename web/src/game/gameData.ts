/**
 * The canonical orders of 02-game-rules §4.1 and §5.1 – the same as the server enums BuildingType and
 * UnitType. UI lists follow them. (The API sends buildings and units as maps keyed by these names.)
 */
export const buildingTypes = [
  'HEADQUARTERS',
  'BARRACKS',
  'STABLE',
  'WORKSHOP',
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
  'BANNER_HALL',
] as const;

/** 'HEADQUARTERS' | 'BARRACKS' | … – derived from the array, so both stay in sync. */
export type BuildingType = (typeof buildingTypes)[number];

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

/** The buildings that train units, in building order (server: GameData.RECRUITING_BUILDINGS). */
export const recruitingBuildings = [
  'BARRACKS',
  'STABLE',
  'WORKSHOP',
  'ACADEMY',
  'BANNER_HALL',
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
