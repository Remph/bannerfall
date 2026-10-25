import type { MapMovement, MapMovementLine } from '../api/queries/map';

/*
 * The movement markers of the map (M26, M36, 02-game-rules §6, 05-frontend §6): which badges a
 * village gets from the player's own movements towards it. Pure, so the rules are tested without a
 * canvas.
 */

/**
 * Red crossed swords: attacks. Yellow crossed swords: attacks of the player's own that carry
 * nobles (only he knows; an incoming attack hides its units). Green sword: supports. Blue arrow
 * (M36): his own troops walking home to this village.
 */
export type MarkerKind = 'attack' | 'noble' | 'support' | 'return';

export interface Marker {
  kind: MarkerKind;
  /** Movements behind the badge; above 1 the badge shows the number. */
  count: number;
}

/**
 * The badges of one village's top left corner, left to right: the attacks first (yellow as soon as
 * one of the player's own attacks has nobles, then the badge counts all attacks), then the
 * supports. At most one of each, so a besieged village with supports coming shows both. Returns
 * have their own corner ({@link returnMarker}).
 */
export function movementMarkers(movement: MapMovement): Marker[] {
  const markers: Marker[] = [];
  if (movement.attacks > 0) {
    markers.push({ kind: movement.nobleAttacks > 0 ? 'noble' : 'attack', count: movement.attacks });
  }
  if (movement.supports > 0) {
    markers.push({ kind: 'support', count: movement.supports });
  }
  return markers;
}

/**
 * The badge of the top right corner (M36): the player's own troops walking home to the village,
 * after a battle, a cancelled command or a support withdrawn or sent back. The top right is free on
 * his villages: the NPC lord's pennant is only drawn on NPC villages.
 */
export function returnMarker(movement: MapMovement): Marker | undefined {
  return movement.returns > 0 ? { kind: 'return', count: movement.returns } : undefined;
}

/** The number on a badge: nothing for one movement, "9+" beyond nine (the badge is small). */
export function markerCount(count: number): string {
  if (count <= 1) {
    return '';
  }
  return count > 9 ? '9+' : String(count);
}

/**
 * The earliest arrival of all entries (ms), returns included, when the map asks again; undefined
 * without any.
 */
export function nextMovementArrivalMs(movements: readonly MapMovement[]): number | undefined {
  let next: number | undefined;
  for (const movement of movements) {
    for (const time of [movement.nextArriveAt, movement.nextReturnAt]) {
      if (time === null) {
        continue;
      }
      const at = Date.parse(time);
      if (next === undefined || at < next) {
        next = at;
      }
    }
  }
  return next;
}

/**
 * The drawings of the badges, as SVG path data in a 24 × 24 box (stroked with round caps). The
 * canvas draws them with Path2D, the legend with an <svg>, so both look the same. Crossed swords
 * are the top bar's "troops" icon; the support's single sword is one of its two swords; the return
 * is the rally point's "returning" arrow.
 */
export const MARKER_PATHS: Record<MarkerKind, string> = {
  attack: 'M5 19 L19 5 M19 19 L5 5 M4 16 L8 20 M16 20 L20 16',
  noble: 'M5 19 L19 5 M19 19 L5 5 M4 16 L8 20 M16 20 L20 16',
  support: 'M5 19 L19 5 M4 16 L8 20',
  // one unit lower than the rally point's icon, so the arrow sits in the middle of the round badge
  return: 'M19 20 V13 A5 5 0 0 0 14 8 H5 M9 4 L5 8 L9 12',
};

/** The order of the legend. */
export const markerKinds: readonly MarkerKind[] = ['attack', 'noble', 'support', 'return'];

/** Whether a movement leaves from or arrives at the village: what "only this village" keeps (1.6.3). */
export function lineOfVillage(line: MapMovementLine, villageId: number): boolean {
  return line.from.id === villageId || line.to.id === villageId;
}

/**
 * The markers of some movements, from their lines: the same entries the server sends for all of
 * them (GET /api/map/movements groups on the target village the same way), so the map can show the
 * markers of one village only.
 */
export function movementsOfLines(lines: readonly MapMovementLine[]): MapMovement[] {
  const byTarget = new Map<number, MapMovement>();
  for (const line of lines) {
    const earlier = byTarget.get(line.to.id);
    const next: MapMovement = earlier ?? {
      villageId: line.to.id,
      x: line.to.x,
      y: line.to.y,
      attacks: 0,
      nobleAttacks: 0,
      supports: 0,
      returns: 0,
      nextArriveAt: null,
      nextReturnAt: null,
    };
    if (line.kind === 'RETURN') {
      next.returns += 1;
      next.nextReturnAt = earliest(next.nextReturnAt, line.arriveAt);
    } else {
      if (line.kind === 'ATTACK') {
        next.attacks += 1;
        next.nobleAttacks += line.nobles ? 1 : 0;
      } else {
        next.supports += 1;
      }
      next.nextArriveAt = earliest(next.nextArriveAt, line.arriveAt);
    }
    byTarget.set(line.to.id, next);
  }
  return [...byTarget.values()].sort((a, b) => a.villageId - b.villageId);
}

function earliest(current: string | null, time: string): string {
  return current === null || Date.parse(time) < Date.parse(current) ? time : current;
}
