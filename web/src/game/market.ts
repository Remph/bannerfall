import type { ResourceAmounts, ResourceType } from './resources';

/*
 * Market rules the screen shows before it asks the server (02-game-rules §12). The server checks
 * everything again; these functions only keep the buttons and hints honest.
 */

/** Resources one merchant carries (sum of all three). */
export const MERCHANT_CAPACITY = 1000;
/** Merchant speed. */
export const MINUTES_PER_FIELD = 6;
/** Largest amount of one resource in a request / of an offer (server: MarketService). */
export const MAX_AMOUNT = 1_000_000;
/** Most units of one offer. */
export const MAX_OFFER_COUNT = 1000;

/** The resource names of offers in the API (`offerResource`), in display order. */
export const offerResources = ['WOOD', 'CLAY', 'IRON'] as const;
export type OfferResource = (typeof offerResources)[number];

/** WOOD → 'wood': the key of amounts and translations. */
export function resourceKey(resource: OfferResource): ResourceType {
  switch (resource) {
    case 'WOOD':
      return 'wood';
    case 'CLAY':
      return 'clay';
    case 'IRON':
      return 'iron';
  }
}

export function totalOf(amounts: ResourceAmounts): number {
  return amounts.wood + amounts.clay + amounts.iron;
}

/** Merchants needed to carry `total` resources: ceil(total / 1000). */
export function merchantsNeeded(total: number): number {
  return Math.ceil(total / MERCHANT_CAPACITY);
}

/** Merchants of `count` units of an offer: every unit travels with its own merchants. */
export function merchantsForOffer(offerAmount: number, count: number): number {
  return merchantsNeeded(offerAmount) * count;
}

/** One-way travel time of merchants over `distance` fields, as the server computes it. */
export function travelSeconds(distance: number, world: { speed: number; unitSpeed: number }): number {
  return Math.max(1, Math.round((distance * MINUTES_PER_FIELD * 60) / (world.speed * world.unitSpeed)));
}

/** An offer's ratio is allowed if 1/maxRatio ≤ want/offer ≤ maxRatio (both amounts > 0). */
export function ratioAllowed(offerAmount: number, wantAmount: number, maxRatio: number): boolean {
  return (
    offerAmount > 0 && wantAmount > 0 && wantAmount <= maxRatio * offerAmount && offerAmount <= maxRatio * wantAmount
  );
}

/** The amounts one can ask for an offered `offerAmount`: [ceil(offer / ratio), floor(offer × ratio)]. */
export function wantRange(offerAmount: number, maxRatio: number): { min: number; max: number } {
  return { min: Math.max(1, Math.ceil(offerAmount / maxRatio - 1e-9)), max: Math.floor(offerAmount * maxRatio + 1e-9) };
}

/**
 * The most of one resource that can go into a transport: what is in stock, and what the free
 * merchants can still carry after the other two amounts.
 */
export function maxSendable(stock: number, free: number, others: number): number {
  return Math.max(0, Math.min(Math.floor(stock), free * MERCHANT_CAPACITY - others, MAX_AMOUNT));
}

/** The most units of an own offer: stock, free merchants and the limit of 1000 decide. */
export function maxOfferCount(stock: number, free: number, offerAmount: number): number {
  if (offerAmount <= 0) {
    return 0;
  }
  const byMerchants = Math.floor(free / merchantsNeeded(offerAmount));
  return Math.max(0, Math.min(MAX_OFFER_COUNT, Math.floor(stock / offerAmount), byMerchants));
}

/**
 * How often the player can accept an offer: no more than is left, the acceptor's stock of the wanted
 * resource and its free merchants (they carry the payment) allow.
 */
export function maxAcceptable(remaining: number, wantAmount: number, stock: number, free: number): number {
  if (wantAmount <= 0) {
    return 0;
  }
  const byStock = Math.floor(stock / wantAmount);
  const byMerchants = Math.floor((free * MERCHANT_CAPACITY) / wantAmount);
  return Math.max(0, Math.min(remaining, byStock, byMerchants));
}

/** What an offer is worth to whoever accepts it: how much one gets for one paid (higher = better). */
export function offerValue(offer: { offerAmount: number; wantAmount: number }): number {
  return offer.offerAmount / offer.wantAmount;
}

/** The next time something on the market changes: a transport arrives or its merchants are home. */
export function nextMarketChangeMs(
  transports: readonly { arriveAt: string; returnAt: string; arrived: boolean }[],
): number | undefined {
  let next: number | undefined;
  for (const transport of transports) {
    const at = Date.parse(transport.arrived ? transport.returnAt : transport.arriveAt);
    if (next === undefined || at < next) {
      next = at;
    }
  }
  return next;
}
