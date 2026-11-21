import type { ChronicleMap, ChronicleMapSide } from '../../api/queries/chronicle';
import { BARBARIAN } from '../../game/chronicle';
import { guildColor } from '../../game/politicalMap';
import { drawKeepMark, type MapColors } from '../map/mapDraw';

/*
 * Draws one day of the chronicle's replay map (2.1 I1): the whole map, one square per village in its
 * owner's colour with a soft area around each side's villages (like the overview's political layer),
 * the banner keeps, and a ring around the village of the moment the player picked. Plain canvas calls
 * like overviewDraw.ts; the caller has scaled the context for the device pixel ratio.
 */

const SQUARE_MIN = 3;
const TERRITORY_FIELDS = 2.5;
const TERRITORY_MIN_RADIUS = 5;
const RING_MIN_HALF = 6;

export interface ReplayScene {
  /** The canvas's side in CSS px (the map is square). */
  side: number;
  map: ChronicleMap;
  /** The owner of each village (a side's index, BARBARIAN or ABSENT). */
  state: Int32Array;
  colors: MapColors;
  theme: 'light' | 'dark';
  /** A village to ring (its index in `map.villages`), or null. */
  highlight: number | null;
}

/** Hues this close to the own red (about 6°) would read as the reader's own villages. */
const OWN_HUE = 6;
const OWN_HUE_GAP = 32;

function hueOf(color: string): number {
  const match = /hsl\((\d+)/.exec(color);
  return match === null ? 180 : Number(match[1]);
}

/** guildColor for `seed`, stepped on to the next seed while it lands near the own red. */
function awayFromOwn(seed: number, theme: 'light' | 'dark'): string {
  let color = guildColor(seed, theme);
  for (let step = 1; step <= 12; step++) {
    const distance = Math.abs(((hueOf(color) - OWN_HUE + 540) % 360) - 180);
    if (distance >= OWN_HUE_GAP) {
      break;
    }
    color = guildColor(seed + step * 1009, theme);
  }
  return color;
}

/**
 * A side's colour: the player's own in the own red (as on every map), guilds in their colour and
 * players without a guild each in a hue of their own, both kept away from the own red, lords in
 * the lords' colour.
 */
export function sideColor(side: ChronicleMapSide, colors: MapColors, theme: 'light' | 'dark'): string {
  if (side.mine) {
    return colors.kinds.own;
  }
  if (side.kind === 'GUILD') {
    return awayFromOwn(side.id, theme);
  }
  return side.npc ? colors.kinds.npc : awayFromOwn(side.id * 7 + 3, theme);
}

/** Fields of land shown around the outermost villages. */
const VIEW_MARGIN = 4;
/** The smallest square shown, in fields, so a young world isn't drawn huge. */
const VIEW_MIN = 20;

/**
 * The square of the map the replay shows: the villages of the whole story with a margin, not the
 * whole map, since a world fills its middle first. Inside the map; the whole map if it is small.
 */
export function viewOf(map: ChronicleMap): { x: number; y: number; span: number } {
  const size = Math.max(1, map.size);
  if (map.villages.length === 0 || size <= VIEW_MIN) {
    return { x: 0, y: 0, span: size };
  }
  const xs = map.villages.map((dot) => dot.x);
  const ys = map.villages.map((dot) => dot.y);
  const minX = Math.min(...xs);
  const minY = Math.min(...ys);
  const span = Math.min(
    size,
    Math.max(VIEW_MIN, Math.max(...xs) - minX + 1, Math.max(...ys) - minY + 1) + 2 * VIEW_MARGIN,
  );
  const centre = (low: number, high: number) =>
    Math.max(0, Math.min(size - span, Math.round((low + high + 1) / 2 - span / 2)));
  return { x: centre(minX, Math.max(...xs)), y: centre(minY, Math.max(...ys)), span };
}

export function drawReplay(ctx: CanvasRenderingContext2D, scene: ReplayScene): void {
  const { side, map, state, colors, theme } = scene;
  const view = viewOf(map);
  const tile = side / view.span;
  ctx.fillStyle = colors.grass;
  ctx.fillRect(0, 0, side, side);
  ctx.lineWidth = 2;
  ctx.strokeStyle = colors.edge;
  ctx.strokeRect(1, 1, side - 2, side - 2);

  const fills = map.sides.map((entry) => sideColor(entry, colors, theme));
  const middle = (index: number) => {
    const dot = map.villages[index];
    return dot === undefined ? null : { x: (dot.x - view.x + 0.5) * tile, y: (dot.y - view.y + 0.5) * tile };
  };

  // the soft areas: one shape per side, filled once, so overlapping discs run together
  const radius = Math.max(TERRITORY_MIN_RADIUS, tile * TERRITORY_FIELDS);
  const areas = new Map<number, Path2D>();
  state.forEach((owner, index) => {
    const point = owner >= 0 ? middle(index) : null;
    if (point === null) {
      return;
    }
    let area = areas.get(owner);
    if (area === undefined) {
      area = new Path2D();
      areas.set(owner, area);
    }
    area.moveTo(point.x + radius, point.y);
    area.arc(point.x, point.y, radius, 0, Math.PI * 2);
  });
  ctx.save();
  ctx.beginPath();
  ctx.rect(0, 0, side, side);
  ctx.clip();
  ctx.globalAlpha = 0.2;
  for (const [owner, area] of areas) {
    ctx.fillStyle = fills[owner] ?? colors.kinds.player;
    ctx.fill(area);
  }
  ctx.restore();

  const square = Math.max(SQUARE_MIN, tile);
  state.forEach((owner, index) => {
    const point = owner >= 0 || owner === BARBARIAN ? middle(index) : null;
    if (point === null) {
      return;
    }
    ctx.fillStyle = owner === BARBARIAN ? colors.kinds.barbarian : (fills[owner] ?? colors.kinds.player);
    ctx.fillRect(point.x - square / 2, point.y - square / 2, square, square);
    if (owner >= 0 && map.sides[owner]?.mine === true) {
      ctx.lineWidth = 1.5;
      ctx.strokeStyle = colors.outline;
      ctx.strokeRect(point.x - square / 2, point.y - square / 2, square, square);
    }
  });
  map.villages.forEach((dot, index) => {
    const point = dot.keep && (state[index] ?? -2) !== -2 ? middle(index) : null;
    if (point !== null) {
      drawKeepMark(ctx, point, square, colors);
    }
  });

  if (scene.highlight !== null) {
    const point = middle(scene.highlight);
    if (point !== null) {
      const half = Math.max(RING_MIN_HALF, tile * 1.8);
      ctx.beginPath();
      ctx.arc(point.x, point.y, half, 0, Math.PI * 2);
      ctx.lineWidth = 4;
      ctx.strokeStyle = colors.outline;
      ctx.stroke();
      ctx.lineWidth = 2;
      ctx.strokeStyle = colors.marker;
      ctx.stroke();
    }
  }
}
