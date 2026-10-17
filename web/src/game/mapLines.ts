import type { MapMovementLine } from '../api/queries/map';
import type { MarkerKind } from './mapMovements';
import type { Coords } from './coords';
import type { Point } from './mapView';

/*
 * The movement lines of the map (v1.3, 02-game-rules §6, 05-frontend §6): every movement of the
 * markers as a line from where it started to where it arrives, solid behind the troops, dotted
 * ahead, with the marker's badge where the troops are now. Pure, so the rules are tested without a
 * canvas. Positions are in fields: the middle of field x|y is (x + 0.5, y + 0.5).
 */

/** The colour and badge of a line: the same kinds as the village markers. */
export function lineKind(line: MapMovementLine): MarkerKind {
  if (line.kind === 'RETURN') {
    return 'return';
  }
  if (line.kind === 'SUPPORT') {
    return 'support';
  }
  return line.nobles ? 'noble' : 'attack';
}

/** Another player's attack on one of the player's villages: the one line drawn to stand out. */
export function lineHostile(line: MapMovementLine): boolean {
  return !line.own && line.kind === 'ATTACK';
}

/** Distance between the two villages in fields. */
export function lineDistance(line: MapMovementLine): number {
  return Math.hypot(line.to.x - line.from.x, line.to.y - line.from.y);
}

/**
 * How much of the way is behind the troops, 0 to 1; undefined for another player's attack (its
 * start time is hidden, so is where it is).
 */
export function lineProgress(line: MapMovementLine, nowMs: number): number | undefined {
  if (line.departAt === null) {
    return undefined;
  }
  const depart = Date.parse(line.departAt);
  const arrive = Date.parse(line.arriveAt);
  if (arrive <= depart) {
    return 1;
  }
  return Math.min(1, Math.max(0, (nowMs - depart) / (arrive - depart)));
}

/** Fields still ahead of the troops; undefined where their position is hidden. */
export function lineFieldsLeft(line: MapMovementLine, nowMs: number): number | undefined {
  const progress = lineProgress(line, nowMs);
  return progress === undefined ? undefined : lineDistance(line) * (1 - progress);
}

/** Where a line starts and ends, in map coordinates (the middles of the two fields). */
export function lineEnds(line: MapMovementLine): { from: Point; to: Point } {
  return {
    from: { x: line.from.x + 0.5, y: line.from.y + 0.5 },
    to: { x: line.to.x + 0.5, y: line.to.y + 0.5 },
  };
}

/** Where the troops are now, in map coordinates; undefined where their position is hidden. */
export function linePosition(line: MapMovementLine, nowMs: number): Point | undefined {
  const progress = lineProgress(line, nowMs);
  if (progress === undefined) {
    return undefined;
  }
  const { from, to } = lineEnds(line);
  return { x: from.x + (to.x - from.x) * progress, y: from.y + (to.y - from.y) * progress };
}

/** Where the bubble of a line points to: the troops, or the middle of the way where they are hidden. */
export function lineAnchor(line: MapMovementLine, nowMs: number): Point {
  const position = linePosition(line, nowMs);
  if (position !== undefined) {
    return position;
  }
  const { from, to } = lineEnds(line);
  return { x: (from.x + to.x) / 2, y: (from.y + to.y) / 2 };
}

/** Whether the line starts or ends at the village (a selected village highlights its lines). */
export function lineTouches(line: MapMovementLine, villageId: number): boolean {
  return line.from.id === villageId || line.to.id === villageId;
}

/**
 * The line under a tap or the mouse, `point` and the reaches in fields: the troops' badge first
 * (within `markerReach` of where they are now), the nearest one wins; otherwise a line passing within
 * `lineReach`. The caller checks villages in between, so a tap on a village still selects it.
 */
export function lineMarkerNear(
  lines: readonly MapMovementLine[],
  point: Point,
  nowMs: number,
  markerReach: number,
): MapMovementLine | undefined {
  let best: MapMovementLine | undefined;
  let bestDistance = markerReach;
  for (const line of lines) {
    const position = linePosition(line, nowMs);
    if (position === undefined) {
      continue;
    }
    const distance = Math.hypot(position.x - point.x, position.y - point.y);
    if (distance <= bestDistance) {
      best = line;
      bestDistance = distance;
    }
  }
  return best;
}

/**
 * Whether a tap (or the mouse) at `point` means the troop badge at `badge` (the nearest within
 * reach) rather than `village`: over a village only a tap on the drawn badge itself, `onBadge`
 * fields from its middle (its radius and a little slack), picks the troops. So troops just sent or
 * almost home don't block the rest of their village's field, also when several fan out from it.
 */
export function badgeWins(point: Point, badge: Point, village: Coords | undefined, onBadge: number): boolean {
  return village === undefined || Math.hypot(badge.x - point.x, badge.y - point.y) <= onBadge;
}

/** The line passing closest to `point`, if within `lineReach` fields (see {@link lineMarkerNear}). */
export function lineNear(
  lines: readonly MapMovementLine[],
  point: Point,
  lineReach: number,
): MapMovementLine | undefined {
  let best: MapMovementLine | undefined;
  let bestDistance = lineReach;
  for (const line of lines) {
    const distance = distanceToSegment(point, lineEnds(line));
    if (distance <= bestDistance) {
      best = line;
      bestDistance = distance;
    }
  }
  return best;
}

function distanceToSegment(point: Point, { from, to }: { from: Point; to: Point }): number {
  const dx = to.x - from.x;
  const dy = to.y - from.y;
  const length2 = dx * dx + dy * dy;
  const t = length2 === 0 ? 0 : Math.min(1, Math.max(0, ((point.x - from.x) * dx + (point.y - from.y) * dy) / length2));
  return Math.hypot(from.x + dx * t - point.x, from.y + dy * t - point.y);
}

const HALF = 0.5;

/** Own troops still within this many fields of their start fold into one badge at the village. */
export const FOLD_REACH = 1;

/** A badge folds this many movements or more; one alone keeps its own. */
const FOLD_MIN = 2;

/** Which kind a folded badge shows: the strongest among its troops. */
const FOLD_KIND_ORDER: readonly MarkerKind[] = ['noble', 'attack', 'support', 'return'];

/** Several own movements that just left one village, drawn as one badge with their number. */
export interface FoldedBadge {
  /** The village they left. */
  start: MapMovementLine['from'];
  kind: MarkerKind;
  lines: MapMovementLine[];
}

/**
 * The player's own troops that left the same village a moment ago (still within {@link FOLD_REACH}
 * fields of it) fold into one badge when there are at least two of them, so a farm round doesn't
 * bury the village under its badges. The picked line keeps its own badge, and so do the lines of the
 * selected village: picking the village (or tapping the folded badge on it) shows each of them again.
 */
export function foldedBadges(
  lines: readonly MapMovementLine[],
  nowMs: number,
  keep: { lineId: number | null; villageId: number | null },
): FoldedBadge[] {
  const leaving = lines.filter((line) => {
    const gone = lineFieldsGone(line, nowMs);
    return (
      line.own && gone !== undefined && gone < FOLD_REACH && line.id !== keep.lineId && line.from.id !== keep.villageId
    );
  });
  const starts = [...new Set(leaving.map((line) => line.from.id))];
  return starts.flatMap((startId) => {
    const group = leaving.filter((line) => line.from.id === startId);
    const first = group[0];
    if (first === undefined || group.length < FOLD_MIN) {
      return [];
    }
    const kinds = new Set(group.map(lineKind));
    return [
      { start: first.from, kind: FOLD_KIND_ORDER.find((kind) => kinds.has(kind)) ?? lineKind(first), lines: group },
    ];
  });
}

function lineFieldsGone(line: MapMovementLine, nowMs: number): number | undefined {
  const progress = lineProgress(line, nowMs);
  return progress === undefined ? undefined : lineDistance(line) * progress;
}

/** A troop badge where it is drawn, and how many movements it stands for. */
export interface PlacedBadge {
  line: MapMovementLine;
  /** Where it is drawn (screen px). */
  at: Point;
  count: number;
}

/**
 * Badges of one way and kind that touch (a farm round sent to the same target a few seconds apart)
 * merge into one with their number: the one arriving first stands for them. Badges closer than
 * `reach` (screen px) touch. The picked line never merges, so its badge stays where its troops are.
 */
export function mergeTouchingBadges(
  badges: readonly { line: MapMovementLine; at: Point }[],
  reach: number,
  pickedLineId: number | null,
): PlacedBadge[] {
  return [...badges]
    .sort((a, b) => Date.parse(a.line.arriveAt) - Date.parse(b.line.arriveAt))
    .reduce<PlacedBadge[]>((shown, badge) => {
      const into =
        badge.line.id === pickedLineId
          ? undefined
          : shown.find(
              (other) =>
                other.line.id !== pickedLineId &&
                other.line.from.id === badge.line.from.id &&
                other.line.to.id === badge.line.to.id &&
                lineKind(other.line) === lineKind(badge.line) &&
                Math.hypot(other.at.x - badge.at.x, other.at.y - badge.at.y) < reach,
            );
      return into === undefined
        ? [...shown, { ...badge, count: 1 }]
        : shown.map((other) => (other === into ? { ...other, count: other.count + 1 } : other));
    }, []);
}

/**
 * Where the arrows along a way `length` long sit, measured from its start: every `spacing` or a
 * little closer, `margin` clear of both ends; one in the middle of a way too short for two, none on
 * a way shorter than `minLength`. Any unit, as long as all are the same (the map uses screen px).
 */
export function chevronOffsets(length: number, margin: number, spacing: number, minLength: number): number[] {
  if (length < minLength) {
    return [];
  }
  const room = length - margin - margin;
  if (room < spacing) {
    return [length * HALF];
  }
  const count = Math.floor(room / spacing) + 1;
  const step = room / (count - 1);
  return Array.from({ length: count }, (_, index) => margin + index * step);
}

/** The earliest arrival of all lines (ms), when the map asks again; undefined without any. */
export function nextLineArrivalMs(lines: readonly MapMovementLine[]): number | undefined {
  let next: number | undefined;
  for (const line of lines) {
    const at = Date.parse(line.arriveAt);
    if (next === undefined || at < next) {
      next = at;
    }
  }
  return next;
}
