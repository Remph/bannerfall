import type { TemplateLetter } from './farmTemplates';
import type { BuildingType, UnitType } from './gameData';
import { nobleUnitPrice, type NoblePricing } from './noblePrice';
import { MAX_RECRUIT_COUNT, totalOf } from './recruitment';
import { resourceTypes, type ResourceAmounts } from './resources';
import type { UnitMap } from './troops';

/**
 * A recruit template (02-game-rules §5.2b, 1.4): a name and the army a village should have, one of
 * the player's five (A to E, the same letters as the farm templates but a set of their own).
 */
export interface RecruitTemplate {
  readonly letter: TemplateLetter;
  readonly name: string;
  /** The goal per unit type. */
  readonly units: UnitMap;
}

/** What the plan needs of a unit on the recruit screen (the RecruitUnit DTO fits). */
export interface PlanUnit {
  readonly unit: UnitType;
  readonly cost: ResourceAmounts;
  readonly pop: number;
  readonly secondsPerUnit: number;
  readonly available: boolean;
}

/** What the plan needs of a recruiting building (the RecruitBuilding DTO fits). */
export interface PlanBuilding {
  readonly building: BuildingType;
  readonly level: number;
  readonly units: readonly PlanUnit[];
  readonly queue: readonly { readonly unit: UnitType; readonly count: number; readonly completedCount: number }[];
}

/** Why a unit of the template recruits nothing now. */
export type PlanSkip = 'reached' | 'locked' | 'queueFull' | 'slots';

export interface PlanLine {
  readonly unit: PlanUnit;
  readonly building: BuildingType;
  readonly goal: number;
  /** The village's own: at home, away and still in its recruit orders. */
  readonly have: number;
  /** What the goal still lacks (for the paladin at most the free slots). */
  readonly missing: number;
  /** What a tap on "Recruit" queues now. */
  readonly now: number;
  readonly skip: PlanSkip | null;
}

export interface PlanInput {
  readonly goal: UnitMap;
  /** In UI order; a unit the server doesn't list (e.g. thieves with banners off) is left out. */
  readonly buildings: readonly PlanBuilding[];
  readonly home: UnitMap;
  readonly away: UnitMap;
  readonly stock: ResourceAmounts;
  readonly freePop: number;
  /** Orders per building (world setting). */
  readonly queueSize: number;
  /** Free paladin slots; undefined where the paladin isn't listed. */
  readonly freeSlots: number | undefined;
  /** The noble's rising price (1.6, 02-game-rules §5.7); undefined where he isn't listed. */
  readonly noble?: NoblePricing;
}

/**
 * What each unit of a line of `count` costs: the noble's price rises with every one (1.6), the
 * others cost what the server lists.
 */
export function linePrice(input: Pick<PlanInput, 'noble'>, unit: PlanUnit, count: number): ResourceAmounts {
  return unit.unit === 'NOBLE' && input.noble !== undefined ? nobleUnitPrice(input.noble, count) : unit.cost;
}

type Draft = Omit<PlanLine, 'now'>;

/** One building's lines before scaling: each line that recruits takes a place in its queue. */
function draftLines(input: PlanInput, building: PlanBuilding): readonly Draft[] {
  const wanted = building.units.filter((unit) => (input.goal[unit.unit] ?? 0) > 0);
  return wanted.reduce<{ lines: readonly Draft[]; places: number }>(
    (acc, unit) => {
      const goal = input.goal[unit.unit] ?? 0;
      const training = building.queue
        .filter((order) => order.unit === unit.unit)
        .reduce((sum, order) => sum + order.count - order.completedCount, 0);
      const have = (input.home[unit.unit] ?? 0) + (input.away[unit.unit] ?? 0) + training;
      const open = Math.max(0, goal - have);
      const missing = unit.unit === 'PALADIN' && input.freeSlots !== undefined ? Math.min(open, input.freeSlots) : open;
      // the first reason that holds, in this order
      const reasons: readonly (readonly [boolean, PlanSkip])[] = [
        [open === 0, 'reached'],
        [!unit.available || building.level === 0, 'locked'],
        [acc.places <= 0, 'queueFull'],
        [missing === 0, 'slots'],
      ];
      const skip = reasons.find(([holds]) => holds)?.[1] ?? null;
      const line: Draft = { unit, building: building.building, goal, have, missing, skip };
      return { lines: [...acc.lines, line], places: skip === null ? acc.places - 1 : acc.places };
    },
    { lines: [], places: input.queueSize - building.queue.length },
  ).lines;
}

/**
 * What a template recruits in the village right now (02-game-rules §5.2b): per unit what the goal
 * still lacks, all of it cut by one share until the stock (rounded down) and the free population
 * pay for it, so a short village recruits the template's mix instead of its first units in full.
 * Units that recruit nothing say why.
 */
export function planTemplate(input: PlanInput): PlanLine[] {
  const drafts = input.buildings.flatMap((building) => draftLines(input, building));
  const need = totalOf(
    drafts
      .filter((line) => line.skip === null)
      .map((line) => ({ cost: linePrice(input, line.unit, line.missing), pop: line.unit.pop, count: line.missing })),
  );
  // Fewer nobles than the line asks for cost each at most what each of the whole line would, so the
  // share below never plans more than the stock pays for.
  const shares = [
    ...resourceTypes
      .filter((type) => need.cost[type] > 0)
      .map((type) => Math.floor(input.stock[type]) / need.cost[type]),
    ...(need.pop > 0 ? [Math.max(0, input.freePop) / need.pop] : []),
  ];
  const share = Math.max(0, Math.min(1, ...shares));
  return drafts.map((line) => ({
    ...line,
    now: line.skip === null ? Math.min(MAX_RECRUIT_COUNT, Math.floor(line.missing * share)) : 0,
  }));
}

/** The lines that recruit something, as the orders of "recruit all" (in unit order). */
export function ordersOf(lines: readonly PlanLine[]): { unit: UnitType; count: number }[] {
  return lines.filter((line) => line.now > 0).map((line) => ({ unit: line.unit.unit, count: line.now }));
}

/**
 * How long the plan's orders train: the buildings train side by side, so the longest building's
 * sum counts (the orders already in its queue come on top).
 */
export function planSeconds(lines: readonly PlanLine[]): number {
  const perBuilding = lines.reduce<Partial<Record<BuildingType, number>>>(
    (acc, line) => ({ ...acc, [line.building]: (acc[line.building] ?? 0) + line.now * line.unit.secondsPerUnit }),
    {},
  );
  return Math.max(0, ...Object.values(perBuilding));
}
