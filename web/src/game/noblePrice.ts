import { maxAffordableRising } from './recruitment';
import { resourceTypes, type ResourceAmounts } from './resources';

/**
 * The rising noble price on the client (1.6, 02-game-rules §5.7), the same arithmetic as the
 * server's NobleRules: the server decides, this shows what an order of several nobles costs and
 * how many the stock pays for while the player types.
 */

/** What the arithmetic needs of GET /api/noble. The generated NobleDto has these fields, so it fits. */
export interface NoblePricing {
  villages: number;
  nobles: number;
  freeRanks: number;
  step: number;
  maxFactor: number;
  baseCost: ResourceAmounts;
}

/** The rank of a player who holds `held` villages and nobles together: `max(0, held − freeRanks)`. */
export function nobleRank(pricing: NoblePricing, held: number): number {
  return Math.max(0, held - pricing.freeRanks);
}

/** `min(maxFactor, 1 + step × rank)`; 1 for a step of 0. */
export function nobleFactor(pricing: NoblePricing, rank: number): number {
  if (pricing.step <= 0 || rank <= 0) {
    return 1;
  }
  return Math.min(pricing.maxFactor, 1 + pricing.step * rank);
}

/** The price of one noble at `rank`: every resource of the base price times the factor, rounded. */
export function noblePrice(pricing: NoblePricing, rank: number): ResourceAmounts {
  const factor = nobleFactor(pricing, rank);
  return {
    wood: Math.round(pricing.baseCost.wood * factor),
    clay: Math.round(pricing.baseCost.clay * factor),
    iron: Math.round(pricing.baseCost.iron * factor),
  };
}

/**
 * What each noble of an order of `count` costs (count ≥ 1): the average of their prices, noble `i`
 * at the rank of `villages + nobles + i`, every resource rounded up. The order costs `count` times
 * that. Below 1 it is the next noble's price.
 */
export function nobleUnitPrice(pricing: NoblePricing, count: number): ResourceAmounts {
  const held = pricing.villages + pricing.nobles;
  const n = Math.max(1, count);
  const sum = { wood: 0, clay: 0, iron: 0 };
  for (let i = 0; i < n; i++) {
    const one = noblePrice(pricing, nobleRank(pricing, held + i));
    for (const type of resourceTypes) {
      sum[type] += one[type];
    }
  }
  return { wood: Math.ceil(sum.wood / n), clay: Math.ceil(sum.clay / n), iron: Math.ceil(sum.iron / n) };
}

/**
 * How many nobles the stock (rounded down) and the free population pay for, with the price rising
 * from one to the next; never negative, at most MAX_RECRUIT_COUNT.
 */
export function nobleMaxAffordable(
  pricing: NoblePricing,
  stock: ResourceAmounts,
  freePop: number,
  pop: number,
): number {
  return maxAffordableRising(stock, (count) => nobleUnitPrice(pricing, count), freePop, pop);
}
