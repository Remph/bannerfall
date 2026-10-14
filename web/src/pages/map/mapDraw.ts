import type { MapMovement, MapVillage } from '../../api/queries/map';
import type { Coords } from '../../game/coords';
import {
  MARKER_PATHS,
  markerCount,
  movementMarkers,
  returnMarker,
  type Marker,
  type MarkerKind,
} from '../../game/mapMovements';
import {
  AXIS_STEP,
  axisValues,
  HOUSES_MIN_TILE,
  toScreen,
  villageKind,
  villageSize,
  visibleFields,
  type Camera,
  type FieldRange,
  type Point,
  type Size,
  type VillageKind,
} from '../../game/mapView';

/*
 * Draws the map onto a canvas (05-frontend §6). Plain canvas calls, no React: MapPage calls
 * drawMap() after every change of the camera, the data or the theme. Sizes are CSS pixels; the
 * caller has already scaled the context for the device pixel ratio.
 */

/** Height of the axis band at the top and width of the one on the left (CSS px); overlays keep clear of them. */
export const AXIS_TOP = 20;
export const AXIS_LEFT = 28;

/** From this field size on, village names are written below the villages. */
const LABEL_MIN_TILE = 44;
const LABEL_MAX_CHARS = 16;
/** Radius of the ground patch under a small, medium and large village, in fields. */
const PATCH_RADIUS = [0.3, 0.38, 0.46] as const;
/** Far out: side of the square of a small, medium and large village, in fields. */
const SQUARE_SIDE = [0.55, 0.7, 0.85] as const;
/** Below this field size only the lines every 5 fields are drawn (the others would be a blur). */
const THIN_GRID_MIN_TILE = 16;
/** Selection rings never get smaller than this (half their side, px), so they stay visible far out. */
const RING_MIN_HALF = 7;
const FONT = '"Alegreya Sans", system-ui, sans-serif';
/** Movement badges (M26): their radius is this share of a field, within these bounds (CSS px). */
const BADGE_SHARE = 0.2;
const BADGE_MIN_RADIUS = 5;
const BADGE_MAX_RADIUS = 12;
/** Smaller badges have no number: it would be unreadable. */
const BADGE_COUNT_MIN_RADIUS = 7;

/** The colours of the current theme: the --map-* tokens in tokens.css. */
export interface MapColors {
  grass: string;
  grassAlt: string;
  grid: string;
  gridStrong: string;
  outside: string;
  edge: string;
  kinds: Record<VillageKind, string>;
  outline: string;
  marker: string;
  label: string;
  labelHalo: string;
  axis: string;
  axisText: string;
  /** Movement badges (M26, M36) by kind, and the colour of the swords and arrow on them. */
  badges: Record<MarkerKind, string>;
  badgeInk: string;
  badgeInkNoble: string;
}

export function readMapColors(element: Element): MapColors {
  const style = getComputedStyle(element);
  const color = (name: string) => style.getPropertyValue(`--map-${name}`).trim();
  return {
    grass: color('grass'),
    grassAlt: color('grass-alt'),
    grid: color('grid'),
    gridStrong: color('grid-strong'),
    outside: color('outside'),
    edge: color('edge'),
    kinds: { own: color('own'), player: color('player'), npc: color('npc'), barbarian: color('barbarian') },
    outline: color('outline'),
    marker: color('marker'),
    label: color('label'),
    labelHalo: color('label-halo'),
    axis: color('axis'),
    axisText: color('axis-text'),
    badges: { attack: color('attack'), noble: color('noble'), support: color('support'), return: color('return') },
    badgeInk: color('badge-ink'),
    badgeInkNoble: color('badge-ink-noble'),
  };
}

/** Everything one picture of the map depends on. */
export interface MapScene {
  camera: Camera;
  size: Size;
  mapSize: number;
  villages: readonly MapVillage[];
  /** The player's own movements by target village (M26, returns M36); empty while the markers are switched off. */
  movements: readonly MapMovement[];
  ownPlayerId: number;
  selectedId: number | null;
  hoveredId: number | null;
  /** A field to point out without a village on it (the target of "go to"). */
  marked: Coords | null;
  /** Mark the middle of the canvas (keyboard focus: Enter selects the village there). */
  crosshair: boolean;
  colors: MapColors;
}

export function drawMap(ctx: CanvasRenderingContext2D, scene: MapScene): void {
  const { size, colors } = scene;
  ctx.fillStyle = colors.outside;
  ctx.fillRect(0, 0, size.width, size.height);
  const range = visibleFields(scene.camera, size, scene.mapSize);
  if (range !== undefined) {
    drawGround(ctx, scene, range);
    if (scene.marked !== null) {
      drawMarkedField(ctx, scene, scene.marked);
    }
    drawVillages(ctx, scene, range);
    drawMovementMarkers(ctx, scene, range);
  }
  if (scene.crosshair) {
    drawCrosshair(ctx, { x: size.width / 2, y: size.height / 2 }, colors);
  }
  drawAxes(ctx, scene, range);
}

/** Grass in 5 × 5 blocks (every other one a little darker), grid lines, the map edge. */
function drawGround(ctx: CanvasRenderingContext2D, scene: MapScene, range: FieldRange): void {
  const { camera, size, mapSize, colors } = scene;
  const tile = camera.tile;
  const origin = toScreen(camera, size, { x: 0, y: 0 }); // where the map's corner (0|0) is on screen
  const left = (x: number) => origin.x + x * tile;
  const top = (y: number) => origin.y + y * tile;

  for (let blockY = Math.floor(range.minY / AXIS_STEP); blockY <= Math.floor(range.maxY / AXIS_STEP); blockY++) {
    for (let blockX = Math.floor(range.minX / AXIS_STEP); blockX <= Math.floor(range.maxX / AXIS_STEP); blockX++) {
      const x = blockX * AXIS_STEP;
      const y = blockY * AXIS_STEP;
      ctx.fillStyle = (blockX + blockY) % 2 === 0 ? colors.grass : colors.grassAlt;
      ctx.fillRect(
        left(x),
        top(y),
        (Math.min(x + AXIS_STEP, mapSize) - x) * tile,
        (Math.min(y + AXIS_STEP, mapSize) - y) * tile,
      );
    }
  }

  // Lines between the fields, stronger every 5 fields. Two paths, so it's two strokes, not one per line.
  const thin = new Path2D();
  const strong = new Path2D();
  const yFrom = top(range.minY);
  const yTo = top(range.maxY + 1);
  const xFrom = left(range.minX);
  const xTo = left(range.maxX + 1);
  const showThin = tile >= THIN_GRID_MIN_TILE;
  for (let x = Math.max(range.minX, 1); x <= Math.min(range.maxX + 1, mapSize - 1); x++) {
    if (!showThin && x % AXIS_STEP !== 0) {
      continue;
    }
    const path = x % AXIS_STEP === 0 ? strong : thin;
    const screenX = crisp(left(x));
    path.moveTo(screenX, yFrom);
    path.lineTo(screenX, yTo);
  }
  for (let y = Math.max(range.minY, 1); y <= Math.min(range.maxY + 1, mapSize - 1); y++) {
    if (!showThin && y % AXIS_STEP !== 0) {
      continue;
    }
    const path = y % AXIS_STEP === 0 ? strong : thin;
    const screenY = crisp(top(y));
    path.moveTo(xFrom, screenY);
    path.lineTo(xTo, screenY);
  }
  ctx.lineWidth = 1;
  ctx.strokeStyle = colors.grid;
  ctx.stroke(thin);
  ctx.strokeStyle = colors.gridStrong;
  ctx.stroke(strong);

  ctx.lineWidth = 2;
  ctx.strokeStyle = colors.edge;
  ctx.strokeRect(left(0), top(0), mapSize * tile, mapSize * tile);
}

function drawVillages(ctx: CanvasRenderingContext2D, scene: MapScene, range: FieldRange): void {
  const { camera, size, colors } = scene;
  const tile = camera.tile;
  const onScreen = scene.villages.filter(
    (village) =>
      village.x >= range.minX - 1 &&
      village.x <= range.maxX + 1 &&
      village.y >= range.minY - 1 &&
      village.y <= range.maxY + 1,
  );
  for (const village of onScreen) {
    const middle = toScreen(camera, size, { x: village.x + 0.5, y: village.y + 0.5 });
    const kind = villageKind(village, scene.ownPlayerId);
    if (village.id === scene.selectedId) {
      drawFieldRing(ctx, middle, tile, colors, 'selected');
    } else if (village.id === scene.hoveredId) {
      drawFieldRing(ctx, middle, tile, colors, 'hovered');
    }
    if (tile < HOUSES_MIN_TILE) {
      // far out: a square; NPC lords get a gold edge instead of the pennant
      const edge = kind === 'npc' ? colors.marker : colors.outline;
      drawVillageSquare(ctx, middle, tile, villageSize(village.points), colors.kinds[kind], edge);
    } else {
      drawVillage(ctx, middle, tile, villageSize(village.points), colors.kinds[kind], colors.outline);
      if (kind === 'npc') {
        drawPennant(ctx, middle, tile, colors);
      }
    }
  }
  // Names last, so no village is drawn over them.
  if (tile >= LABEL_MIN_TILE) {
    ctx.font = `600 ${Math.round(Math.min(13, tile * 0.22))}px ${FONT}`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'top';
    ctx.lineJoin = 'round';
    ctx.lineWidth = 3;
    for (const village of onScreen) {
      if (village.name === null) {
        continue; // barbarians have no name
      }
      const middle = toScreen(camera, size, { x: village.x + 0.5, y: village.y + 0.5 });
      const text = shorten(village.name);
      const y = middle.y + tile * 0.36;
      ctx.strokeStyle = colors.labelHalo;
      ctx.strokeText(text, middle.x, y);
      ctx.fillStyle = colors.label;
      ctx.fillText(text, middle.x, y);
    }
  }
}

/** One Path2D per badge kind, built when first drawn (the map redraws on every pan frame). */
const markerPaths = new Map<MarkerKind, Path2D>();

function markerPath(kind: MarkerKind): Path2D {
  let path = markerPaths.get(kind);
  if (path === undefined) {
    path = new Path2D(MARKER_PATHS[kind]);
    markerPaths.set(kind, path);
  }
  return path;
}

/**
 * The movement badges (M26) in the top left corner of the target's field, beside each other:
 * crossed swords for attacks (red, yellow with own nobles), a sword for supports (green), each with
 * its number if more than one is on the way. The player's own troops walking home (M36) get a blue
 * badge with the rally point's arrow in the top right corner, so it never crowds an attack badge on
 * a village that is attacked while its troops come back. Far out only coloured dots. Drawn from the
 * movement's own coordinates, so a marker shows even before the village's chunk has loaded.
 */
function drawMovementMarkers(ctx: CanvasRenderingContext2D, scene: MapScene, range: FieldRange): void {
  const { camera, size, colors } = scene;
  const tile = camera.tile;
  const far = tile < HOUSES_MIN_TILE;
  // far out a dot bigger than the badge share, so it stands out against the village squares
  const radius = far
    ? Math.max(3, tile * 0.3)
    : Math.min(BADGE_MAX_RADIUS, Math.max(BADGE_MIN_RADIUS, tile * BADGE_SHARE));
  // numbers in a second pass, so the next badge doesn't cover them
  const counts: { text: string; x: number; y: number; align: CanvasTextAlign }[] = [];
  // set before the badges: where the return badge goes depends on how wide the numbers are
  ctx.font = `700 ${Math.max(12, Math.round(radius * 1.3))}px ${FONT}`;
  const countWidth = (marker: Marker) => {
    const text = markerCount(marker.count);
    return far || text === '' || radius < BADGE_COUNT_MIN_RADIUS ? 0 : ctx.measureText(text).width;
  };
  // a badge's number sits at its bottom right; the right-hand badge's at its bottom left, inside the field
  const drawBadge = (marker: Marker, centre: Point, numberLeft = false) => {
    ctx.beginPath();
    ctx.arc(centre.x, centre.y, radius, 0, Math.PI * 2);
    ctx.fillStyle = colors.badges[marker.kind];
    ctx.fill();
    ctx.lineWidth = far ? 1 : 1.5;
    ctx.strokeStyle = colors.outline;
    ctx.stroke();
    if (far) {
      return;
    }
    // the swords or the arrow: the 24 × 24 icon path scaled into the circle
    const scale = (radius * 1.3) / 24;
    ctx.save();
    ctx.translate(centre.x - 12 * scale, centre.y - 12 * scale);
    ctx.scale(scale, scale);
    ctx.lineWidth = 2.6;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.strokeStyle = marker.kind === 'noble' ? colors.badgeInkNoble : colors.badgeInk;
    ctx.stroke(markerPath(marker.kind));
    ctx.restore();
    const text = markerCount(marker.count);
    if (text !== '' && radius >= BADGE_COUNT_MIN_RADIUS) {
      const x = numberLeft ? centre.x - radius * 0.6 : centre.x + radius * 0.6;
      counts.push({ text, x, y: centre.y + radius * 0.95, align: numberLeft ? 'right' : 'left' });
    }
  };
  for (const movement of scene.movements) {
    const outside =
      movement.x < range.minX - 1 ||
      movement.x > range.maxX + 1 ||
      movement.y < range.minY - 1 ||
      movement.y > range.maxY + 1;
    if (outside) {
      continue;
    }
    const corner = toScreen(camera, size, { x: movement.x, y: movement.y });
    const markers = movementMarkers(movement);
    markers.forEach((marker, index) => {
      drawBadge(marker, { x: corner.x + radius + 1 + index * (radius * 2 + 1), y: corner.y + radius + 1 });
    });
    const returning = returnMarker(movement);
    if (returning !== undefined) {
      // The return badge steps one row down instead of crowding the row on the left: when attacks
      // and supports together reach it (two badges are more than half a field wide), when a number
      // would touch the other side's badge (the numbers' 3 px halo is the gap), and whenever both
      // the left row's last badge and the return badge carry a number: side by side the two face
      // each other and "2 2" reads as 22, at every zoom.
      const right = corner.x + tile - radius - 1;
      const last = markers.at(-1);
      const lastX = corner.x + radius + 1 + (markers.length - 1) * (radius * 2 + 1);
      const lastWidth = last === undefined ? 0 : countWidth(last);
      const returnWidth = countWidth(returning);
      const rowEnd = last === undefined ? corner.x : Math.max(lastX + radius, lastX + radius * 0.6 + lastWidth);
      const returnStart = Math.min(right - radius, right - radius * 0.6 - returnWidth);
      const row = (lastWidth > 0 && returnWidth > 0) || rowEnd + 3 > returnStart ? 1 : 0;
      drawBadge(returning, { x: right, y: corner.y + radius + 1 + row * (radius * 2 + 1) }, true);
    }
  }
  if (counts.length > 0) {
    // the font is still the one set above
    ctx.textBaseline = 'middle';
    ctx.lineJoin = 'round';
    ctx.lineWidth = 3;
    for (const count of counts) {
      ctx.textAlign = count.align;
      ctx.strokeStyle = colors.labelHalo;
      ctx.strokeText(count.text, count.x, count.y);
      ctx.fillStyle = colors.label;
      ctx.fillText(count.text, count.x, count.y);
    }
  }
}

/**
 * A village: a ground patch in the owner's colour and one, two or three houses (small, medium,
 * large village). `middle` is the middle of the field.
 */
function drawVillage(
  ctx: CanvasRenderingContext2D,
  middle: Point,
  tile: number,
  size: 0 | 1 | 2,
  color: string,
  outline: string,
): void {
  const { x, y } = middle;
  const radius = tile * PATCH_RADIUS[size];
  ctx.globalAlpha = 0.3;
  ctx.fillStyle = color;
  ctx.beginPath();
  ctx.ellipse(x, y + tile * 0.2, radius, radius * 0.5, 0, 0, Math.PI * 2);
  ctx.fill();
  ctx.globalAlpha = 1;

  const line = Math.max(1, tile / 32);
  if (size === 0) {
    drawHouse(ctx, x, y + tile * 0.22, tile * 0.42, color, outline, line);
  } else if (size === 1) {
    drawHouse(ctx, x - tile * 0.17, y + tile * 0.1, tile * 0.32, color, outline, line);
    drawHouse(ctx, x + tile * 0.1, y + tile * 0.28, tile * 0.42, color, outline, line);
  } else {
    drawHouse(ctx, x - tile * 0.22, y + tile * 0.06, tile * 0.32, color, outline, line);
    drawHouse(ctx, x + tile * 0.22, y + tile * 0.06, tile * 0.32, color, outline, line);
    drawHouse(ctx, x, y + tile * 0.3, tile * 0.46, color, outline, line);
  }
}

/** Far out: a village is a square in the owner's colour, bigger for bigger villages. */
function drawVillageSquare(
  ctx: CanvasRenderingContext2D,
  middle: Point,
  tile: number,
  size: 0 | 1 | 2,
  color: string,
  edge: string,
): void {
  const side = Math.max(4, tile * SQUARE_SIDE[size]);
  ctx.beginPath();
  ctx.roundRect(middle.x - side / 2, middle.y - side / 2, side, side, side * 0.2);
  ctx.fillStyle = color;
  ctx.fill();
  ctx.lineWidth = 1.5;
  ctx.strokeStyle = edge;
  ctx.stroke();
}

/** A house with a pointed roof and a door; (x, baseY) is the middle of its bottom edge, `width` the walls. */
function drawHouse(
  ctx: CanvasRenderingContext2D,
  x: number,
  baseY: number,
  width: number,
  color: string,
  outline: string,
  line: number,
): void {
  const half = width / 2;
  const walls = width * 0.55;
  const roof = width * 0.5;
  const eave = width * 0.12;
  ctx.beginPath();
  ctx.moveTo(x - half, baseY);
  ctx.lineTo(x - half, baseY - walls);
  ctx.lineTo(x - half - eave, baseY - walls);
  ctx.lineTo(x, baseY - walls - roof);
  ctx.lineTo(x + half + eave, baseY - walls);
  ctx.lineTo(x + half, baseY - walls);
  ctx.lineTo(x + half, baseY);
  ctx.closePath();
  ctx.fillStyle = color;
  ctx.fill();
  ctx.lineJoin = 'round';
  ctx.lineWidth = line;
  ctx.strokeStyle = outline;
  ctx.stroke();
  const door = width * 0.26;
  ctx.fillStyle = outline;
  ctx.fillRect(x - door / 2, baseY - walls * 0.6, door, walls * 0.6);
}

/** The NPC lord's marker: a small gold pennant on a pole at the top right of the field. */
function drawPennant(ctx: CanvasRenderingContext2D, middle: Point, tile: number, colors: MapColors): void {
  const poleX = middle.x + tile * 0.32;
  const top = middle.y - tile * 0.38;
  const line = Math.max(1, tile / 32);
  ctx.lineWidth = line;
  ctx.strokeStyle = colors.outline;
  ctx.beginPath();
  ctx.moveTo(poleX, middle.y + tile * 0.12);
  ctx.lineTo(poleX, top);
  ctx.stroke();
  ctx.beginPath();
  ctx.moveTo(poleX, top);
  ctx.lineTo(poleX + tile * 0.2, top + tile * 0.07);
  ctx.lineTo(poleX, top + tile * 0.14);
  ctx.closePath();
  ctx.fillStyle = colors.marker;
  ctx.fill();
  ctx.stroke();
}

/** Gold ring with a dark edge around the selected village's field; a faint ring under the mouse. */
function drawFieldRing(
  ctx: CanvasRenderingContext2D,
  middle: Point,
  tile: number,
  colors: MapColors,
  kind: 'selected' | 'hovered',
): void {
  const half = Math.max(RING_MIN_HALF, tile / 2 - 2);
  ctx.beginPath();
  ctx.roundRect(middle.x - half, middle.y - half, half * 2, half * 2, half * 0.36);
  if (kind === 'selected') {
    ctx.lineWidth = 5;
    ctx.strokeStyle = colors.outline;
    ctx.stroke();
    ctx.lineWidth = 2.5;
    ctx.strokeStyle = colors.marker;
    ctx.stroke();
  } else {
    ctx.lineWidth = 2;
    ctx.strokeStyle = colors.gridStrong;
    ctx.stroke();
  }
}

/** Dashed ring on an empty field the player jumped to. */
function drawMarkedField(ctx: CanvasRenderingContext2D, scene: MapScene, field: Coords): void {
  const { camera, size, colors } = scene;
  const middle = toScreen(camera, size, { x: field.x + 0.5, y: field.y + 0.5 });
  const half = Math.max(RING_MIN_HALF, camera.tile / 2 - 3);
  ctx.beginPath();
  ctx.roundRect(middle.x - half, middle.y - half, half * 2, half * 2, half * 0.36);
  ctx.lineWidth = 4;
  ctx.strokeStyle = colors.outline;
  ctx.setLineDash([6, 4]);
  ctx.stroke();
  ctx.lineWidth = 2;
  ctx.strokeStyle = colors.marker;
  ctx.stroke();
  ctx.setLineDash([]);
}

function drawCrosshair(ctx: CanvasRenderingContext2D, middle: Point, colors: MapColors): void {
  const path = new Path2D();
  path.arc(middle.x, middle.y, 7, 0, Math.PI * 2);
  for (const [dx, dy] of [
    [1, 0],
    [-1, 0],
    [0, 1],
    [0, -1],
  ] as const) {
    path.moveTo(middle.x + dx * 4, middle.y + dy * 4);
    path.lineTo(middle.x + dx * 13, middle.y + dy * 13);
  }
  ctx.lineWidth = 4;
  ctx.strokeStyle = colors.outline;
  ctx.stroke(path);
  ctx.lineWidth = 2;
  ctx.strokeStyle = colors.marker;
  ctx.stroke(path);
}

/** Bands along the top and the left edge with every fifth coordinate (05-frontend §6). */
function drawAxes(ctx: CanvasRenderingContext2D, scene: MapScene, range: FieldRange | undefined): void {
  const { camera, size, colors } = scene;
  ctx.fillStyle = colors.axis;
  ctx.fillRect(0, 0, size.width, AXIS_TOP);
  ctx.fillRect(0, AXIS_TOP, AXIS_LEFT, size.height - AXIS_TOP);
  if (range === undefined) {
    return;
  }
  ctx.font = `600 11px ${FONT}`;
  ctx.fillStyle = colors.axisText;
  ctx.strokeStyle = colors.axisText;
  ctx.lineWidth = 1;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  const ticks = new Path2D();
  for (const x of axisValues(range.minX, range.maxX)) {
    const screenX = toScreen(camera, size, { x: x + 0.5, y: 0 }).x;
    if (screenX > AXIS_LEFT + 10 && screenX < size.width - 6) {
      ctx.fillText(String(x), screenX, AXIS_TOP / 2 - 1);
      ticks.moveTo(crisp(screenX), AXIS_TOP - 3);
      ticks.lineTo(crisp(screenX), AXIS_TOP);
    }
  }
  for (const y of axisValues(range.minY, range.maxY)) {
    const screenY = toScreen(camera, size, { x: 0, y: y + 0.5 }).y;
    if (screenY > AXIS_TOP + 8 && screenY < size.height - 6) {
      ctx.fillText(String(y), AXIS_LEFT / 2 - 1, screenY);
      ticks.moveTo(AXIS_LEFT - 3, crisp(screenY));
      ticks.lineTo(AXIS_LEFT, crisp(screenY));
    }
  }
  ctx.stroke(ticks);
}

/** Lines one pixel wide look sharp on half pixels. */
function crisp(value: number): number {
  return Math.round(value) + 0.5;
}

function shorten(name: string): string {
  return name.length > LABEL_MAX_CHARS ? `${name.slice(0, LABEL_MAX_CHARS - 1)}…` : name;
}
