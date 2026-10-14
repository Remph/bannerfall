import { resourceTypes, type ResourceAmounts } from './resources';

/**
 * Helpers for recruitment (02-game-rules §5.2). Units of an order are done one by one; the server
 * moves them home whenever it reads the village (no events), so the client computes the same
 * numbers to show progress and to know when to ask again.
 */

/** Most units in one order: an API limit (server: RecruitRules.MAX_COUNT). */
export const MAX_RECRUIT_COUNT = 30_000;

/** What the helpers need of an order. The generated RecruitOrderDto has these fields, so it fits. */
export interface RecruitOrderTiming {
  count: number;
  /** Units done and at home when the server answered. */
  completedCount: number;
  secondsPerUnit: number;
  startAt: string;
}

/**
 * Units of the order done at `nowMs`, like the server's `finishedBy`:
 * `clamp(floor((now − startAt) / secondsPerUnit), 0, count)` with whole elapsed seconds.
 */
export function unitsDone(order: RecruitOrderTiming, nowMs: number): number {
  const elapsedSeconds = Math.floor((nowMs - Date.parse(order.startAt)) / 1000);
  if (elapsedSeconds <= 0) {
    return 0;
  }
  return Math.min(order.count, Math.floor(elapsedSeconds / order.secondsPerUnit));
}

/** When unit number `done + 1` of the order is done (ms); undefined when all units are done. */
export function nextUnitAtMs(order: RecruitOrderTiming, done: number): number | undefined {
  if (done >= order.count) {
    return undefined;
  }
  return Date.parse(order.startAt) + (done + 1) * order.secondsPerUnit * 1000;
}

/**
 * The earliest moment one of these orders has a new unit that the last answer didn't contain yet
 * (`startAt + (completedCount + 1) × secondsPerUnit`): then the village and the recruitment are
 * read again, so the unit shows up at home. Undefined without orders.
 */
export function nextUnitDueMs(orders: readonly RecruitOrderTiming[]): number | undefined {
  let earliest: number | undefined;
  for (const order of orders) {
    const due = nextUnitAtMs(order, order.completedCount);
    if (due !== undefined && (earliest === undefined || due < earliest)) {
      earliest = due;
    }
  }
  return earliest;
}

/**
 * How many units the stock and the free population pay for right now, like the server's
 * `maxAffordable`: the smallest of `floor(stock / cost)` per resource and `floor(freePop / pop)`,
 * never negative, at most MAX_RECRUIT_COUNT. Computed here from the ticking resources, so it grows
 * while the page is open. Costs and pop are positive (the server's game data checks that).
 */
export function maxAffordable(stock: ResourceAmounts, cost: ResourceAmounts, freePop: number, pop: number): number {
  let max = Math.min(MAX_RECRUIT_COUNT, Math.floor(freePop / pop));
  for (const type of resourceTypes) {
    max = Math.min(max, Math.floor(Math.floor(stock[type]) / cost[type]));
  }
  return Math.max(0, max);
}

/** Cost or population of `count` units: every value of the per-unit amounts times `count`. */
export function timesCount(cost: ResourceAmounts, count: number): ResourceAmounts {
  return { wood: cost.wood * count, clay: cost.clay * count, iron: cost.iron * count };
}

/** A count typed into one unit row of the recruit screen, with what one unit of it costs. */
export interface TypedRow {
  cost: ResourceAmounts;
  pop: number;
  count: number;
}

/** Cost and population of all typed rows together ("recruit all", M27). */
export function totalOf(rows: readonly TypedRow[]): { cost: ResourceAmounts; pop: number } {
  const cost = { wood: 0, clay: 0, iron: 0 };
  let pop = 0;
  for (const row of rows) {
    for (const type of resourceTypes) {
      cost[type] += row.cost[type] * row.count;
    }
    pop += row.pop * row.count;
  }
  return { cost, pop };
}

/**
 * The most units of one row while the `others` keep their typed counts (M27): what is left of the
 * stock and of the free population after the other rows is shared, so typing 4 spearmen lowers the
 * maximum of the swordsmen. Whole numbers are subtracted from the stock, so rounding the stock down
 * (like the server does) gives the same result as before. 0 when the others already take too much.
 */
export function sharedMax(
  stock: ResourceAmounts,
  freePop: number,
  unit: { cost: ResourceAmounts; pop: number },
  others: readonly TypedRow[],
): number {
  const taken = totalOf(others);
  const left = {
    wood: stock.wood - taken.cost.wood,
    clay: stock.clay - taken.cost.clay,
    iron: stock.iron - taken.cost.iron,
  };
  return maxAffordable(left, unit.cost, freePop - taken.pop, unit.pop);
}
