import type { BuildingType } from '../../../game/gameData';

/**
 * The highest level of every building (02-game-rules §4.1, the server's buildings.yml; a test keeps
 * them in step). The village picture needs them before any building list is loaded: a building's
 * drawing grows with its share of this maximum, so a maxed one always looks finished.
 */
export const maxLevels: Record<BuildingType, number> = {
  HEADQUARTERS: 30,
  BARRACKS: 25,
  STABLE: 20,
  WORKSHOP: 15,
  BANNER_HALL: 3,
  ACADEMY: 1,
  SMITHY: 20,
  RALLY_POINT: 1,
  MARKET: 25,
  WOODCUTTER: 30,
  CLAY_PIT: 30,
  IRON_MINE: 30,
  FARM: 30,
  WAREHOUSE: 30,
  HIDING_PLACE: 10,
  WALL: 20,
};

export interface Growth {
  /** Which of the three drawings: 0 up to a third of the maximum, 1 up to two thirds, 2 above. */
  look: 0 | 1 | 2;
  /** 0–5, one more every fifth of the maximum: the small extras between the looks (a flag, a fence …). */
  step: number;
}

/** The share of the maximum up to which each look is drawn; above the last one, the finished look. */
const LOOK_UNTIL = [1 / 3, 2 / 3] as const;
const STEPS = 5;
/** Level 10 of 30 is exactly a third, not 0.333… above it. */
const EPSILON = 1e-9;

/** How grown a building at this level is drawn (only for level 1 and up; level 0 is a plot). */
export function growthOf(type: BuildingType, level: number): Growth {
  const share = Math.min(1, Math.max(0, level / maxLevels[type]));
  const index = LOOK_UNTIL.findIndex((until) => share <= until + EPSILON);
  const look: Growth['look'] = index === -1 ? 2 : index === 0 ? 0 : 1;
  return { look, step: Math.floor(share * STEPS + EPSILON) };
}
