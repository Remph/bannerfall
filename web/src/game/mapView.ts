import type { Coords } from './coords';

/*
 * The map's camera and the maths around it (05-frontend §6). Pure functions: no React, no canvas.
 *
 * Map units are fields: field (x|y) covers the square x … x+1 × y … y+1, so its middle is
 * (x + 0.5 | y + 0.5). Screen units are CSS pixels from the top left corner of the canvas.
 */

export const TILE_DEFAULT = 48;
/** Far out enough to see a whole 100 × 100 map on a phone (05-frontend §6, asked for in the M6 review). */
export const TILE_MIN = 8;
/** Below this field size villages are drawn as simple squares instead of houses. */
export const HOUSES_MIN_TILE = 20;
export const TILE_MAX = 64;
/** A link to a village opens the map at least this close (1.6.2): its house and name are easy to see. */
export const TILE_FOCUS = 56;
/** One press of + / − changes the size of a field by this factor. */
export const ZOOM_STEP = 1.25;
/** The server is asked for 20 × 20 fields at a time (query key ['mapChunk', cx, cy]). */
export const CHUNK_SIZE = 20;
/** Axis labels and the stronger grid lines: every 5 fields. */
export const AXIS_STEP = 5;
/** Arrow keys move the map by this many fields (with Shift: five times as far). */
export const KEY_PAN_FIELDS = 2;

/**
 * Village sizes on the map by points: below 280 small, below 1,600 medium, else large. A new
 * village has 39 points, a fully built one about 29,100 (02-game-rules §4.2). Until 1.2.2 the
 * limits were 300 and 2,000 on the old points growth of 1.2; the new ones are the same build
 * steps on the growth of 1.15, so a village changes size at the same point as before.
 */
const MEDIUM_VILLAGE_POINTS = 280;
const LARGE_VILLAGE_POINTS = 1600;

export interface Point {
  x: number;
  y: number;
}

export interface Size {
  width: number;
  height: number;
}

/** What the map shows: the map point in the middle of the canvas, and how many pixels one field is wide. */
export interface Camera {
  x: number;
  y: number;
  tile: number;
}

/** Whole fields, both ends included. */
export interface FieldRange {
  minX: number;
  maxX: number;
  minY: number;
  maxY: number;
}

/** A 20 × 20 block of fields: chunk (1|2) holds x 20 … 39, y 40 … 59. */
export interface Chunk {
  cx: number;
  cy: number;
}

/** `mate` (1.6.3): a village of another player in the viewer's own guild. */
export type VillageKind = 'own' | 'mate' | 'player' | 'npc' | 'barbarian';

export function clampTile(tile: number): number {
  return Math.min(TILE_MAX, Math.max(TILE_MIN, tile));
}

/** The zoom a map opened by a village link starts with: the player's own, if that is closer already. */
export function focusTile(stored: number): number {
  return clampTile(Math.max(stored, TILE_FOCUS));
}

/** The middle of the canvas on the middle of a field. */
export function cameraOn(field: Coords, tile: number): Camera {
  return { x: field.x + 0.5, y: field.y + 0.5, tile: clampTile(tile) };
}

/** Keeps the middle of the canvas on the map: the map edge can come to the middle of the screen, not further. */
export function clampCamera(camera: Camera, mapSize: number): Camera {
  return { x: clamp(camera.x, 0, mapSize), y: clamp(camera.y, 0, mapSize), tile: clampTile(camera.tile) };
}

/** Screen point → map point (fractional fields). */
export function toMap(camera: Camera, size: Size, point: Point): Point {
  return {
    x: camera.x + (point.x - size.width / 2) / camera.tile,
    y: camera.y + (point.y - size.height / 2) / camera.tile,
  };
}

/** Map point → screen point. */
export function toScreen(camera: Camera, size: Size, mapPoint: Point): Point {
  return {
    x: (mapPoint.x - camera.x) * camera.tile + size.width / 2,
    y: (mapPoint.y - camera.y) * camera.tile + size.height / 2,
  };
}

/** The field under a screen point (may be off the map). */
export function fieldAt(camera: Camera, size: Size, point: Point): Coords {
  const mapPoint = toMap(camera, size, point);
  return { x: Math.floor(mapPoint.x), y: Math.floor(mapPoint.y) };
}

/** The field in the middle of the canvas, on the map. */
export function middleField(camera: Camera, mapSize: number): Coords {
  return {
    x: clamp(Math.floor(camera.x), 0, mapSize - 1),
    y: clamp(Math.floor(camera.y), 0, mapSize - 1),
  };
}

/** Dragging by (dx, dy) pixels: the map follows the finger, so the camera moves the other way. */
export function panBy(camera: Camera, dx: number, dy: number, mapSize: number): Camera {
  return clampCamera({ ...camera, x: camera.x - dx / camera.tile, y: camera.y - dy / camera.tile }, mapSize);
}

/** Zooms to `tile` and keeps the map point under `point` where it is (wheel and pinch zoom around the pointer). */
export function zoomAt(camera: Camera, size: Size, point: Point, tile: number, mapSize: number): Camera {
  const anchor = toMap(camera, size, point);
  const newTile = clampTile(tile);
  return clampCamera(
    {
      x: anchor.x - (point.x - size.width / 2) / newTile,
      y: anchor.y - (point.y - size.height / 2) / newTile,
      tile: newTile,
    },
    mapSize,
  );
}

/**
 * Two fingers moved from `from` to `to` (a few pixels, one pointer event): the map moves with
 * their midpoint and zooms by the change of their distance.
 */
export function pinch(
  camera: Camera,
  size: Size,
  from: readonly [Point, Point],
  to: readonly [Point, Point],
  mapSize: number,
): Camera {
  const fromMid = midpoint(from);
  const toMid = midpoint(to);
  const panned = panBy(camera, toMid.x - fromMid.x, toMid.y - fromMid.y, mapSize);
  const fromDistance = pointDistance(from);
  if (fromDistance < 1) {
    return panned; // both fingers on (almost) the same spot: no sensible zoom factor
  }
  return zoomAt(panned, size, toMid, camera.tile * (pointDistance(to) / fromDistance), mapSize);
}

/** The part of the canvas a panel covers: everything below `top` and left of `right` (CSS px). */
export interface Cover {
  top: number;
  right: number;
}

/**
 * Moves the map up if `field` is hidden under a panel, into the middle of the free part above it.
 * Returns the same camera object if the field is visible (React then skips the re-render).
 */
export function revealField(camera: Camera, size: Size, field: Coords, cover: Cover, mapSize: number): Camera {
  const middle = toScreen(camera, size, { x: field.x + 0.5, y: field.y + 0.5 });
  const half = camera.tile / 2;
  if (middle.x - half >= cover.right || middle.y + half <= cover.top) {
    return camera;
  }
  return clampCamera({ ...camera, y: camera.y + (middle.y - cover.top / 2) / camera.tile }, mapSize);
}

/** The size of a field after `steps` presses of + (positive) or − (negative). */
export function steppedTile(tile: number, steps: number): number {
  return clampTile(tile * ZOOM_STEP ** steps);
}

/** The size of a field after a wheel or trackpad event: one ZOOM_STEP per notch of a mouse wheel (≈ 100 px). */
export function wheelTile(tile: number, deltaPixels: number): number {
  return clampTile(tile * ZOOM_STEP ** (-deltaPixels / 100));
}

/** What a wheel event does: zoom by `pixels` (positive = out), or pan the map by (dx, dy) pixels. */
export type WheelAction = { kind: 'zoom'; pixels: number } | { kind: 'pan'; dx: number; dy: number };

/**
 * Mouse wheel and trackpad. A plain vertical wheel zooms (a mouse can't do anything else); Ctrl +
 * wheel – which is also how browsers report a trackpad pinch – zooms; a scroll with a sideways
 * part (two fingers on a trackpad) pans. undefined = nothing to do.
 */
export function wheelAction(event: {
  deltaX: number;
  deltaY: number;
  deltaMode: number;
  ctrlKey: boolean;
}): WheelAction | undefined {
  // deltaMode: 0 = pixels, 1 = lines (Firefox mouse wheels), 2 = pages (counted as three notches)
  const unit = event.deltaMode === 1 ? 40 : event.deltaMode === 2 ? 300 : 1;
  const dx = event.deltaX * unit;
  const dy = event.deltaY * unit;
  if (event.ctrlKey || dx === 0) {
    return dy === 0 ? undefined : { kind: 'zoom', pixels: dy };
  }
  return { kind: 'pan', dx: -dx, dy: -dy }; // scrolling right shows what is right: the map moves left
}

/** The fields at least partly on screen, cut at the map edges; undefined if no part of the map is visible. */
export function visibleFields(camera: Camera, size: Size, mapSize: number): FieldRange | undefined {
  const topLeft = toMap(camera, size, { x: 0, y: 0 });
  const bottomRight = toMap(camera, size, { x: size.width, y: size.height });
  const range = {
    minX: Math.max(0, Math.floor(topLeft.x)),
    maxX: Math.min(mapSize - 1, Math.ceil(bottomRight.x) - 1),
    minY: Math.max(0, Math.floor(topLeft.y)),
    maxY: Math.min(mapSize - 1, Math.ceil(bottomRight.y) - 1),
  };
  return range.minX > range.maxX || range.minY > range.maxY ? undefined : range;
}

/** The chunks covering a range of fields, row by row. */
export function chunksFor(range: FieldRange): Chunk[] {
  const chunks: Chunk[] = [];
  for (let cy = Math.floor(range.minY / CHUNK_SIZE); cy <= Math.floor(range.maxY / CHUNK_SIZE); cy++) {
    for (let cx = Math.floor(range.minX / CHUNK_SIZE); cx <= Math.floor(range.maxX / CHUNK_SIZE); cx++) {
      chunks.push({ cx, cy });
    }
  }
  return chunks;
}

/** The query parameters of GET /api/map for a chunk. */
export function chunkArea(chunk: Chunk): { x: number; y: number; w: number; h: number } {
  return { x: chunk.cx * CHUNK_SIZE, y: chunk.cy * CHUNK_SIZE, w: CHUNK_SIZE, h: CHUNK_SIZE };
}

/** The coordinates that get an axis label: the multiples of `step` (AXIS_STEP unless given) from min to max. */
export function axisValues(min: number, max: number, step = AXIS_STEP): number[] {
  const values: number[] = [];
  for (let value = Math.ceil(min / step) * step; value <= max; value += step) {
    values.push(value);
  }
  return values;
}

/** Who a village belongs to, from the player's point of view (decides its colour). */
export function villageKind(
  village: { playerId: number | null; npc: boolean; guildId?: number | null },
  ownPlayerId: number,
  ownGuildId: number | null = null,
): VillageKind {
  if (village.playerId === null) {
    return 'barbarian';
  }
  if (village.playerId === ownPlayerId) {
    return 'own';
  }
  if (village.npc) {
    return 'npc';
  }
  return ownGuildId !== null && village.guildId === ownGuildId ? 'mate' : 'player';
}

/**
 * The villages that get a short label at medium zoom (v1.3): the player's own villages (their name)
 * and foreign ones (their owner's name). A foreign player gets one label per 5 × 5 block, under his
 * biggest village there, so a cluster of one player's villages isn't labelled over and over.
 * Barbarians and the villages in `skip` (the picked ones, which get their full name) get none.
 */
export function mediumLabels<
  T extends { id: number; x: number; y: number; playerId: number | null; playerName: string | null; points: number },
>(villages: readonly T[], ownPlayerId: number, skip: ReadonlySet<number>): Array<{ village: T; own: boolean }> {
  const own: T[] = [];
  const biggest = new Map<string, T>();
  for (const village of villages) {
    if (village.playerId === null || village.playerName === null || skip.has(village.id)) {
      continue;
    }
    if (village.playerId === ownPlayerId) {
      own.push(village);
      continue;
    }
    const key = `${village.playerId}:${Math.floor(village.x / AXIS_STEP)}:${Math.floor(village.y / AXIS_STEP)}`;
    const best = biggest.get(key);
    if (
      best === undefined ||
      village.points > best.points ||
      (village.points === best.points && village.id < best.id)
    ) {
      biggest.set(key, village);
    }
  }
  return [
    ...own.map((village) => ({ village, own: true })),
    ...[...biggest.values()].map((village) => ({ village, own: false })),
  ];
}

/** 0 = small, 1 = medium, 2 = large. */
export function villageSize(points: number): 0 | 1 | 2 {
  if (points < MEDIUM_VILLAGE_POINTS) {
    return 0;
  }
  return points < LARGE_VILLAGE_POINTS ? 1 : 2;
}

/**
 * How far from a village's middle a tap or click still hits it, in fields: the whole field, but at
 * least 10 px for a mouse and a fingertip (24 px) for touch – far out, that is more than a field.
 * villageNear takes the nearest village, so a big radius never picks the wrong one of two.
 */
export function hitRadius(tile: number, pointerType: string): number {
  const field = Math.SQRT1_2; // middle to corner
  return Math.max(field, (pointerType === 'mouse' ? 10 : 24) / tile);
}

/** The village whose middle is closest to `mapPoint`, if it is at most `maxDistance` fields away. */
export function villageNear<T extends Coords>(
  villages: readonly T[],
  mapPoint: Point,
  maxDistance: number,
): T | undefined {
  let nearest: T | undefined;
  let nearestDistance = maxDistance;
  for (const village of villages) {
    const distance = Math.hypot(village.x + 0.5 - mapPoint.x, village.y + 0.5 - mapPoint.y);
    if (distance <= nearestDistance) {
      nearest = village;
      nearestDistance = distance;
    }
  }
  return nearest;
}

/*
 * The overview map (M11b, 05-frontend §6): a broad area around the current village, one small
 * square per village. It uses the same Camera as the detailed map – its `tile` is the scale, the
 * pixels of one field – so toMap / toScreen / visibleFields work for it too.
 */

/** The overview shows this many fields across its shorter side, or the whole map while that is smaller. */
export const OVERVIEW_FIELDS = 100;
/** Light grid lines on the overview every 10 fields. */
export const OVERVIEW_GRID = 10;
/** Grid labels on the overview are at least this far apart (CSS px), so a few multiples of 10 get one. */
const OVERVIEW_LABEL_MIN_GAP = 40;
/** The overview's arrow buttons and arrow keys move it by this many fields. */
export const OVERVIEW_STEP = 20;

/** Pixels per field: OVERVIEW_FIELDS (or the whole map) fit across the shorter side. */
export function overviewScale(size: Size, mapSize: number): number {
  return Math.min(size.width, size.height) / Math.min(OVERVIEW_FIELDS, mapSize);
}

/**
 * The overview centred on `centre`, as far as the map edges allow: its area stays on the map. Along
 * a side on which the whole map fits, the map sits in the middle.
 */
export function overviewView(centre: Point, size: Size, mapSize: number): Camera {
  const scale = overviewScale(size, mapSize);
  return {
    x: clampOverviewAxis(centre.x, size.width / scale, mapSize),
    y: clampOverviewAxis(centre.y, size.height / scale, mapSize),
    tile: scale,
  };
}

/** The overview's middle after moving it by (dx, dy) fields, already held at the map edges. */
export function moveOverview(centre: Point, dx: number, dy: number, size: Size, mapSize: number): Point {
  const view = overviewView(centre, size, mapSize);
  const moved = overviewView({ x: view.x + dx, y: view.y + dy }, size, mapSize);
  return { x: moved.x, y: moved.y };
}

/** Whether a map point lies in the part of the map the overview shows. */
export function overviewShows(overview: Camera, size: Size, mapPoint: Point): boolean {
  const screen = toScreen(overview, size, mapPoint);
  return screen.x >= 0 && screen.x <= size.width && screen.y >= 0 && screen.y <= size.height;
}

/** A rectangle in CSS pixels. */
export interface Rect {
  left: number;
  top: number;
  width: number;
  height: number;
}

/** Where the part the detailed map shows (`camera` on a canvas of `size`) lies on the overview. */
export function viewRect(camera: Camera, size: Size, overview: Camera, overviewSize: Size): Rect {
  const topLeft = toScreen(overview, overviewSize, toMap(camera, size, { x: 0, y: 0 }));
  const scale = overview.tile / camera.tile;
  return { left: topLeft.x, top: topLeft.y, width: size.width * scale, height: size.height * scale };
}

/** Whether `point` is on the rectangle or at most `slop` px outside it (a finger needs more room). */
export function onRect(rect: Rect, point: Point, slop: number): boolean {
  return (
    point.x >= rect.left - slop &&
    point.x <= rect.left + rect.width + slop &&
    point.y >= rect.top - slop &&
    point.y <= rect.top + rect.height + slop
  );
}

/** A click or tap on the overview at `mapPoint` (toMap with the overview's camera): the detailed map's middle goes there, the zoom stays. */
export function cameraAt(camera: Camera, mapPoint: Point, mapSize: number): Camera {
  return clampCamera({ ...camera, x: mapPoint.x, y: mapPoint.y }, mapSize);
}

/**
 * Dragging the view rectangle on the overview by (dx, dy) pixels: the detailed map moves the same
 * way (unlike panBy, which drags the map itself and so moves the camera the other way).
 */
export function dragViewRect(camera: Camera, dx: number, dy: number, overviewTile: number, mapSize: number): Camera {
  return clampCamera({ ...camera, x: camera.x + dx / overviewTile, y: camera.y + dy / overviewTile }, mapSize);
}

/** How many fields apart the overview's grid labels are: a multiple of OVERVIEW_GRID, at least 40 px. */
export function overviewLabelStep(scale: number): number {
  return OVERVIEW_GRID * Math.max(1, Math.ceil(OVERVIEW_LABEL_MIN_GAP / (OVERVIEW_GRID * scale)));
}

function clampOverviewAxis(value: number, extent: number, mapSize: number): number {
  return extent >= mapSize ? mapSize / 2 : clamp(value, extent / 2, mapSize - extent / 2);
}

function midpoint([a, b]: readonly [Point, Point]): Point {
  return { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 };
}

function pointDistance([a, b]: readonly [Point, Point]): number {
  return Math.hypot(a.x - b.x, a.y - b.y);
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

/**
 * The part of the segment `from`–`to` that lies on `rect`, and how far along the segment it starts
 * (px, so a dashed line keeps its pattern), or undefined when none does (Liang–Barsky).
 */
export function clipSegment(
  from: Point,
  to: Point,
  rect: Rect,
): { from: Point; to: Point; skipped: number } | undefined {
  const dx = to.x - from.x;
  const dy = to.y - from.y;
  let start = 0;
  let end = 1;
  const sides = [
    [-dx, from.x - rect.left],
    [dx, rect.left + rect.width - from.x],
    [-dy, from.y - rect.top],
    [dy, rect.top + rect.height - from.y],
  ] as const;
  for (const [p, q] of sides) {
    if (p === 0) {
      if (q < 0) {
        return undefined; // parallel to this side and outside it
      }
      continue;
    }
    const t = q / p;
    if (p < 0) {
      start = Math.max(start, t);
    } else {
      end = Math.min(end, t);
    }
  }
  if (start > end) {
    return undefined;
  }
  return {
    from: { x: from.x + dx * start, y: from.y + dy * start },
    to: { x: from.x + dx * end, y: from.y + dy * end },
    skipped: Math.hypot(dx, dy) * start,
  };
}

/**
 * The dashes of a line `length` px long whose pattern (`on` px drawn, then `off` px gap, starting
 * with a dash) began `offset` px before it, as `clipSegment` skipped them: each dash's start and end
 * along the line, cut to 0..`length`. With `on` 0 every dash is a dot (start = end). The map works
 * the dashes out itself instead of using setLineDash, so no sliver of a dash a fraction of a pixel
 * long reaches the GPU (1.4.1).
 */
export function dashRuns(length: number, offset: number, on: number, off: number): [number, number][] {
  const period = on + off;
  if (!(period > 0) || !(length >= 0)) {
    return [];
  }
  const runs: [number, number][] = [];
  for (let start = Math.max(0, Math.ceil((offset - on) / period)) * period - offset; start <= length; start += period) {
    runs.push([Math.max(0, start), Math.min(length, start + on)]);
  }
  return runs;
}

/** `rect` grown by `margin` on every side. */
export function growRect(rect: Rect, margin: number): Rect {
  return {
    left: rect.left - margin,
    top: rect.top - margin,
    width: rect.width + 2 * margin,
    height: rect.height + 2 * margin,
  };
}

/** The part of `rect` that lies on `bounds` (a width or height of 0 when they don't meet). */
export function intersectRect(rect: Rect, bounds: Rect): Rect {
  const left = Math.max(rect.left, bounds.left);
  const top = Math.max(rect.top, bounds.top);
  const right = Math.min(rect.left + rect.width, bounds.left + bounds.width);
  const bottom = Math.min(rect.top + rect.height, bounds.top + bounds.height);
  return { left, top, width: Math.max(0, right - left), height: Math.max(0, bottom - top) };
}
