import type { MapMovement, MapMovementLine, MapVillage } from '../../api/queries/map';
import type { Coords } from '../../game/coords';
import { bannerRollBackAt } from '../../game/farm';
import {
  chevronOffsets,
  foldedBadges,
  lineEnds,
  lineHostile,
  lineKind,
  linePosition,
  lineTouches,
  mergeTouchingBadges,
} from '../../game/mapLines';
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
  clipSegment,
  growRect,
  HOUSES_MIN_TILE,
  intersectRect,
  mediumLabels,
  toScreen,
  villageKind,
  villageSize,
  visibleFields,
  type Camera,
  type FieldRange,
  type Point,
  type Rect,
  type Size,
  type VillageKind,
} from '../../game/mapView';
import { landOf, riverFields, riversOf, type Land, type Mound } from '../../game/terrain';

/*
 * Draws the map onto a canvas (05-frontend §6). Plain canvas calls, no React: MapPage calls
 * drawMap() after every change of the camera, the data or the theme. Sizes are CSS pixels; the
 * caller has already scaled the context for the device pixel ratio.
 */

/**
 * Long shapes (the rivers, the map edge, movement lines) are cut to the canvas and this much around
 * it (CSS px, more than any line is wide) before they are drawn, so the GPU never gets points
 * thousands of px off the canvas (1.3.1: the likely cause of streaks flashing across the map on a
 * phone while it moved). Nothing on the canvas changes.
 */
const DRAW_MARGIN = 64;

/** Height of the axis band at the top and width of the one on the left (CSS px); overlays keep clear of them. */
export const AXIS_TOP = 20;
export const AXIS_LEFT = 28;

/** From this field size on, village names are written below the villages. */
const LABEL_MIN_TILE = 44;
const LABEL_MAX_CHARS = 16;
/**
 * From this field size on (up to LABEL_MIN_TILE), the player's own villages show their name and
 * foreign ones their owner's; the selected and the hovered village show their whole name on a plate.
 */
const MEDIUM_LABEL_MIN_TILE = 26;
/** Radius of the ground patch under a small, medium and large village, in fields. */
const PATCH_RADIUS = [0.3, 0.38, 0.46] as const;
/** Far out: side of the square of a small, medium and large village, in fields. */
const SQUARE_SIDE = [0.55, 0.7, 0.85] as const;
/** Below this field size only the lines every 5 fields are drawn (the others would be a blur). */
const THIN_GRID_MIN_TILE = 16;
/** Selection rings never get smaller than this (half their side, px), so they stay visible far out. */
const RING_MIN_HALF = 7;
const FONT = '"Alegreya Sans", system-ui, sans-serif';
const PLATE_NAME_FONT = `700 13px ${FONT}`;
const PLATE_OWNER_FONT = `500 11.5px ${FONT}`;
/** Movement badges (M26): their radius is this share of a field, within these bounds (CSS px). */
const BADGE_SHARE = 0.2;
const BADGE_MIN_RADIUS = 5;
const BADGE_MAX_RADIUS = 12;
/** Smaller badges have no number: it would be unreadable. */
const BADGE_COUNT_MIN_RADIUS = 7;
/** Movement lines (v1.3): their width is this share of a field, within these bounds (CSS px). */
const LINE_SHARE = 0.06;
const LINE_MIN_WIDTH = 1.5;
const LINE_MAX_WIDTH = 3.5;
/** Lines that don't belong to the selected village or line fade to this. */
const LINE_FADED = 0.25;
/** A pale edge under every line, like the names have: this much wider (CSS px) and this strong. */
const LINE_EDGE = 3;
const LINE_EDGE_ALPHA = 0.7;
/** An attack on the player: this many times as wide as the other lines, in long dashes (in widths), on a wider edge. */
const HOSTILE_WIDTH = 1.7;
const HOSTILE_DASH = [2.6, 1.6] as const;
const HOSTILE_EDGE = 5;
const HOSTILE_EDGE_ALPHA = 0.9;
/**
 * The arrows along the lines: this far apart and this far from the ends (CSS px, or the share of a
 * field when that is more), none on a line shorter than ARROW_MIN_LENGTH, and none within this many
 * badge radii of the troops.
 */
const ARROW_MIN_SPACING = 70;
const ARROW_SPACING_SHARE = 3;
const ARROW_MIN_MARGIN = 18;
const ARROW_MARGIN_SHARE = 0.75;
const ARROW_MIN_LENGTH = 28;
const ARROW_BADGE_GAP = 1.8;
/** Badges of one way closer than this many radii merge into one with their number. */
const BADGE_MERGE_REACH = 1.4;

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
  /** The faded banner of a village whose banner chance the player used (v1.3). */
  bannerUsed: string;
  /** The sheet of paper on a village with the player's own note (1.4). */
  note: string;
  /** The landscape (v1.3): broadleaf crowns, their shade and light, and firs. */
  tree: string;
  treeShade: string;
  treeLight: string;
  conifer: string;
  /** A mound's lit side and its shade; the shade also tints hilly fields far out. */
  hill: string;
  hillShade: string;
  water: string;
  waterBank: string;
  /** Wooded fields far out. */
  forestFar: string;
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
    bannerUsed: color('banner-used'),
    note: color('note'),
    tree: color('tree'),
    treeShade: color('tree-shade'),
    treeLight: color('tree-light'),
    conifer: color('conifer'),
    hill: color('hill'),
    hillShade: color('hill-shade'),
    water: color('water'),
    waterBank: color('water-bank'),
    forestFar: color('forest-far'),
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
  /** The same movements one by one (v1.3); empty while the lines are switched off. */
  lines: readonly MapMovementLine[];
  /** The tapped line (its bubble is open) or the one under the mouse. */
  selectedLineId: number | null;
  ownPlayerId: number;
  selectedId: number | null;
  hoveredId: number | null;
  /** A field to point out without a village on it (the target of "go to"). */
  marked: Coords | null;
  /** Mark the middle of the canvas (keyboard focus: Enter selects the village there). */
  crosshair: boolean;
  colors: MapColors;
  /** Server time of the picture: a used banner chance that is back by then is not drawn. */
  nowMs: number;
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
    drawMovementLines(ctx, scene);
    drawVillages(ctx, scene, range);
    drawMovementMarkers(ctx, scene, range);
    drawLineBadges(ctx, scene);
    // the names above the troops on the lines, so a farm round doesn't hide them
    drawVillageLabels(ctx, scene, range);
    drawNamePlates(ctx, scene);
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

  drawTerrain(ctx, scene, range);

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

  // a side far off the canvas is moved to just outside it
  const edge = intersectRect(
    { left: left(0), top: top(0), width: mapSize * tile, height: mapSize * tile },
    drawArea(size),
  );
  ctx.lineWidth = 2;
  ctx.strokeStyle = colors.edge;
  ctx.strokeRect(edge.left, edge.top, edge.width, edge.height);
}

/** Far out, fields with at least this much forest or hill are tinted. */
const FAR_TINT_MIN = 0.2;
/** The land is worked out and kept in square pieces of this many fields, as they come into view. */
const LAND_PIECE = 20;
/** More pieces than this (a big map looked at all over) and the kept ones are dropped and made anew. */
const LAND_PIECES_KEPT = 400;

interface LandPiece {
  land: Land;
  /** The far-out tint of the piece, one pixel per field, for the colours in `key`. */
  tint?: { key: string; image: HTMLCanvasElement | undefined };
  /** The piece's mounds in fields from its corner, without the fields in `key` (villages, rivers). */
  mounds?: { key: string; shade: Path2D; light: Path2D };
}

const landPieces = new Map<string, LandPiece>();
/** The rivers of a map size and the fields they run through. */
const waterCache = new Map<number, { rivers: Array<Array<{ x: number; y: number }>>; fields: Set<number> }>();

function landPiece(px: number, py: number): LandPiece {
  const key = `${px}|${py}`;
  let piece = landPieces.get(key);
  if (piece === undefined) {
    if (landPieces.size >= LAND_PIECES_KEPT) {
      landPieces.clear();
    }
    piece = { land: landOf(px * LAND_PIECE, py * LAND_PIECE, LAND_PIECE) };
    landPieces.set(key, piece);
  }
  return piece;
}

/** Woods in a forest green, hills in a dry green, stronger where they are thicker. */
function tintImage(land: Land, colors: MapColors): HTMLCanvasElement | undefined {
  const image = document.createElement('canvas');
  image.width = LAND_PIECE;
  image.height = LAND_PIECE;
  const ctx = image.getContext('2d');
  if (ctx === null) {
    return undefined;
  }
  land.forest.forEach((forest, index) => {
    const hills = land.hills[index] ?? 0;
    if (forest > FAR_TINT_MIN) {
      ctx.globalAlpha = 0.25 + forest * 0.3;
      ctx.fillStyle = colors.forestFar;
    } else if (hills > FAR_TINT_MIN) {
      ctx.globalAlpha = 0.25 + hills * 0.35;
      ctx.fillStyle = colors.hillShade;
    } else {
      return;
    }
    ctx.fillRect(index % LAND_PIECE, Math.floor(index / LAND_PIECE), 1, 1);
  });
  return image;
}

/**
 * The landscape under the villages (v1.3, game/terrain.ts): hills, two rivers and woods. Far out
 * tinted fields and thin rivers; closer in mounds and trees, none on a village's field or in a river.
 * The land is worked out once per piece, its mounds are kept as one path, and each tree is drawn from
 * a ready picture.
 */
function drawTerrain(ctx: CanvasRenderingContext2D, scene: MapScene, range: FieldRange): void {
  const { camera, size, mapSize, colors } = scene;
  const tile = camera.tile;
  const far = tile < HOUSES_MIN_TILE;
  const corner = toScreen(camera, size, { x: 0, y: 0 });
  const pixelRatio = ctx.getTransform().a;
  let water = waterCache.get(mapSize);
  if (water === undefined) {
    const rivers = riversOf(mapSize);
    water = { rivers, fields: riverFields(mapSize, rivers) };
    waterCache.set(mapSize, water);
  }
  const area = drawArea(size);
  const onMap = intersectRect({ left: corner.x, top: corner.y, width: mapSize * tile, height: mapSize * tile }, area);
  ctx.save();
  ctx.beginPath();
  ctx.rect(onMap.left, onMap.top, onMap.width, onMap.height);
  ctx.clip();

  const pieces = { minX: Math.floor(range.minX / LAND_PIECE), maxX: Math.floor(range.maxX / LAND_PIECE) };
  const mounds: Array<{ paths: NonNullable<LandPiece['mounds']>; at: Point }> = [];
  const trees: Array<{ shape: TreeShape; foot: Point; r: number }> = [];
  if (far) {
    // one small picture per piece, scaled up without smoothing; its edges on whole device pixels, so
    // two pieces meet without a seam
    const snap = (value: number) => Math.round(value * pixelRatio) / pixelRatio;
    const key = `${colors.forestFar} ${colors.hillShade}`;
    ctx.imageSmoothingEnabled = false;
    for (let py = Math.floor(range.minY / LAND_PIECE); py <= Math.floor(range.maxY / LAND_PIECE); py++) {
      for (let px = pieces.minX; px <= pieces.maxX; px++) {
        const piece = landPiece(px, py);
        if (piece.tint?.key !== key) {
          piece.tint = { key, image: tintImage(piece.land, colors) };
        }
        if (piece.tint.image !== undefined) {
          const left = snap(corner.x + px * LAND_PIECE * tile);
          const top = snap(corner.y + py * LAND_PIECE * tile);
          const right = snap(corner.x + (px + 1) * LAND_PIECE * tile);
          const bottom = snap(corner.y + (py + 1) * LAND_PIECE * tile);
          ctx.drawImage(piece.tint.image, left, top, right - left, bottom - top);
        }
      }
    }
    ctx.imageSmoothingEnabled = true;
  } else {
    // no trees or mounds on a village's field or in a river
    const clear = new Set(water.fields);
    const villageFields = new Map<string, number[]>();
    for (const village of scene.villages) {
      const field = village.y * mapSize + village.x;
      clear.add(field);
      const key = `${Math.floor(village.x / LAND_PIECE)}|${Math.floor(village.y / LAND_PIECE)}`;
      const fields = villageFields.get(key);
      if (fields === undefined) {
        villageFields.set(key, [field]);
      } else {
        fields.push(field);
      }
    }
    // a piece's mounds are one path, made again only when a village appears or goes there
    for (let py = Math.floor(range.minY / LAND_PIECE); py <= Math.floor(range.maxY / LAND_PIECE); py++) {
      for (let px = pieces.minX; px <= pieces.maxX; px++) {
        const piece = landPiece(px, py);
        const key = `${mapSize} ${(villageFields.get(`${px}|${py}`) ?? []).sort((a, b) => a - b).join(',')}`;
        if (piece.mounds?.key !== key) {
          piece.mounds = { key, ...moundPaths(piece.land, px, py, mapSize, clear) };
        }
        mounds.push({
          paths: piece.mounds,
          at: { x: corner.x + px * LAND_PIECE * tile, y: corner.y + py * LAND_PIECE * tile },
        });
      }
    }
    for (let y = range.minY; y <= range.maxY; y++) {
      const py = Math.floor(y / LAND_PIECE);
      const row = y - py * LAND_PIECE;
      const top = corner.y + y * tile;
      for (let px = pieces.minX; px <= pieces.maxX; px++) {
        const { land } = landPiece(px, py);
        for (const { x, tree } of land.trees[row] ?? []) {
          if (x < range.minX || x > range.maxX || clear.has(y * mapSize + x)) {
            continue;
          }
          const foot = { x: corner.x + (x + tree.dx) * tile, y: top + tree.dy * tile };
          const r = tile * tree.size;
          // two mirrored crowns, so a wood isn't one stamp
          const shape = tree.conifer ? 'fir' : tree.dx > 0.5 ? 'broadleafRight' : 'broadleafLeft';
          trees.push({ shape, foot, r });
        }
      }
    }
  }

  // all the shade first, so a mound reaching into the next piece keeps its lit side
  for (const [part, color] of [
    ['shade', colors.hillShade],
    ['light', colors.hill],
  ] as const) {
    ctx.fillStyle = color;
    for (const { paths, at } of mounds) {
      ctx.save();
      ctx.translate(at.x, at.y);
      ctx.scale(tile, tile);
      ctx.fill(paths[part]);
      ctx.restore();
    }
  }

  // rivers: the water on a darker bank, which is left out far away; only the stretches near the
  // canvas (a step of a river is less than a field)
  const riverPath = new Path2D();
  for (const river of water.rivers) {
    let previous: Point | undefined;
    let drawing = false;
    for (const point of river) {
      const at = toScreen(camera, size, point);
      if (previous !== undefined && boxesMeet(previous, at, area)) {
        if (!drawing) {
          riverPath.moveTo(previous.x, previous.y);
          drawing = true;
        }
        riverPath.lineTo(at.x, at.y);
      } else {
        drawing = false;
      }
      previous = at;
    }
  }
  const width = Math.max(far ? 1.5 : 3, tile * 0.26);
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';
  if (!far) {
    ctx.lineWidth = width + 3;
    ctx.strokeStyle = colors.waterBank;
    ctx.stroke(riverPath);
  }
  ctx.lineWidth = width;
  ctx.strokeStyle = colors.water;
  ctx.stroke(riverPath);

  // each tree from its picture, the back ones first
  trees.sort((a, b) => a.foot.y - b.foot.y);
  const sprites = treeSpritesOf(colors);
  for (const { shape, foot, r } of trees) {
    const sprite = treeSprite(sprites, shape, r * pixelRatio, colors);
    if (sprite !== undefined) {
      const scale = r / sprite.radius;
      ctx.drawImage(
        sprite.image,
        foot.x - TREE_BOX.left * r,
        foot.y - TREE_BOX.top * r,
        sprite.image.width * scale,
        sprite.image.height * scale,
      );
    }
  }
  ctx.restore();
}

/** The mounds of a piece in fields from its corner, leaving out the fields in `clear`. */
function moundPaths(
  land: Land,
  px: number,
  py: number,
  mapSize: number,
  clear: ReadonlySet<number>,
): { shade: Path2D; light: Path2D } {
  const shade = new Path2D();
  const light = new Path2D();
  land.mounds.forEach((row, index) => {
    const y = py * LAND_PIECE + index;
    for (const { x, mound } of row) {
      if (!clear.has(y * mapSize + x)) {
        addMound(shade, light, { x: x - px * LAND_PIECE, y: index }, 1, mound);
      }
    }
  });
  return { shade, light };
}

/** A soft mound, lit from the top left: the whole mound in shade, then its lit left part. */
function addMound(shade: Path2D, light: Path2D, origin: Point, tile: number, mound: Mound): void {
  const cx = origin.x + tile * mound.cx;
  const cy = origin.y + tile * mound.cy;
  const w = tile * mound.w;
  const h = tile * mound.h;
  shade.moveTo(cx - w, cy);
  shade.bezierCurveTo(cx - w * 0.55, cy - h * 1.25, cx + w * 0.45, cy - h * 1.3, cx + w, cy);
  shade.closePath();
  light.moveTo(cx - w, cy);
  light.bezierCurveTo(cx - w * 0.6, cy - h * 1.2, cx + w * 0.05, cy - h * 1.32, cx + w * 0.18, cy - h * 0.9);
  light.quadraticCurveTo(cx - w * 0.15, cy - h * 0.3, cx - w * 0.3, cy);
  light.closePath();
}

type TreeShape = 'fir' | 'broadleafLeft' | 'broadleafRight';

/** How far a tree reaches from its foot, in tree radii: to the left and right, up and down. */
const TREE_BOX = { left: 1.2, right: 1.25, top: 1.65, bottom: 1.2 } as const;
/** Trees are drawn from pictures of these radii (device px), each scaled down at most to half. */
const TREE_SPRITE_RADII = [6, 12, 24, 48, 96] as const;
const TREE_SHAPES: readonly TreeShape[] = ['fir', 'broadleafLeft', 'broadleafRight'];
type TreeSprite = { image: HTMLCanvasElement; radius: number };
/** By shape and size, per theme (the four tree colours). */
type TreeSprites = Map<number, TreeSprite | undefined>;
const treeSprites = new Map<string, TreeSprites>();

function treeSpritesOf(colors: MapColors): TreeSprites {
  const key = `${colors.tree} ${colors.treeShade} ${colors.treeLight} ${colors.conifer}`;
  let sprites = treeSprites.get(key);
  if (sprites === undefined) {
    sprites = new Map();
    treeSprites.set(key, sprites);
  }
  return sprites;
}

/**
 * The picture of a tree, the next size up from `radius` (device px), drawn once per size and theme.
 * Thousands of trees are on screen at once; drawing each of their shapes anew on every frame of a
 * pan would make the map stutter.
 */
function treeSprite(sprites: TreeSprites, shape: TreeShape, radius: number, colors: MapColors): TreeSprite | undefined {
  const level = TREE_SPRITE_RADII.find((candidate) => candidate >= radius) ?? TREE_SPRITE_RADII.at(-1) ?? radius;
  // a number, not a string: this runs for every tree on every frame
  const key = TREE_SHAPES.indexOf(shape) * 1000 + level;
  if (sprites.has(key)) {
    return sprites.get(key);
  }
  const image = document.createElement('canvas');
  image.width = Math.ceil((TREE_BOX.left + TREE_BOX.right) * level);
  image.height = Math.ceil((TREE_BOX.top + TREE_BOX.bottom) * level);
  const ctx = image.getContext('2d');
  const sprite = ctx === null ? undefined : { image, radius: level };
  if (ctx !== null) {
    drawTree(ctx, shape, { x: TREE_BOX.left * level, y: TREE_BOX.top * level }, level, colors);
  }
  sprites.set(key, sprite);
  return sprite;
}

/** Two mirrored crowns of three leafy lobes each (x, y, radius in tree radii). */
const LOBES: Record<Exclude<TreeShape, 'fir'>, ReadonlyArray<readonly [number, number, number]>> = {
  broadleafLeft: [
    [-0.44, -0.28, 0.6],
    [0.4, -0.2, 0.54],
    [-0.04, -0.66, 0.6],
  ],
  broadleafRight: [
    [-0.4, -0.22, 0.56],
    [0.42, -0.26, 0.58],
    [0.02, -0.66, 0.62],
  ],
};

/**
 * One tree with its shadow, lit from the top left. A fir: a short trunk under two tiers of branches, their right
 * halves in shade. A broadleaf tree: a trunk under a crown of three lobes, shaded on the lower right.
 */
function drawTree(ctx: CanvasRenderingContext2D, shape: TreeShape, foot: Point, r: number, colors: MapColors): void {
  ctx.beginPath();
  ctx.ellipse(foot.x + r * 0.25, foot.y + r * 0.85, r * 0.95, r * 0.3, 0, 0, Math.PI * 2);
  ctx.globalAlpha = 0.3;
  ctx.fillStyle = colors.treeShade;
  ctx.fill();
  ctx.globalAlpha = 1;
  const trunk = new Path2D();
  if (shape === 'fir') {
    trunk.rect(foot.x - r * 0.1, foot.y + r * 0.55, r * 0.2, r * 0.3);
    const branches = new Path2D();
    const shade = new Path2D();
    const tiers = [
      { top: -1.55, base: -0.2, half: 0.62 },
      { top: -0.85, base: 0.6, half: 0.85 },
    ];
    for (const tier of tiers) {
      branches.moveTo(foot.x, foot.y + r * tier.top);
      branches.lineTo(foot.x + r * tier.half, foot.y + r * tier.base);
      branches.lineTo(foot.x - r * tier.half, foot.y + r * tier.base);
      branches.closePath();
      shade.moveTo(foot.x, foot.y + r * tier.top);
      shade.lineTo(foot.x + r * tier.half, foot.y + r * tier.base);
      shade.lineTo(foot.x, foot.y + r * tier.base);
      shade.closePath();
    }
    ctx.fillStyle = colors.treeShade;
    ctx.fill(trunk);
    ctx.fillStyle = colors.conifer;
    ctx.fill(branches);
    ctx.globalAlpha = 0.45;
    ctx.fillStyle = colors.treeShade;
    ctx.fill(shade);
    ctx.globalAlpha = 1;
    return;
  }
  trunk.rect(foot.x - r * 0.1, foot.y - r * 0.1, r * 0.2, r * 0.9);
  const shade = new Path2D();
  const crown = new Path2D();
  const light = new Path2D();
  for (const [lx, ly, lr] of LOBES[shape]) {
    addCircle(shade, foot.x + (lx + 0.1) * r, foot.y + (ly + 0.14) * r, lr * r);
    addCircle(crown, foot.x + lx * r, foot.y + ly * r, lr * r);
    addCircle(light, foot.x + (lx - 0.16) * r, foot.y + (ly - 0.18) * r, lr * r * 0.42);
  }
  ctx.fillStyle = colors.treeShade;
  ctx.fill(trunk);
  ctx.fill(shade);
  ctx.fillStyle = colors.tree;
  ctx.fill(crown);
  ctx.fillStyle = colors.treeLight;
  ctx.fill(light);
}

function addCircle(path: Path2D, x: number, y: number, r: number): void {
  path.moveTo(x + r, y);
  path.arc(x, y, r, 0, Math.PI * 2);
}

/** The villages in sight, and one field around (a house or name reaches into the next field). */
function villagesOnScreen(scene: MapScene, range: FieldRange): MapVillage[] {
  return scene.villages.filter(
    (village) =>
      village.x >= range.minX - 1 &&
      village.x <= range.maxX + 1 &&
      village.y >= range.minY - 1 &&
      village.y <= range.maxY + 1,
  );
}

function drawVillages(ctx: CanvasRenderingContext2D, scene: MapScene, range: FieldRange): void {
  const { camera, size, colors } = scene;
  const tile = camera.tile;
  for (const village of villagesOnScreen(scene, range)) {
    const middle = toScreen(camera, size, { x: village.x + 0.5, y: village.y + 0.5 });
    const kind = villageKind(village, scene.ownPlayerId);
    if (village.id === scene.selectedId) {
      drawFieldRing(ctx, middle, tile, colors, 'selected');
    } else if (village.id === scene.hoveredId) {
      drawFieldRing(ctx, middle, tile, colors, 'hovered');
    }
    if (village.keep) {
      drawKeepRing(ctx, middle, tile, colors);
    }
    if (tile < HOUSES_MIN_TILE) {
      // far out: a square; NPC lords get a gold edge instead of the pennant
      const edge = kind === 'npc' ? colors.marker : colors.outline;
      drawVillageSquare(ctx, middle, tile, villageSize(village.points), colors.kinds[kind], edge);
    } else if (village.keep) {
      // near: the keep's fort in the owner's colour instead of houses (1.3)
      drawKeep(ctx, middle, tile, colors.kinds[kind], colors);
    } else {
      drawVillage(ctx, middle, tile, villageSize(village.points), colors.kinds[kind], colors.outline);
      if (kind === 'npc') {
        drawPennant(ctx, middle, tile, colors);
      }
      if (bannerRollBackAt(village.nextBannerRollAt, scene.nowMs) !== undefined) {
        drawUsedBanner(ctx, middle, tile, colors);
      }
    }
    if (village.keep && tile < HOUSES_MIN_TILE) {
      drawKeepStar(ctx, middle, tile, colors);
    }
    if (village.note !== null) {
      drawNoteMark(ctx, middle, tile, colors);
    }
  }
}

/** The gold ring on a keep's field (1.3): its radius in tiles (at least the minimum in px), fill and edge. */
const KEEP_RING = {
  share: 0.47,
  minRadius: 6,
  fillAlpha: 0.22,
  edgeAlpha: 0.85,
  edgeShare: 0.05,
  minEdge: 1.5,
} as const;

/**
 * The keep's fort (1.3), in tiles from the field's middle: its width, the base below the middle, the
 * towers' width and height, the wall's height, the crenels' depth, the gate's width, the flag pole.
 */
const KEEP_FORT = {
  width: 0.66,
  base: 0.3,
  tower: 0.17,
  towerHeight: 0.44,
  wallHeight: 0.27,
  notch: 0.05,
  gate: 0.14,
  pole: 0.24,
} as const;

/** Crenels along the wall between the towers: up, down, up, down, up. */
const KEEP_CRENELS = 5;

/** A soft gold ring on a keep's field, at every zoom (1.3). */
function drawKeepRing(ctx: CanvasRenderingContext2D, middle: Point, tile: number, colors: MapColors): void {
  ctx.save();
  ctx.beginPath();
  ctx.arc(middle.x, middle.y, Math.max(KEEP_RING.minRadius, tile * KEEP_RING.share), 0, Math.PI * 2);
  ctx.globalAlpha = KEEP_RING.fillAlpha;
  ctx.fillStyle = colors.marker;
  ctx.fill();
  ctx.globalAlpha = KEEP_RING.edgeAlpha;
  ctx.lineWidth = Math.max(KEEP_RING.minEdge, tile * KEEP_RING.edgeShare);
  ctx.strokeStyle = colors.marker;
  ctx.stroke();
  ctx.restore();
}

/**
 * A banner keep near the map (1.3): two towers and a crenellated wall in the owner's colour, a gate
 * and a gold banner, instead of the village's houses.
 */
function drawKeep(ctx: CanvasRenderingContext2D, middle: Point, tile: number, color: string, colors: MapColors): void {
  const line = Math.max(1, tile / 32);
  const width = tile * KEEP_FORT.width;
  const left = middle.x - width / 2;
  const right = middle.x + width / 2;
  const base = middle.y + tile * KEEP_FORT.base;
  const tower = tile * KEEP_FORT.tower;
  const towerTop = base - tile * KEEP_FORT.towerHeight;
  const wallTop = base - tile * KEEP_FORT.wallHeight;
  const notch = tile * KEEP_FORT.notch;
  const merlon = tower / 3;
  const crenel = (width - tower * 2) / KEEP_CRENELS;
  // the outline, left to right: tower with two merlons, the crenellated wall, the other tower
  const outline: Point[] = [
    { x: left, y: base },
    { x: left, y: towerTop },
    { x: left + merlon, y: towerTop },
    { x: left + merlon, y: towerTop + notch },
    { x: left + merlon * 2, y: towerTop + notch },
    { x: left + merlon * 2, y: towerTop },
    { x: left + tower, y: towerTop },
    ...Array.from({ length: KEEP_CRENELS }, (_, index) => {
      const y = index % 2 === 0 ? wallTop : wallTop + notch;
      const from = left + tower + crenel * index;
      return [
        { x: from, y },
        { x: from + crenel, y },
      ];
    }).flat(),
    { x: right - tower, y: towerTop },
    { x: right - merlon * 2, y: towerTop },
    { x: right - merlon * 2, y: towerTop + notch },
    { x: right - merlon, y: towerTop + notch },
    { x: right - merlon, y: towerTop },
    { x: right, y: towerTop },
    { x: right, y: base },
  ];
  ctx.beginPath();
  outline.forEach((point, index) => (index === 0 ? ctx.moveTo(point.x, point.y) : ctx.lineTo(point.x, point.y)));
  ctx.closePath();
  ctx.fillStyle = color;
  ctx.fill();
  ctx.lineJoin = 'round';
  ctx.lineWidth = line;
  ctx.strokeStyle = colors.outline;
  ctx.stroke();
  // the gate, an arch
  const gate = tile * KEEP_FORT.gate;
  const arch = base - gate * 0.8;
  ctx.beginPath();
  ctx.moveTo(middle.x - gate / 2, base);
  ctx.lineTo(middle.x - gate / 2, arch);
  ctx.arc(middle.x, arch, gate / 2, Math.PI, 0);
  ctx.lineTo(middle.x + gate / 2, base);
  ctx.closePath();
  ctx.fillStyle = colors.outline;
  ctx.fill();
  // the banner on the right tower: a pole and a gold swallowtail flag
  const poleX = right - tower / 2;
  const poleTop = towerTop - tile * KEEP_FORT.pole;
  ctx.beginPath();
  ctx.moveTo(poleX, towerTop);
  ctx.lineTo(poleX, poleTop);
  ctx.lineWidth = line;
  ctx.strokeStyle = colors.outline;
  ctx.stroke();
  ctx.beginPath();
  ctx.moveTo(poleX, poleTop);
  ctx.lineTo(poleX - tile * 0.2, poleTop + tile * 0.05);
  ctx.lineTo(poleX - tile * 0.15, poleTop + tile * 0.09);
  ctx.lineTo(poleX - tile * 0.2, poleTop + tile * 0.13);
  ctx.lineTo(poleX, poleTop + tile * 0.13);
  ctx.closePath();
  ctx.fillStyle = colors.marker;
  ctx.fill();
  ctx.lineWidth = Math.max(0.8, line * 0.8);
  ctx.stroke();
}

/** A keep on the overview map (1.3): a gold star over its square, a little larger than the square. */
export function drawKeepMark(ctx: CanvasRenderingContext2D, middle: Point, side: number, colors: MapColors): void {
  drawStar(ctx, middle, Math.max(4, side * 1.2), colors, 1);
}

/** A five-pointed gold star with a dark edge around `centre`. */
function drawStar(
  ctx: CanvasRenderingContext2D,
  centre: Point,
  outer: number,
  colors: MapColors,
  lineWidth: number,
): void {
  const inner = outer * 0.45;
  ctx.beginPath();
  Array.from({ length: 10 }, (_, point) => {
    const radius = point % 2 === 0 ? outer : inner;
    const angle = -Math.PI / 2 + (point * Math.PI) / 5;
    return { x: centre.x + radius * Math.cos(angle), y: centre.y + radius * Math.sin(angle) };
  }).forEach((point, index) => (index === 0 ? ctx.moveTo(point.x, point.y) : ctx.lineTo(point.x, point.y)));
  ctx.closePath();
  ctx.fillStyle = colors.marker;
  ctx.fill();
  ctx.lineWidth = lineWidth;
  ctx.lineJoin = 'round';
  ctx.strokeStyle = colors.outline;
  ctx.stroke();
}

function drawVillageLabels(ctx: CanvasRenderingContext2D, scene: MapScene, range: FieldRange): void {
  const { camera, size, colors } = scene;
  const tile = camera.tile;
  const onScreen = villagesOnScreen(scene, range);
  // Further in, a short label under each village: the player's own villages show their name, foreign
  // ones their owner's (one per player and 5 × 5 block). The picked villages get a plate instead.
  const picked = new Set([scene.selectedId, scene.hoveredId].filter((id) => id !== null));
  if (tile >= MEDIUM_LABEL_MIN_TILE && tile < LABEL_MIN_TILE) {
    ctx.font = `600 ${Math.round(Math.max(10, Math.min(12, tile * 0.3)))}px ${FONT}`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'top';
    ctx.lineJoin = 'round';
    ctx.lineWidth = 3;
    for (const { village, own } of mediumLabels(onScreen, scene.ownPlayerId, picked)) {
      const name = own ? village.name : village.playerName;
      if (name === null) {
        continue;
      }
      const middle = toScreen(camera, size, { x: village.x + 0.5, y: village.y + 0.5 });
      const text = shorten(name);
      const y = middle.y + tile * 0.42;
      ctx.strokeStyle = colors.labelHalo;
      ctx.strokeText(text, middle.x, y);
      ctx.fillStyle = colors.label;
      ctx.fillText(text, middle.x, y);
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
      if (village.name === null || picked.has(village.id)) {
        continue; // barbarians have no name; the picked ones get a plate (drawNamePlates)
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

/**
 * From medium zoom on, the selected and the hovered village show their whole name on a plate under
 * the field, a foreign village its owner's name below that. Drawn last, so no badge covers it.
 */
function drawNamePlates(ctx: CanvasRenderingContext2D, scene: MapScene): void {
  const { camera, size, colors } = scene;
  const tile = camera.tile;
  if (tile < MEDIUM_LABEL_MIN_TILE) {
    return;
  }
  for (const village of scene.villages) {
    if (village.name === null || (village.id !== scene.selectedId && village.id !== scene.hoveredId)) {
      continue;
    }
    const owner = village.playerId !== scene.ownPlayerId ? village.playerName : null;
    ctx.font = PLATE_OWNER_FONT;
    const ownerWidth = owner === null ? 0 : ctx.measureText(owner).width;
    ctx.font = PLATE_NAME_FONT;
    const width = Math.max(ctx.measureText(village.name).width, ownerWidth) + 14;
    const height = owner === null ? 20 : 34;
    const middle = toScreen(camera, size, { x: village.x + 0.5, y: village.y + 0.5 });
    const top = middle.y + Math.max(tile * 0.5, 12) + 2;
    ctx.beginPath();
    ctx.roundRect(middle.x - width / 2, top, width, height, 6);
    ctx.fillStyle = colors.labelHalo;
    ctx.fill();
    ctx.lineWidth = 1.5;
    ctx.strokeStyle = village.id === scene.selectedId ? colors.marker : colors.gridStrong;
    ctx.stroke();
    ctx.textAlign = 'center';
    ctx.textBaseline = 'top';
    ctx.fillStyle = colors.label;
    ctx.fillText(village.name, middle.x, top + 3);
    if (owner !== null) {
      ctx.font = PLATE_OWNER_FONT;
      ctx.fillText(owner, middle.x, top + 18);
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
  const radius = badgeRadius(tile);
  // numbers in a second pass, so the next badge doesn't cover them
  const counts: { text: string; x: number; y: number; halfWidth: number }[] = [];
  // a number sits in a small bubble on the badge's outer top corner, off the house's roof
  ctx.font = countFont(radius);
  const bubbleOffset = radius * 0.85;
  /** Half the width of a badge's number bubble; 0 without one. Where the return badge goes depends on it. */
  const bubbleHalf = (marker: Marker) => {
    const text = markerCount(marker.count);
    return far || text === '' || radius < BADGE_COUNT_MIN_RADIUS ? 0 : countBubbleHalf(ctx, text, radius);
  };
  // the left-hand badges' bubbles sit on their top right, the right-hand badge's on its top left
  const drawBadge = (marker: Marker, centre: Point, numberLeft = false) => {
    drawTroopBadge(ctx, centre, marker.kind, radius, far, colors);
    const halfWidth = bubbleHalf(marker);
    if (halfWidth > 0) {
      const x = numberLeft ? centre.x - bubbleOffset : centre.x + bubbleOffset;
      counts.push({ text: markerCount(marker.count), x, y: centre.y - radius * 0.8, halfWidth });
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
      // bubble would come closer than 3 px to the other side, and whenever both the left row's last
      // badge and the return badge carry a number: side by side the two bubbles face each other and
      // "2 2" reads as 22, at every zoom.
      const right = corner.x + tile - radius - 1;
      const last = markers.at(-1);
      const lastX = corner.x + radius + 1 + (markers.length - 1) * (radius * 2 + 1);
      const lastHalf = last === undefined ? 0 : bubbleHalf(last);
      const returnHalf = bubbleHalf(returning);
      const rowEnd = last === undefined ? corner.x : Math.max(lastX + radius, lastX + bubbleOffset + lastHalf);
      const returnStart = Math.min(right - radius, right - bubbleOffset - returnHalf);
      const row = (lastHalf > 0 && returnHalf > 0) || rowEnd + 3 > returnStart ? 1 : 0;
      drawBadge(returning, { x: right, y: corner.y + radius + 1 + row * (radius * 2 + 1) }, true);
    }
  }
  // the font is still the one set above
  for (const count of counts) {
    drawCountBubble(ctx, count.text, count, count.halfWidth, radius, colors);
  }
}

/** The font of the numbers on the badges. */
function countFont(radius: number): string {
  return `700 ${Math.max(10, Math.round(radius * 0.95))}px ${FONT}`;
}

/** Half the height of a number bubble on a badge of this radius. */
function countBubbleRadius(radius: number): number {
  return Math.max(7, radius * 0.62);
}

/** Half the width of the number bubble for `text`, with the count font set. */
function countBubbleHalf(ctx: CanvasRenderingContext2D, text: string, radius: number): number {
  return Math.max(countBubbleRadius(radius), (ctx.measureText(text).width + 6) / 2);
}

/** A movement badge (M26): a circle in the kind's colour with its swords, sword or arrow; far out a dot. */
function drawTroopBadge(
  ctx: CanvasRenderingContext2D,
  centre: Point,
  kind: MarkerKind,
  radius: number,
  far: boolean,
  colors: MapColors,
): void {
  ctx.beginPath();
  ctx.arc(centre.x, centre.y, radius, 0, Math.PI * 2);
  ctx.fillStyle = colors.badges[kind];
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
  ctx.strokeStyle = kind === 'noble' ? colors.badgeInkNoble : colors.badgeInk;
  ctx.stroke(markerPath(kind));
  ctx.restore();
}

/** A badge's number in its bubble, centred on `at`; the count font must be set. */
function drawCountBubble(
  ctx: CanvasRenderingContext2D,
  text: string,
  at: Point,
  halfWidth: number,
  radius: number,
  colors: MapColors,
): void {
  const bubbleRadius = countBubbleRadius(radius);
  ctx.beginPath();
  ctx.roundRect(at.x - halfWidth, at.y - bubbleRadius, halfWidth * 2, bubbleRadius * 2, bubbleRadius);
  ctx.fillStyle = colors.outline;
  ctx.fill();
  ctx.lineWidth = 1.5;
  ctx.strokeStyle = colors.badgeInk;
  ctx.stroke();
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillStyle = colors.badgeInk;
  ctx.fillText(text, at.x, at.y + 0.5);
}

/**
 * The radius of a movement badge at this zoom (CSS px). Far out a plain dot, bigger than the badge
 * share, so it stands out against the village squares.
 */
export function badgeRadius(tile: number): number {
  return tile < HOUSES_MIN_TILE
    ? Math.max(3, tile * 0.3)
    : Math.min(BADGE_MAX_RADIUS, Math.max(BADGE_MIN_RADIUS, tile * BADGE_SHARE));
}

/**
 * How strongly a line is drawn: fully while nothing is picked, and for the picked line or the
 * lines of the selected village; the others fade, so the picked ones stand out on a busy map.
 */
function lineAlpha(line: MapMovementLine, scene: MapScene): number {
  if (scene.selectedLineId !== null) {
    return line.id === scene.selectedLineId ? 1 : LINE_FADED;
  }
  if (scene.selectedId !== null) {
    return lineTouches(line, scene.selectedId) ? 1 : LINE_FADED;
  }
  return 1;
}

/**
 * The movement lines (v1.3), under the villages: dotted from start to target (the way ahead),
 * solid with a dark edge from the start to where the troops are now (the way behind), on a pale
 * edge like the names have, with arrows towards the target. Another player's attack on the player
 * has no position (its start time is hidden): it is the one line that stands out, wider and in long
 * dashes all the way. A way shared by several movements gets its dots and arrows once.
 */
function drawMovementLines(ctx: CanvasRenderingContext2D, scene: MapScene): void {
  if (scene.lines.length === 0) {
    return;
  }
  const { camera, size, colors } = scene;
  const area = drawArea(size);
  const width = Math.min(LINE_MAX_WIDTH, Math.max(LINE_MIN_WIDTH, camera.tile * LINE_SHARE));
  const dotted = new Set<string>();
  const arrows: Arrows[] = [];
  const strokes: LineStroke[] = [];
  ctx.save();
  ctx.lineCap = 'round';
  // the pale edges first, all of them: drawn line by line, the edge of a later movement on the same
  // way would cover the dots or dashes of an earlier one
  ctx.strokeStyle = colors.labelHalo;
  for (const line of scene.lines) {
    const ends = lineEnds(line);
    const from = toScreen(camera, size, ends.from);
    const to = toScreen(camera, size, ends.to);
    if (clipSegment(from, to, area) === undefined) {
      continue;
    }
    const alpha = lineAlpha(line, scene);
    const color = colors.badges[lineKind(line)];
    const position = linePosition(line, scene.nowMs);
    const at = position === undefined ? undefined : toScreen(camera, size, position);
    const hostile = lineHostile(line);
    const way = `${line.from.id}>${line.to.id}:${lineKind(line)}:${alpha}:${hostile}`;
    let wayWidth: number | undefined;
    if (!dotted.has(way) || at === undefined) {
      dotted.add(way);
      wayWidth = at === undefined ? width * 1.15 : width;
      if (hostile) {
        wayWidth = width * HOSTILE_WIDTH;
      }
      ctx.globalAlpha = alpha * (hostile ? HOSTILE_EDGE_ALPHA : LINE_EDGE_ALPHA);
      ctx.lineWidth = wayWidth + (hostile ? HOSTILE_EDGE : LINE_EDGE);
      strokeSegment(ctx, from, to, area);
      arrows.push({ from, to, color, alpha, width: wayWidth, badge: at });
    }
    strokes.push({ from, to, at, alpha, color, hostile, wayWidth });
  }
  // then the lines, an attack on the player last, so it stays on top of a shared way
  const ordered = [...strokes.filter((stroke) => !stroke.hostile), ...strokes.filter((stroke) => stroke.hostile)];
  for (const { from, to, at, alpha, color, hostile, wayWidth } of ordered) {
    if (wayWidth !== undefined) {
      ctx.globalAlpha = alpha * (at === undefined ? 1 : 0.85);
      ctx.strokeStyle = color;
      ctx.lineWidth = wayWidth;
      ctx.setLineDash(hostile ? HOSTILE_DASH.map((share) => share * wayWidth) : [0.1, width * 2.8]);
      strokeSegment(ctx, from, to, area);
    }
    if (at === undefined) {
      continue;
    }
    ctx.setLineDash([]);
    ctx.globalAlpha = alpha * 0.55;
    ctx.strokeStyle = colors.outline;
    ctx.lineWidth = width + 2;
    strokeSegment(ctx, from, at, area);
    ctx.globalAlpha = alpha;
    ctx.strokeStyle = color;
    ctx.lineWidth = width;
    strokeSegment(ctx, from, at, area);
  }
  ctx.setLineDash([]);
  drawArrows(ctx, arrows, camera.tile, colors, area);
  ctx.restore();
}

/** One movement line on screen, between drawing the edges and the lines. */
interface LineStroke {
  from: Point;
  to: Point;
  /** Where the troops are now; undefined where it is hidden. */
  at: Point | undefined;
  alpha: number;
  color: string;
  hostile: boolean;
  /** The dotted or dashed way's width; undefined when another movement of the same way drew it. */
  wayWidth: number | undefined;
}

/** The part of a line near the canvas; its dashes stay where they would be on the whole line. */
function strokeSegment(ctx: CanvasRenderingContext2D, from: Point, to: Point, area: Rect): void {
  const part = clipSegment(from, to, area);
  if (part === undefined) {
    return;
  }
  ctx.lineDashOffset = part.skipped;
  ctx.beginPath();
  ctx.moveTo(part.from.x, part.from.y);
  ctx.lineTo(part.to.x, part.to.y);
  ctx.stroke();
  ctx.lineDashOffset = 0;
}

/** The arrows of one way, on screen. */
interface Arrows {
  from: Point;
  to: Point;
  color: string;
  alpha: number;
  /** The line's width. */
  width: number;
  /** The troops' badge, if shown: no arrow under it. */
  badge: Point | undefined;
}

/** Small open arrows along each way, pointing at the target, on the same pale edge as the line. */
function drawArrows(
  ctx: CanvasRenderingContext2D,
  arrows: readonly Arrows[],
  tile: number,
  colors: MapColors,
  area: Rect,
): void {
  const margin = Math.max(ARROW_MIN_MARGIN, tile * ARROW_MARGIN_SHARE);
  const spacing = Math.max(ARROW_MIN_SPACING, tile * ARROW_SPACING_SHARE);
  const badgeGap = badgeRadius(tile) * ARROW_BADGE_GAP;
  ctx.lineJoin = 'round';
  for (const arrow of arrows) {
    const dx = arrow.to.x - arrow.from.x;
    const dy = arrow.to.y - arrow.from.y;
    const length = Math.hypot(dx, dy);
    const half = Math.max(3.5, arrow.width * 1.6);
    const stroke = Math.max(1.6, arrow.width * 0.9);
    for (const along of chevronOffsets(length, margin, spacing, ARROW_MIN_LENGTH)) {
      const x = arrow.from.x + (dx * along) / length;
      const y = arrow.from.y + (dy * along) / length;
      if (!boxesMeet({ x, y }, { x, y }, area)) {
        continue;
      }
      if (arrow.badge !== undefined && Math.hypot(arrow.badge.x - x, arrow.badge.y - y) < badgeGap) {
        continue;
      }
      ctx.save();
      ctx.translate(x, y);
      ctx.rotate(Math.atan2(dy, dx));
      ctx.beginPath();
      ctx.moveTo(-half * 0.6, -half);
      ctx.lineTo(half * 0.5, 0);
      ctx.lineTo(-half * 0.6, half);
      ctx.globalAlpha = arrow.alpha;
      ctx.strokeStyle = colors.labelHalo;
      ctx.lineWidth = stroke + LINE_EDGE;
      ctx.stroke();
      ctx.strokeStyle = arrow.color;
      ctx.lineWidth = stroke;
      ctx.stroke();
      ctx.restore();
    }
  }
}

/**
 * The troops on the lines: the markers' badge (swords, sword, arrow) where they are now, above the
 * villages. Far out a dot. A farm round just sent folds into one badge with its number at the bottom
 * left of its village, and badges of one way that touch merge into one with their number
 * (`foldedBadges`, `mergeTouchingBadges`). The picked line's badge gets a gold ring and is drawn
 * last, on top.
 */
function drawLineBadges(ctx: CanvasRenderingContext2D, scene: MapScene): void {
  if (scene.lines.length === 0) {
    return;
  }
  const { camera, size, colors } = scene;
  const far = camera.tile < HOUSES_MIN_TILE;
  const radius = badgeRadius(camera.tile);
  const folded = foldedBadges(scene.lines, scene.nowMs, { lineId: scene.selectedLineId, villageId: scene.selectedId });
  const foldedIds = new Set(folded.flatMap((badge) => badge.lines.map((line) => line.id)));
  ctx.save();
  for (const badge of folded) {
    const corner = toScreen(camera, size, { x: badge.start.x, y: badge.start.y });
    // bottom left: the top row holds the markers (M26) and the right edge their return badge, which
    // steps down a row while attacks or supports come in too
    const centre = { x: corner.x + radius + 1, y: corner.y + camera.tile - radius - 1 };
    ctx.globalAlpha = Math.max(...badge.lines.map((line) => lineAlpha(line, scene)));
    drawLineBadge(ctx, centre, badge.kind, badge.lines.length, radius, far, colors);
  }
  const shown = scene.lines.flatMap((line) => {
    const position = foldedIds.has(line.id) ? undefined : linePosition(line, scene.nowMs);
    if (position === undefined) {
      return [];
    }
    const at = toScreen(camera, size, position);
    const outside = at.x < -radius || at.y < -radius || at.x > size.width + radius || at.y > size.height + radius;
    return outside ? [] : [{ line, at }];
  });
  const placed = mergeTouchingBadges(shown, radius * BADGE_MERGE_REACH, scene.selectedLineId);
  const picked = placed.filter((badge) => badge.line.id === scene.selectedLineId);
  for (const { line, at, count } of [...placed.filter((badge) => badge.line.id !== scene.selectedLineId), ...picked]) {
    ctx.globalAlpha = lineAlpha(line, scene);
    if (line.id === scene.selectedLineId) {
      ctx.beginPath();
      ctx.arc(at.x, at.y, radius + 3.5, 0, Math.PI * 2);
      ctx.lineWidth = 4.5;
      ctx.strokeStyle = colors.outline;
      ctx.stroke();
      ctx.lineWidth = 2.5;
      ctx.strokeStyle = colors.marker;
      ctx.stroke();
    }
    drawLineBadge(ctx, at, lineKind(line), count, radius, far, colors);
  }
  ctx.restore();
}

/** A troop badge on a line, with its number in a bubble when it stands for more than one movement. */
function drawLineBadge(
  ctx: CanvasRenderingContext2D,
  centre: Point,
  kind: MarkerKind,
  count: number,
  radius: number,
  far: boolean,
  colors: MapColors,
): void {
  drawTroopBadge(ctx, centre, kind, radius, far, colors);
  const text = markerCount(count);
  if (far || text === '' || radius < BADGE_COUNT_MIN_RADIUS) {
    return;
  }
  // on the badge's outer top corner, like the village markers' numbers
  ctx.font = countFont(radius);
  const at = { x: centre.x + radius * 0.85, y: centre.y - radius * 0.8 };
  drawCountBubble(ctx, text, at, countBubbleHalf(ctx, text, radius), radius, colors);
}

/** The canvas and DRAW_MARGIN around it. */
function drawArea(size: Size): Rect {
  return growRect({ left: 0, top: 0, width: size.width, height: size.height }, DRAW_MARGIN);
}

/** False when the box around `from` and `to` is clear of `area`: then the segment between them is too. */
function boxesMeet(from: Point, to: Point, area: Rect): boolean {
  return (
    Math.max(from.x, to.x) >= area.left &&
    Math.min(from.x, to.x) <= area.left + area.width &&
    Math.max(from.y, to.y) >= area.top &&
    Math.min(from.y, to.y) <= area.top + area.height
  );
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

/**
 * A village whose banner chance the player used (v1.3, the farm list's "Banner from …" pill): a
 * small faded banner with a swallowtail on a pole at the top left of the field, across from the NPC
 * pennant. Only on the detailed map; the village panel says when the chance is back.
 */
function drawUsedBanner(ctx: CanvasRenderingContext2D, middle: Point, tile: number, colors: MapColors): void {
  const poleX = middle.x - tile * 0.34;
  const top = middle.y - tile * 0.4;
  const width = tile * 0.16;
  const height = tile * 0.27;
  const line = Math.max(1, tile / 32);
  ctx.lineWidth = line;
  ctx.lineJoin = 'round';
  ctx.strokeStyle = colors.outline;
  ctx.beginPath();
  ctx.moveTo(poleX, middle.y + tile * 0.1);
  ctx.lineTo(poleX, top);
  ctx.lineTo(poleX - width, top);
  ctx.stroke();
  ctx.beginPath();
  ctx.moveTo(poleX, top);
  ctx.lineTo(poleX - width, top);
  ctx.lineTo(poleX - width, top + height);
  ctx.lineTo(poleX - width / 2, top + height * 0.75);
  ctx.lineTo(poleX, top + height);
  ctx.closePath();
  ctx.globalAlpha = 0.8;
  ctx.fillStyle = colors.bannerUsed;
  ctx.fill();
  ctx.globalAlpha = 1;
  ctx.stroke();
}

/** The note's sheet of paper, in tiles: right and bottom edge from the field's middle, its width. */
const NOTE_MARK = { edge: 0.46, width: 0.24, minWidth: 8, dot: 0.16, dotAt: 0.32 } as const;

/**
 * A village with the player's own note (1.4, 02-game-rules §23): a small sheet of paper with a
 * folded corner at the bottom right of the field, the one corner nothing else uses (used banner
 * and movement badges top left, NPC pennant and return badge top right, folded line badges bottom
 * left). Far out, where villages are squares, a paper-coloured dot.
 */
function drawNoteMark(ctx: CanvasRenderingContext2D, middle: Point, tile: number, colors: MapColors): void {
  ctx.lineWidth = Math.max(1, tile / 40);
  ctx.strokeStyle = colors.outline;
  ctx.fillStyle = colors.note;
  if (tile < HOUSES_MIN_TILE) {
    ctx.beginPath();
    ctx.arc(
      middle.x + tile * NOTE_MARK.dotAt,
      middle.y + tile * NOTE_MARK.dotAt,
      Math.max(2, tile * NOTE_MARK.dot),
      0,
      Math.PI * 2,
    );
    ctx.fill();
    ctx.stroke();
    return;
  }
  const width = Math.max(NOTE_MARK.minWidth, tile * NOTE_MARK.width);
  const height = width * 1.2;
  const right = middle.x + tile * NOTE_MARK.edge;
  const bottom = middle.y + tile * NOTE_MARK.edge;
  const left = right - width;
  const top = bottom - height;
  const fold = width * 0.35;
  ctx.lineJoin = 'round';
  ctx.beginPath();
  ctx.moveTo(left, top);
  ctx.lineTo(right - fold, top);
  ctx.lineTo(right, top + fold);
  ctx.lineTo(right, bottom);
  ctx.lineTo(left, bottom);
  ctx.closePath();
  ctx.fill();
  ctx.stroke();
  // the folded corner and two lines of writing
  ctx.beginPath();
  ctx.moveTo(right - fold, top);
  ctx.lineTo(right - fold, top + fold);
  ctx.lineTo(right, top + fold);
  for (const share of [0.5, 0.72]) {
    ctx.moveTo(left + width * 0.2, top + height * share);
    ctx.lineTo(right - width * 0.2, top + height * share);
  }
  ctx.stroke();
}

/**
 * A banner keep far out, where it is a square (v1.3, 02-game-rules §22): a gold star at the top of
 * the field, whoever owns it, so the ring of keeps around the centre shows on the whole map. Nearer,
 * the fort itself shows (drawKeep).
 */
function drawKeepStar(ctx: CanvasRenderingContext2D, middle: Point, tile: number, colors: MapColors): void {
  const outer = Math.max(3, tile * 0.13);
  const centre = { x: middle.x, y: middle.y - Math.max(outer, tile * 0.36) };
  drawStar(ctx, centre, outer, colors, Math.max(1, tile / 40));
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
