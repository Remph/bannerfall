import type { MapVillage } from '../../api/queries/map';
import type { Coords } from '../../game/coords';
import { guildColor, type MapLayer } from '../../game/politicalMap';
import {
  axisValues,
  OVERVIEW_GRID,
  overviewLabelStep,
  toScreen,
  villageKind,
  visibleFields,
  type Camera,
  type Rect,
  type Size,
} from '../../game/mapView';
import { drawKeepMark, type MapColors } from './mapDraw';

/*
 * Draws the overview map (M11b, 05-frontend §6): the map area with a light grid every 10 fields and
 * a few labels, one square per village in the owner's colour, the current village marked and the
 * rectangle of what the detailed map shows. Plain canvas calls like mapDraw.ts; the caller has
 * already scaled the context for the device pixel ratio.
 */

const FONT = '"Alegreya Sans", system-ui, sans-serif';
/** A village is at least this many CSS px wide, so it stays visible on a whole big map. */
const SQUARE_MIN = 3;
/** The reach of a village's area on the political layer: this many fields, at least this many CSS px. */
const TERRITORY_FIELDS = 2.5;
const TERRITORY_MIN_RADIUS = 7;
/** Half the side of the ring around the current village, at least (CSS px). */
const RING_MIN_HALF = 5;

export interface OverviewScene {
  overview: Camera;
  size: Size;
  mapSize: number;
  villages: readonly MapVillage[];
  ownPlayerId: number;
  /** The player's guild: its members' villages get the mate colour (1.6.3). */
  ownGuildId: number | null;
  currentVillage: Coords;
  /** The part the detailed map shows; null while it hasn't been measured yet. */
  view: Rect | null;
  colors: MapColors;
  /** 'guild' (M15c): the villages in their guild's colour, with a soft area around them. */
  layer: MapLayer;
  theme: 'light' | 'dark';
}

export function drawOverview(ctx: CanvasRenderingContext2D, scene: OverviewScene): void {
  const { overview, size, mapSize, colors } = scene;
  const scale = overview.tile;
  ctx.fillStyle = colors.outside;
  ctx.fillRect(0, 0, size.width, size.height);
  const range = visibleFields(overview, size, mapSize);
  const origin = toScreen(overview, size, { x: 0, y: 0 }); // the map's corner (0|0) on screen

  ctx.fillStyle = colors.grass;
  ctx.fillRect(origin.x, origin.y, mapSize * scale, mapSize * scale);

  if (range !== undefined) {
    // the light grid, inside the map
    const grid = new Path2D();
    for (const x of axisValues(Math.max(1, range.minX), Math.min(mapSize - 1, range.maxX + 1), OVERVIEW_GRID)) {
      const screenX = crisp(origin.x + x * scale);
      grid.moveTo(screenX, Math.max(0, origin.y));
      grid.lineTo(screenX, Math.min(size.height, origin.y + mapSize * scale));
    }
    for (const y of axisValues(Math.max(1, range.minY), Math.min(mapSize - 1, range.maxY + 1), OVERVIEW_GRID)) {
      const screenY = crisp(origin.y + y * scale);
      grid.moveTo(Math.max(0, origin.x), screenY);
      grid.lineTo(Math.min(size.width, origin.x + mapSize * scale), screenY);
    }
    ctx.lineWidth = 1;
    ctx.strokeStyle = colors.gridStrong;
    ctx.stroke(grid);
  }

  ctx.lineWidth = 2;
  ctx.strokeStyle = colors.edge;
  ctx.strokeRect(origin.x, origin.y, mapSize * scale, mapSize * scale);

  // villages: squares in the owner's colour, NPC lords with a gold edge like on the detailed map far out
  const side = Math.max(SQUARE_MIN, scale);
  const political = scene.layer === 'guild';
  const visible = scene.villages.filter((village) => {
    const middle = toScreen(overview, size, { x: village.x + 0.5, y: village.y + 0.5 });
    return !(middle.x < -side || middle.x > size.width + side || middle.y < -side || middle.y > size.height + side);
  });
  if (political) {
    drawTerritories(ctx, scene);
  }
  for (const village of visible) {
    const middle = toScreen(overview, size, { x: village.x + 0.5, y: village.y + 0.5 });
    const kind = villageKind(village, scene.ownPlayerId, scene.ownGuildId);
    const guild = political && village.guildId != null ? guildColor(village.guildId, scene.theme) : undefined;
    ctx.fillStyle = guild ?? colors.kinds[kind];
    // players without a guild fade back so the guilds stand out
    ctx.globalAlpha = political && guild === undefined && kind === 'player' ? 0.55 : 1;
    ctx.fillRect(middle.x - side / 2, middle.y - side / 2, side, side);
    ctx.globalAlpha = 1;
    if (kind === 'npc') {
      ctx.lineWidth = 1;
      ctx.strokeStyle = colors.marker;
      ctx.strokeRect(middle.x - side / 2, middle.y - side / 2, side, side);
    } else if (political && kind === 'own') {
      // the player's own villages keep their mark when the colour says "guild"
      ctx.lineWidth = 2;
      ctx.strokeStyle = colors.outline;
      ctx.strokeRect(middle.x - side / 2, middle.y - side / 2, side, side);
    }
  }
  // the banner keeps (1.3), a gold star each on top of the squares
  visible
    .filter((village) => village.keep)
    .forEach((village) =>
      drawKeepMark(ctx, toScreen(overview, size, { x: village.x + 0.5, y: village.y + 0.5 }), side, colors),
    );

  // the current village: a gold ring with a dark edge
  const current = toScreen(overview, size, { x: scene.currentVillage.x + 0.5, y: scene.currentVillage.y + 0.5 });
  const half = Math.max(RING_MIN_HALF, scale * 1.5);
  ctx.beginPath();
  ctx.arc(current.x, current.y, half, 0, Math.PI * 2);
  ctx.lineWidth = 4;
  ctx.strokeStyle = colors.outline;
  ctx.stroke();
  ctx.lineWidth = 2;
  ctx.strokeStyle = colors.marker;
  ctx.stroke();

  if (scene.view !== null) {
    drawViewRect(ctx, scene.view, colors);
  }
  if (range !== undefined) {
    drawLabels(ctx, scene, range.minX, range.maxX, range.minY, range.maxY);
  }
}

/**
 * The areas of the political layer: a soft disc in the guild's colour around each of its villages.
 * The discs of one guild are one shape filled once, so overlapping ones run together into one
 * region (stacked translucent discs would darken where they meet); the other guilds' discs blend
 * over it. Barbarians and players without a guild get none. Clipped to the map, so the areas of
 * villages at the edge don't spill into the margin.
 */
function drawTerritories(ctx: CanvasRenderingContext2D, scene: OverviewScene): void {
  const { overview, size, mapSize } = scene;
  const radius = Math.max(TERRITORY_MIN_RADIUS, overview.tile * TERRITORY_FIELDS);
  const areas = new Map<number, Path2D>();
  for (const village of scene.villages) {
    if (village.guildId == null) {
      continue;
    }
    const middle = toScreen(overview, size, { x: village.x + 0.5, y: village.y + 0.5 });
    // a village just off screen can still colour the edge of it
    if (middle.x < -radius || middle.x > size.width + radius || middle.y < -radius || middle.y > size.height + radius) {
      continue;
    }
    let area = areas.get(village.guildId);
    if (area === undefined) {
      area = new Path2D();
      areas.set(village.guildId, area);
    }
    area.moveTo(middle.x + radius, middle.y); // no stray line from the previous disc
    area.arc(middle.x, middle.y, radius, 0, Math.PI * 2);
  }
  const origin = toScreen(overview, size, { x: 0, y: 0 });
  ctx.save();
  ctx.beginPath();
  ctx.rect(origin.x, origin.y, mapSize * overview.tile, mapSize * overview.tile);
  ctx.clip();
  ctx.globalAlpha = 0.22;
  for (const [guildId, area] of areas) {
    ctx.fillStyle = guildColor(guildId, scene.theme);
    ctx.fill(area);
  }
  ctx.restore();
}

/** What the detailed map shows: a lightly filled rectangle with a gold line on a dark one. */
function drawViewRect(ctx: CanvasRenderingContext2D, rect: Rect, colors: MapColors): void {
  ctx.globalAlpha = 0.18;
  ctx.fillStyle = colors.marker;
  ctx.fillRect(rect.left, rect.top, rect.width, rect.height);
  ctx.globalAlpha = 1;
  ctx.lineWidth = 3.5;
  ctx.strokeStyle = colors.outline;
  ctx.strokeRect(rect.left, rect.top, rect.width, rect.height);
  ctx.lineWidth = 1.5;
  ctx.strokeStyle = colors.marker;
  ctx.strokeRect(rect.left, rect.top, rect.width, rect.height);
}

/** A few coordinates along the top and the left edge, written on the map with a halo. */
function drawLabels(
  ctx: CanvasRenderingContext2D,
  scene: OverviewScene,
  minX: number,
  maxX: number,
  minY: number,
  maxY: number,
): void {
  const { overview, size, colors } = scene;
  const step = overviewLabelStep(overview.tile);
  // along the map's top and left edge, or the canvas edge when the map goes on beyond it
  const origin = toScreen(overview, size, { x: 0, y: 0 });
  const labelTop = Math.max(0, origin.y) + 3;
  const labelLeft = Math.max(0, origin.x) + 3;
  ctx.font = `600 10px ${FONT}`;
  ctx.lineJoin = 'round';
  ctx.lineWidth = 3;
  ctx.strokeStyle = colors.labelHalo;
  ctx.fillStyle = colors.label;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'top';
  for (const x of axisValues(Math.max(step, minX), maxX, step)) {
    const screenX = toScreen(overview, size, { x, y: 0 }).x;
    if (screenX > labelLeft + 15 && screenX < size.width - 10) {
      ctx.strokeText(String(x), screenX, labelTop);
      ctx.fillText(String(x), screenX, labelTop);
    }
  }
  ctx.textAlign = 'left';
  ctx.textBaseline = 'middle';
  for (const y of axisValues(Math.max(step, minY), maxY, step)) {
    const screenY = toScreen(overview, size, { x: 0, y }).y;
    if (screenY > labelTop + 13 && screenY < size.height - 8) {
      ctx.strokeText(String(y), labelLeft, screenY);
      ctx.fillText(String(y), labelLeft, screenY);
    }
  }
}

/** Lines one pixel wide look sharp on half pixels. */
function crisp(value: number): number {
  return Math.round(value) + 0.5;
}
