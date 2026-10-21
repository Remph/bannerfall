import type { TemplateLetter } from './farmTemplates';
import { buildingTypes, type BuildingType } from './gameData';

/**
 * Build plans of the village automation (02-game-rules §25, 1.6): up to five per player (A to E),
 * each an ordered list of "building to level" lines with at most one line that starts recruiting a
 * recruit template. The server checks the same rules again (BuildPlanService.checked).
 */

/** The longest plan name (the server's BuildPlanService.MAX_NAME_LENGTH). */
export const MAX_PLAN_NAME = 16;

/** The most lines in a plan (the server's BuildPlanService.MAX_LINES). */
export const MAX_PLAN_LINES = 80;

export type PlanLine =
  | { readonly kind: 'build'; readonly building: BuildingType; readonly level: number }
  | { readonly kind: 'recruit'; readonly template: TemplateLetter };

export interface BuildPlan {
  readonly letter: TemplateLetter;
  readonly name: string;
  readonly lines: readonly PlanLine[];
}

/** What the editor needs of a building (GET /api/build-plans/buildings). */
export interface PlanBuilding {
  readonly building: BuildingType;
  readonly startLevel: number;
  readonly maxLevel: number;
  readonly requires: readonly { readonly building: BuildingType; readonly level: number }[];
  /** False for the Banner Hall in a world without it: its lines are skipped. */
  readonly available: boolean;
}

/** A line as the API sends it: a building and a level, or a recruit template (level 0). */
export interface ApiPlanLine {
  readonly building: BuildingType | null;
  readonly level: number;
  readonly recruit: TemplateLetter | null;
}

export type Levels = Readonly<Partial<Record<BuildingType, number>>>;

export function fromApiLines(lines: readonly ApiPlanLine[]): PlanLine[] {
  return lines.flatMap((line): PlanLine[] => {
    if (line.recruit !== null) {
      return [{ kind: 'recruit', template: line.recruit }];
    }
    return line.building === null ? [] : [{ kind: 'build', building: line.building, level: line.level }];
  });
}

export function toApiLines(lines: readonly PlanLine[]): ApiPlanLine[] {
  return lines.map((line) =>
    line.kind === 'recruit'
      ? { building: null, level: 0, recruit: line.template }
      : { building: line.building, level: line.level, recruit: null },
  );
}

function infoOf(buildings: readonly PlanBuilding[], type: BuildingType): PlanBuilding | undefined {
  return buildings.find((info) => info.building === type);
}

/** The levels of a new village. */
function startLevels(buildings: readonly PlanBuilding[]): Levels {
  return Object.fromEntries(buildings.map((info) => [info.building, info.startLevel]));
}

function withLine(levels: Levels, line: PlanLine): Levels {
  if (line.kind === 'recruit') {
    return levels;
  }
  return { ...levels, [line.building]: Math.max(levels[line.building] ?? 0, line.level) };
}

/** The levels the lines above `index` reach, from a new village on. */
export function reachedAbove(lines: readonly PlanLine[], index: number, buildings: readonly PlanBuilding[]): Levels {
  return lines.slice(0, index).reduce(withLine, startLevels(buildings));
}

export interface Requirement {
  readonly building: BuildingType;
  readonly level: number;
}

/**
 * Per line what it lacks: the requirements the lines above it don't reach (empty when it is
 * fine). The server refuses a plan with a line that lacks something.
 */
export function missingPerLine(lines: readonly PlanLine[], buildings: readonly PlanBuilding[]): Requirement[][] {
  return lines.map((line, index) => {
    if (line.kind === 'recruit') {
      return [];
    }
    const reached = reachedAbove(lines, index, buildings);
    const requires = infoOf(buildings, line.building)?.requires ?? [];
    return requires.filter((need) => (reached[need.building] ?? 0) < need.level);
  });
}

/** Whether the server takes the plan: a name, 1 … 80 lines, nothing missing, one recruit line at most. */
export function isSavable(name: string, lines: readonly PlanLine[], buildings: readonly PlanBuilding[]): boolean {
  const recruitLines = lines.filter((line) => line.kind === 'recruit').length;
  return (
    name.trim().length > 0 &&
    lines.length > 0 &&
    lines.length <= MAX_PLAN_LINES &&
    recruitLines <= 1 &&
    missingPerLine(lines, buildings).every((missing) => missing.length === 0)
  );
}

/**
 * The lines with `building` at `level` appended; a requirement the lines don't reach yet comes in
 * front of it as a line of its own (their requirements first, and so on).
 */
export function appendBuilding(
  lines: readonly PlanLine[],
  building: BuildingType,
  level: number,
  buildings: readonly PlanBuilding[],
): PlanLine[] {
  const reached = reachedAbove(lines, lines.length, buildings);
  const requires = infoOf(buildings, building)?.requires ?? [];
  const withRequirements = requires
    .filter((need) => (reached[need.building] ?? 0) < need.level)
    .reduce<PlanLine[]>(
      (acc, need) => {
        const now = reachedAbove(acc, acc.length, buildings);
        return (now[need.building] ?? 0) < need.level ? appendBuilding(acc, need.building, need.level, buildings) : acc;
      },
      [...lines],
    );
  return [...withRequirements, { kind: 'build', building, level }];
}

/**
 * "+ Building": the building's next level after the lines (with its missing requirements in front);
 * the lines unchanged when they already reach its top level.
 */
export function appendNextLevel(
  lines: readonly PlanLine[],
  building: BuildingType,
  buildings: readonly PlanBuilding[],
): PlanLine[] {
  const info = infoOf(buildings, building);
  const reached = reachedAbove(lines, lines.length, buildings)[building] ?? 0;
  if (info === undefined || reached >= info.maxLevel) {
    return [...lines];
  }
  return appendBuilding(lines, building, reached + 1, buildings);
}

/** "+ Start recruiting": the recruit line at the end, or the one there is pointed at `template`. */
export function setRecruitLine(lines: readonly PlanLine[], template: TemplateLetter): PlanLine[] {
  if (lines.some((line) => line.kind === 'recruit')) {
    return lines.map((line): PlanLine => (line.kind === 'recruit' ? { kind: 'recruit', template } : line));
  }
  return [...lines, { kind: 'recruit', template }];
}

/** The line's level changed by `delta`, kept within 1 … the building's top level. */
export function changeLevel(
  lines: readonly PlanLine[],
  index: number,
  delta: number,
  buildings: readonly PlanBuilding[],
): PlanLine[] {
  return lines.map((line, i): PlanLine => {
    if (i !== index || line.kind === 'recruit') {
      return line;
    }
    const max = infoOf(buildings, line.building)?.maxLevel ?? line.level;
    return { ...line, level: Math.min(max, Math.max(1, line.level + delta)) };
  });
}

/** The line moved one place up (-1) or down (+1); unchanged at the ends. */
export function moveLine(lines: readonly PlanLine[], index: number, delta: -1 | 1): PlanLine[] {
  const target = index + delta;
  const moving = lines[index];
  const other = lines[target];
  if (moving === undefined || other === undefined) {
    return [...lines];
  }
  return lines.map((line, i) => {
    if (i === index) {
      return other;
    }
    return i === target ? moving : line;
  });
}

export function removeLine(lines: readonly PlanLine[], index: number): PlanLine[] {
  return lines.filter((_, i) => i !== index);
}

/**
 * A plan that builds a village up to `levels` ("take this village's levels"): one line per
 * building above its start level, in building order with requirements in front, at most 80 lines.
 */
export function linesFromLevels(levels: Levels, buildings: readonly PlanBuilding[]): PlanLine[] {
  const lines = buildingTypes.reduce<PlanLine[]>((acc, type) => {
    const info = infoOf(buildings, type);
    const level = Math.min(levels[type] ?? 0, info?.maxLevel ?? 0);
    if (info === undefined || !info.available || level <= info.startLevel) {
      return acc;
    }
    const reached = reachedAbove(acc, acc.length, buildings)[type] ?? 0;
    return reached >= level ? acc : appendBuilding(acc, type, level, buildings);
  }, []);
  return lines.slice(0, MAX_PLAN_LINES);
}

/** How a line of the plan stands in a village: reached, the current one, or still to come. */
export type LineState = 'done' | 'current' | 'next';

/** `current` is the status's line index (lines.length once every line is reached). */
export function lineState(index: number, current: number): LineState {
  if (index < current) {
    return 'done';
  }
  return index === current ? 'current' : 'next';
}
