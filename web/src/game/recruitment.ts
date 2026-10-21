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

/**
 * How many units the stock (rounded down) and the free population pay for when the price of each one
 * depends on how many are ordered together (the noble, 1.6): `priceFor(count)` is what each of
 * `count` units costs. Never negative, at most MAX_RECRUIT_COUNT.
 */
export function maxAffordableRising(
  stock: ResourceAmounts,
  priceFor: (count: number) => ResourceAmounts,
  freePop: number,
  pop: number,
): number {
  const max = Math.min(MAX_RECRUIT_COUNT, Math.floor(Math.max(0, freePop) / pop));
  let count = 0;
  while (count < max) {
    const price = priceFor(count + 1);
    if (resourceTypes.some((type) => price[type] * (count + 1) > Math.floor(stock[type]))) {
      break;
    }
    count++;
  }
  return count;
}

/**
 * A unit as the recruit screen prices it: what one costs and needs, and for a unit whose price
 * rises with every one the player holds (the noble, 1.6, 02-game-rules §5.7) what each of `count`
 * costs in one order. Without `priceFor` every unit costs `cost`.
 */
export interface PricedUnit {
  cost: ResourceAmounts;
  pop: number;
  priceFor?: (count: number) => ResourceAmounts;
}

/** What each unit of an order of `count` costs. */
export function unitPriceFor(unit: PricedUnit, count: number): ResourceAmounts {
  return unit.priceFor === undefined ? unit.cost : unit.priceFor(count);
}

/** What an order of `count` units costs. */
export function orderCost(unit: PricedUnit, count: number): ResourceAmounts {
  return timesCount(unitPriceFor(unit, count), count);
}

/** Like `maxAffordable`, with the rising price of a unit that has one. */
export function maxAffordableOf(stock: ResourceAmounts, unit: PricedUnit, freePop: number): number {
  return unit.priceFor === undefined
    ? maxAffordable(stock, unit.cost, freePop, unit.pop)
    : maxAffordableRising(stock, unit.priceFor, freePop, unit.pop);
}

/** Cost or population of `count` units: every value of the per-unit amounts times `count`. */
export function timesCount(cost: ResourceAmounts, count: number): ResourceAmounts {
  return { wood: cost.wood * count, clay: cost.clay * count, iron: cost.iron * count };
}

/** A count typed into one unit row of the recruit screen, with what each unit of that order costs. */
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
  unit: PricedUnit,
  others: readonly TypedRow[],
): number {
  const taken = totalOf(others);
  const left = {
    wood: stock.wood - taken.cost.wood,
    clay: stock.clay - taken.cost.clay,
    iron: stock.iron - taken.cost.iron,
  };
  return maxAffordableOf(left, unit, freePop - taken.pop);
}
