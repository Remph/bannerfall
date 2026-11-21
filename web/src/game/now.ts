import type { Now, NowVillage } from '../api/queries/now';
import type { IncomingMovement, Movement } from '../api/queries/troops';
import type { BuildOrder } from '../api/queries/buildings';
import type { RecruitOrder } from '../api/queries/recruitment';

/**
 * The Now list (2.0 N1): what runs in the player's villages, grouped the way the column, the drawer
 * and the phone sheet show it. Pure functions; the components add the texts and the clock.
 */

/** Which villages the list covers: the one the player looks at, or all of them (remembered). */
export type NowScope = 'this' | 'all';

type Order = BuildOrder | RecruitOrder;
type Dismissal = NowVillage['dismissals'][number];
type Research = NowVillage['research'][number];
type Transport = NowVillage['merchants'][number];
export type VillageRef = NowVillage['village'];

/** One line of the list, with the village it belongs to. */
export type NowRow =
  | { kind: 'attack'; key: string; village: VillageRef; at: number; movement: IncomingMovement }
  | { kind: 'build'; key: string; village: VillageRef; at: number; order: BuildOrder }
  | { kind: 'train'; key: string; village: VillageRef; at: number; order: RecruitOrder; waiting: number }
  | { kind: 'dismiss'; key: string; village: VillageRef; at: number; order: Dismissal; waiting: number }
  | { kind: 'research'; key: string; village: VillageRef; at: number; order: Research }
  | { kind: 'incoming'; key: string; village: VillageRef; at: number; movement: IncomingMovement }
  | { kind: 'outgoing'; key: string; village: VillageRef; at: number; movement: Movement }
  | { kind: 'merchants'; key: string; village: VillageRef; at: number; transport: Transport }
  /** Another player's merchants on their way to the village (2.1). */
  | { kind: 'merchantsIn'; key: string; village: VillageRef; at: number; transport: Transport };

export interface NowSections {
  /** Hostile attacks on any village (a hidden attack would be worse than a long list). */
  attacks: NowRow[];
  building: NowRow[];
  /** Recruiting, dismissing and researching. */
  training: NowRow[];
  /** Commands, supports, returning troops and merchants (own ones and others' coming here), next first. */
  way: NowRow[];
  /** All lines: what the tab and the "More" row count. */
  count: number;
}

/** Someone else's attack: the server hides its units. */
export function isHostile(movement: IncomingMovement): boolean {
  return movement.kind === 'ATTACK' && movement.units === null;
}

function inScope(scope: NowScope, currentVillageId: number | undefined, village: VillageRef): boolean {
  return scope === 'all' || currentVillageId === undefined || village.id === currentVillageId;
}

/** Each building's queue has one running order (the first); the others wait behind it. */
function runningPerBuilding<T extends { building: string }>(orders: readonly T[]): { order: T; waiting: number }[] {
  const waitingBy = new Map<string, number>();
  const seen = new Set<string>();
  const result: { order: T; waiting: number }[] = [];
  for (const order of orders) {
    if (!seen.has(order.building)) {
      seen.add(order.building);
      result.push({ order, waiting: 0 });
    } else {
      waitingBy.set(order.building, (waitingBy.get(order.building) ?? 0) + 1);
    }
  }
  return result.map((entry) => ({ ...entry, waiting: waitingBy.get(entry.order.building) ?? 0 }));
}

/** The moment a line is next about to change: when it arrives, is ready or is back. */
function transportAt(transport: Transport): number {
  return Date.parse(transport.arrived ? transport.returnAt : transport.arriveAt);
}

export function nowSections(now: Now, scope: NowScope, currentVillageId: number | undefined): NowSections {
  const attacks: NowRow[] = [];
  const building: NowRow[] = [];
  const training: NowRow[] = [];
  const way: NowRow[] = [];
  // a support between two own villages is one line, the sender's (it has the cancel button)
  const sentInScope = new Set(
    now.villages
      .filter((entry) => inScope(scope, currentVillageId, entry.village))
      .flatMap((entry) => entry.outgoing.map((movement) => movement.id)),
  );

  for (const entry of now.villages) {
    const village = entry.village;
    // attacks are shown for every village whatever the scope is
    for (const movement of entry.incoming) {
      if (isHostile(movement)) {
        attacks.push({
          kind: 'attack',
          key: `attack-${movement.id}`,
          village,
          at: Date.parse(movement.arriveAt),
          movement,
        });
      }
    }
    if (!inScope(scope, currentVillageId, village)) {
      continue;
    }
    for (const order of entry.builds) {
      building.push({ kind: 'build', key: `build-${order.id}`, village, at: Date.parse(order.endAt), order });
    }
    for (const { order, waiting } of runningPerBuilding(entry.training)) {
      training.push({ kind: 'train', key: `train-${order.id}`, village, at: Date.parse(order.endAt), order, waiting });
    }
    for (const { order, waiting } of runningPerBuilding(entry.dismissals)) {
      training.push({
        kind: 'dismiss',
        key: `dismiss-${order.id}`,
        village,
        at: Date.parse(order.endAt),
        order,
        waiting,
      });
    }
    for (const order of entry.research) {
      training.push({ kind: 'research', key: `research-${order.id}`, village, at: Date.parse(order.endAt), order });
    }
    for (const movement of entry.incoming) {
      if (!isHostile(movement) && !sentInScope.has(movement.id)) {
        way.push({
          kind: 'incoming',
          key: `incoming-${movement.id}`,
          village,
          at: Date.parse(movement.arriveAt),
          movement,
        });
      }
    }
    for (const movement of entry.outgoing) {
      way.push({
        kind: 'outgoing',
        key: `outgoing-${movement.id}`,
        village,
        at: Date.parse(movement.arriveAt),
        movement,
      });
    }
    for (const transport of entry.merchants) {
      way.push({
        kind: 'merchants',
        key: `merchants-${transport.id}`,
        village,
        at: transportAt(transport),
        transport,
      });
    }
    for (const transport of entry.incomingMerchants) {
      way.push({
        kind: 'merchantsIn',
        key: `merchants-in-${transport.id}`,
        village,
        at: Date.parse(transport.arriveAt),
        transport,
      });
    }
  }
  const byTime = (a: NowRow, b: NowRow) => a.at - b.at;
  attacks.sort(byTime);
  training.sort(byTime);
  way.sort(byTime);
  // the build queue keeps its running order (the sort is stable and the queue ends increase)
  building.sort(byTime);
  return {
    attacks,
    building,
    training,
    way,
    count: attacks.length + building.length + training.length + way.length,
  };
}

/** Hostile attacks on any village: what the red dot on the tab says. */
export function hostileCount(now: Now): number {
  return now.villages.reduce((sum, entry) => sum + entry.incoming.filter(isHostile).length, 0);
}

/**
 * When the next timer of the list ends (ms since 1970), over all villages; undefined if nothing
 * runs. A timer that is over already counts too, so the list asks again until the server moved on.
 */
export function nextNowEndMs(now: Now): number | undefined {
  let next: number | undefined;
  const see = (iso: string) => {
    const at = Date.parse(iso);
    if (next === undefined || at < next) {
      next = at;
    }
  };
  for (const entry of now.villages) {
    entry.builds.forEach((order) => see(order.endAt));
    entry.training.forEach((order) => see(order.endAt));
    entry.dismissals.forEach((order) => see(order.endAt));
    entry.research.forEach((order) => see(order.endAt));
    entry.outgoing.forEach((movement) => see(movement.arriveAt));
    entry.incoming.forEach((movement) => see(movement.arriveAt));
    entry.merchants.forEach((transport) => see(transport.arrived ? transport.returnAt : transport.arriveAt));
    entry.incomingMerchants.forEach((transport) => see(transport.arriveAt));
  }
  return next;
}

/** Orders have a start and an end, the bar shows how far they are (0 to 1). */
export function orderProgress(order: Pick<Order, 'startAt' | 'endAt'>, nowMs: number): number {
  const start = Date.parse(order.startAt);
  const end = Date.parse(order.endAt);
  if (!(end > start)) {
    return 1;
  }
  return Math.min(1, Math.max(0, (nowMs - start) / (end - start)));
}
