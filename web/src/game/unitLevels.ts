import { unitTypes, type UnitType } from './gameData';

/**
 * Unit levels (M30, 02-game-rules §5.4) – pure helpers. A level map lists the units above level 1
 * only: a missing unit is level 1 (the server's maps work the same way).
 */
export type UnitLevelMap = Partial<Record<UnitType, number>>;

/** The unit types of a server map keyed by plain strings, with their levels above 1. */
export function toLevelMap(levels: Record<string, number> | null | undefined): UnitLevelMap {
  const map: UnitLevelMap = {};
  for (const type of unitTypes) {
    const level = levels?.[type] ?? 1;
    if (level > 1) {
      map[type] = level;
    }
  }
  return map;
}

/** The level of a unit in a map (1 when it isn't listed). */
export function levelOf(levels: UnitLevelMap, type: UnitType): number {
  return levels[type] ?? 1;
}

/** The levels above 1 of the units the smithy page lists: what the player has researched. */
export function ownLevels(units: readonly { unit: UnitType; level: number }[]): UnitLevelMap {
  const map: UnitLevelMap = {};
  for (const { unit, level } of units) {
    if (level > 1) {
      map[unit] = level;
    }
  }
  return map;
}

/** The request field of the simulator: only levels above 1, null when there are none. */
export function levelsForRequest(levels: UnitLevelMap): Record<string, number> | null {
  const entries = unitTypes.flatMap((type): [UnitType, number][] => {
    const level = levelOf(levels, type);
    return level > 1 ? [[type, level]] : [];
  });
  return entries.length === 0 ? null : Object.fromEntries(entries);
}

/**
 * The level a defending stack's units had, if every stack that has the unit agrees on it. A battle
 * report's defender table adds all stacks together, so a unit whose stacks differ shows no level
 * there (the list of stacks below it does, per stack).
 */
export function sharedLevels(
  stacks: readonly { units: Record<string, number>; levels: Record<string, number> }[],
): UnitLevelMap {
  const map: UnitLevelMap = {};
  for (const type of unitTypes) {
    const present = stacks.filter((stack) => (stack.units[type] ?? 0) > 0);
    if (present.length === 0) {
      continue;
    }
    const first = present[0]?.levels[type] ?? 1;
    if (first > 1 && present.every((stack) => (stack.levels[type] ?? 1) === first)) {
      map[type] = first;
    }
  }
  return map;
}

/** The highest level of each unit over several maps: one level per unit for the simulator's defender. */
export function highestLevels(maps: readonly UnitLevelMap[]): UnitLevelMap {
  const map: UnitLevelMap = {};
  for (const type of unitTypes) {
    const level = Math.max(1, ...maps.map((levels) => levelOf(levels, type)));
    if (level > 1) {
      map[type] = level;
    }
  }
  return map;
}

/** What keeps the next research from starting, in the order the page says it. */
export type ResearchBlock = 'requirements' | 'busy' | 'resources';

/** Why a research can't be started now; null when it can (the server checks it all again). */
export function researchBlock(next: {
  missing: readonly unknown[];
  villageBusy: boolean;
  affordable: boolean;
}): ResearchBlock | null {
  if (next.missing.length > 0) {
    return 'requirements';
  }
  if (next.villageBusy) {
    return 'busy';
  }
  return next.affordable ? null : 'resources';
}

/** The stats of a level (1 = the first entry); a level past the end counts as the top one. */
export function statsAt<T>(stats: readonly T[], level: number): T {
  const entry = stats[Math.min(Math.max(level, 1), stats.length) - 1];
  if (entry === undefined) {
    throw new Error('A unit has at least one stats entry');
  }
  return entry;
}

/** The levels a select offers for a unit: 1 up to its top level. */
export function levelChoices(maxLevel: number): number[] {
  return Array.from({ length: Math.max(1, maxLevel) }, (_, index) => index + 1);
}
