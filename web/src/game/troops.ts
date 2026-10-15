import { unitTypes, type UnitType } from './gameData';

/**
 * Helpers for troops and movements (02-game-rules §6) – pure. Unit maps come from the API keyed by
 * unit name; missing types mean 0 (04-api).
 */
export type UnitMap = Partial<Record<UnitType, number>>;

/** The units that are there, in unit order: [['SPEARMAN', 120], ['AXEMAN', 30]]. */
export function presentUnits(units: UnitMap): [UnitType, number][] {
  // the return type on the arrow function tells flatMap that each entry is a [type, count] pair
  return unitTypes.flatMap((type): [UnitType, number][] => {
    const count = units[type] ?? 0;
    return count > 0 ? [[type, count]] : [];
  });
}

/** Only the types with a count above 0: the units of a command or a withdrawal. */
export function withoutZeros(units: UnitMap): UnitMap {
  return Object.fromEntries(presentUnits(units));
}

/**
 * The unit rows of the send form: every unit at home, plus units that still have a count typed in
 * (they went home elsewhere meanwhile – the row stays, so the count doesn't vanish unseen).
 */
export function sendableUnits(home: UnitMap, counts: UnitMap): UnitType[] {
  return unitTypes.filter((type) => (home[type] ?? 0) > 0 || (counts[type] ?? 0) > 0);
}

/** The most attacks an attack train holds (M37, 02-game-rules §6.1; the server refuses more). */
export const MAX_TRAIN_ATTACKS = 5;

/** What is left at home for the next attack once the attacks lined up in the train are taken. */
export function leftForTrain(home: UnitMap, train: readonly UnitMap[]): UnitMap {
  const left: UnitMap = { ...home };
  for (const attack of train) {
    for (const [type, count] of presentUnits(attack)) {
      left[type] = Math.max(0, (left[type] ?? 0) - count);
    }
  }
  return withoutZeros(left);
}

/**
 * The counts, each cut down to what is available. After an attack joins the train the form keeps
 * what still fits, so another noble attack of the same size is one more tap.
 */
export function capCounts(counts: UnitMap, available: UnitMap): UnitMap {
  return withoutZeros(
    Object.fromEntries(presentUnits(counts).map(([type, count]) => [type, Math.min(count, available[type] ?? 0)])),
  );
}

/**
 * The attacks a send with a train goes out with: those lined up, then the picked units if there are
 * any and the train has room for them.
 */
export function trainAttacks(train: readonly UnitMap[], picked: UnitMap): UnitMap[] {
  const units = withoutZeros(picked);
  return presentUnits(units).length === 0 || train.length >= MAX_TRAIN_ATTACKS ? [...train] : [...train, units];
}

/**
 * A command can be cancelled while `cancellableUntil` hasn't come (exclusive, already capped at the
 * arrival by the server); a return never (null).
 */
export function canCancel(movement: { cancellableUntil: string | null }, nowMs: number): boolean {
  return movement.cancellableUntil !== null && nowMs < Date.parse(movement.cancellableUntil);
}

/** When the next of these movements arrives (ms since 1970); undefined if there are none. */
export function nextArrivalMs(movements: readonly { arriveAt: string }[]): number | undefined {
  let next: number | undefined;
  for (const movement of movements) {
    const at = Date.parse(movement.arriveAt);
    if (next === undefined || at < next) {
      next = at;
    }
  }
  return next;
}

/** The kinds of movements (server enum MovementKind); a player sends only attacks and supports. */
export type MovementKind = 'ATTACK' | 'SUPPORT' | 'RETURN';
export type CommandKind = Exclude<MovementKind, 'RETURN'>;

/** One unit type of the rally point's troop overview (M33). */
export interface TroopOverviewRow {
  type: UnitType;
  /** The village's own units at home. */
  home: number;
  /** Its own units away: standing in other villages, or on the way there or back home. */
  outside: number;
  /** All of the village's own units: home + outside. */
  total: number;
  /** Units of other villages standing here as support – not the village's own, so not in `total`. */
  support: number;
}

/** What the overview needs of GET …/troops (`Troops` in api/queries/troops.ts). */
interface TroopLists extends AwayLists {
  home: UnitMap;
  supportsHere: readonly { units: UnitMap }[];
}

/** What `unitsAway` needs of GET …/troops. */
interface AwayLists {
  ownUnitsElsewhere: readonly { units: UnitMap }[];
  outgoing: readonly { units: UnitMap }[];
  incoming: readonly { kind: MovementKind; units: UnitMap | null }[];
}

/**
 * The village's own units away, per type: the stacks in other villages, the outgoing commands
 * (`outgoing` holds only this village's attacks and supports) and the RETURNs in `incoming`: a
 * return always walks to its home village, so the ones arriving here are this village's units.
 * Incoming attacks and supports belong to other villages (an own attack on a village conquered
 * meanwhile too).
 */
export function unitsAway(troops: AwayLists): UnitMap {
  const maps = [
    ...troops.ownUnitsElsewhere.map((stack) => stack.units),
    ...troops.outgoing.map((movement) => movement.units),
    ...troops.incoming.flatMap((movement) =>
      movement.kind === 'RETURN' && movement.units !== null ? [movement.units] : [],
    ),
  ];
  return sumUnits(maps);
}

function sumUnits(maps: readonly UnitMap[]): UnitMap {
  const sum: UnitMap = {};
  for (const units of maps) {
    for (const type of unitTypes) {
      const count = units[type] ?? 0;
      if (count > 0) {
        sum[type] = (sum[type] ?? 0) + count;
      }
    }
  }
  return sum;
}

/**
 * The troop overview of the rally point (M33): per unit type, how many of the village's own units
 * are at home, away (`unitsAway`) and in all, and how much support of other villages stands here.
 * Only types with any of them, in unit order.
 */
export function troopOverview(troops: TroopLists): TroopOverviewRow[] {
  const away = unitsAway(troops);
  const supports = sumUnits(troops.supportsHere.map((stack) => stack.units));
  return unitTypes.flatMap((type): TroopOverviewRow[] => {
    const home = troops.home[type] ?? 0;
    const outside = away[type] ?? 0;
    const support = supports[type] ?? 0;
    return home + outside + support > 0 ? [{ type, home, outside, total: home + outside, support }] : [];
  });
}
