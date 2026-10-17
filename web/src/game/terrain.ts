/*
 * The map's landscape (v1.3): forests, hills and rivers made from the coordinates alone, so every
 * player sees the same land and nothing is stored or sent. Pure decoration with no effect on the
 * game; mapDraw.ts draws it under the villages.
 */

/** Odd 32-bit multipliers that scatter the bits of the coordinates and the salt. */
const MIX_X = 374761393;
const MIX_Y = 668265263;
const MIX_SALT = 1442695041;
const MIX_FINAL = 1274126177;
const UINT32_RANGE = 2 ** 32;

/** A number in [0, 1) from two coordinates and a salt; the same inputs always give the same number. */
export function hash(x: number, y: number, salt: number): number {
  let h = Math.imul(x | 0, MIX_X) ^ Math.imul(y | 0, MIX_Y) ^ Math.imul(salt | 0, MIX_SALT);
  h = Math.imul(h ^ (h >>> 13), MIX_FINAL);
  h ^= h >>> 16;
  return (h >>> 0) / UINT32_RANGE;
}

/** Eases 0–1 so value noise has no creases at the grid points. */
function smooth(t: number): number {
  return t * t * (3 - 2 * t);
}

function lerp(a: number, b: number, t: number): number {
  return a + (b - a) * t;
}

/** Value noise: soft rises and dips of numbers in [0, 1), about `scale` fields apart. */
function noise(x: number, y: number, scale: number, salt: number): number {
  const fx = x / scale;
  const fy = y / scale;
  const x0 = Math.floor(fx);
  const y0 = Math.floor(fy);
  const tx = smooth(fx - x0);
  const ty = smooth(fy - y0);
  return lerp(
    lerp(hash(x0, y0, salt), hash(x0 + 1, y0, salt), tx),
    lerp(hash(x0, y0 + 1, salt), hash(x0 + 1, y0 + 1, salt), tx),
    ty,
  );
}

/** Broad patches about 9 fields across, with smaller detail about 4 fields across in them. */
const BROAD = { scale: 9, weight: 0.62 } as const;
const DETAIL = { scale: 4, weight: 0.38, salt: 7 } as const;

function layered(x: number, y: number, salt: number): number {
  return BROAD.weight * noise(x, y, BROAD.scale, salt) + DETAIL.weight * noise(x, y, DETAIL.scale, salt + DETAIL.salt);
}

/**
 * Separate salts give forests, hills, firs and each detail their own pattern. Changing one changes
 * the land of every world, so they stay as they are.
 */
const SALT = {
  forest: 101,
  hills: 211,
  treeX: 300,
  treeY: 400,
  treeSize: 500,
  mound: 600,
  firs: 707,
  riverStart: 9,
  riverBend: 900,
} as const;

/** The part of the noise above `from` makes forest or hills, reaching full strength `span` higher. */
const HILLS = { from: 0.62, span: 0.25 } as const;
const FOREST = { from: 0.6, span: 0.22 } as const;
/** From this much hill on, a field has no forest. */
const HILLS_CLEAR_FOREST = 0.15;

export interface FieldTerrain {
  /** 0 = open land, up to 1 = thick forest. */
  forest: number;
  /** 0 = flat, up to 1 = hilly. */
  hills: number;
}

/** What grows or rises on a field. Hills and forest never share a field. */
export function terrainAt(x: number, y: number): FieldTerrain {
  const hills = Math.min(1, Math.max(0, (layered(x, y, SALT.hills) - HILLS.from) / HILLS.span));
  const forest = Math.min(1, Math.max(0, (layered(x, y, SALT.forest) - FOREST.from) / FOREST.span));
  return { forest: hills > HILLS_CLEAR_FOREST ? 0 : forest, hills };
}

export interface Tree {
  /** Where its foot stands inside the field, 0–1 from the field's left and top edge. */
  dx: number;
  dy: number;
  /** Its size in fields. */
  size: number;
  conifer: boolean;
}

/** Below this much forest a field has no trees; thick forest has up to 4 (1 + 3.4, rounded). */
export const TREE_MIN_FOREST = 0.12;
const TREES_PER_FOREST = 3.4;
/** Stands of firs where this noise is high, about 7 fields across. */
const FIRS = { scale: 7, from: 0.55 } as const;
/** Tree feet stay this far inside the field, so crowns rarely cross into the next one. */
const TREE_X = { min: 0.14, max: 0.86 } as const;
const TREE_Y = { min: 0.16, max: 0.84 } as const;
const TREE_SIZE = { min: 0.15, max: 0.23, forestBonus: 0.04 } as const;

/** The trees of a field, back ones first: all firs or all broadleaf trees. */
export function treesOf(x: number, y: number, forest: number): Tree[] {
  const count = forest < TREE_MIN_FOREST ? 0 : Math.round(1 + forest * TREES_PER_FOREST);
  const conifer = noise(x, y, FIRS.scale, SALT.firs) > FIRS.from;
  return Array.from({ length: count }, (_, i) => ({
    dx: lerp(TREE_X.min, TREE_X.max, hash(x, y, SALT.treeX + i)),
    dy: lerp(TREE_Y.min, TREE_Y.max, hash(x, y, SALT.treeY + i)),
    size: lerp(TREE_SIZE.min, TREE_SIZE.max, hash(x, y, SALT.treeSize + i)) + TREE_SIZE.forestBonus * forest,
    conifer,
  })).sort((a, b) => a.dy - b.dy);
}

export interface Mound {
  /** The middle of its foot inside the field, 0–1 from the field's left and top edge. */
  cx: number;
  cy: number;
  /** Half its width and its height, in fields. */
  w: number;
  h: number;
}

/** Hilly fields from this much hill on can have a mound; the chance grows with the hills. */
const MOUND = { minHills: 0.2, chance: 0.35, chancePerHills: 0.6 } as const;
/** In the hilliest parts, half the mounds are big ones. */
const BIG_MOUND = { minHills: 0.55, chance: 0.5, w: 0.62, h: 0.42 } as const;
const MOUND_X = { min: 0.3, max: 0.7 } as const;
const MOUND_Y = { min: 0.6, max: 0.85 } as const;
const MOUND_W = { min: 0.28, max: 0.46, perHills: 0.1 } as const;
const MOUND_H = { min: 0.16, max: 0.28, perHills: 0.1 } as const;

/** The mound on a hilly field, if it has one. */
export function moundOf(x: number, y: number, hills: number): Mound | undefined {
  const roll = (detail: number) => hash(x, y, SALT.mound + detail * 10);
  if (hills <= MOUND.minHills || roll(0) >= MOUND.chance + hills * MOUND.chancePerHills) {
    return undefined;
  }
  const big = hills > BIG_MOUND.minHills && roll(1) < BIG_MOUND.chance;
  return {
    cx: lerp(MOUND_X.min, MOUND_X.max, roll(2)),
    cy: lerp(MOUND_Y.min, MOUND_Y.max, roll(3)),
    w: big ? BIG_MOUND.w : lerp(MOUND_W.min, MOUND_W.max, roll(4)) + MOUND_W.perHills * hills,
    h: big ? BIG_MOUND.h : lerp(MOUND_H.min, MOUND_H.max, roll(5)) + MOUND_H.perHills * hills,
  };
}

export interface Land {
  /** Forest and hills of each field, row by row from the top left corner. */
  forest: Float32Array;
  hills: Float32Array;
  /** Per row from the top: the trees with their field's x, each field's back ones first. */
  trees: Array<Array<{ x: number; tree: Tree }>>;
  /** Per row from the top: the mounds with their field's x. */
  mounds: Array<Array<{ x: number; mound: Mound }>>;
}

/** The land of the `size` × `size` fields from `x0`|`y0` on, worked out once for drawing it as a piece. */
export function landOf(x0: number, y0: number, size: number): Land {
  const land: Land = {
    forest: new Float32Array(size * size),
    hills: new Float32Array(size * size),
    trees: [],
    mounds: [],
  };
  for (let row = 0; row < size; row++) {
    const trees: Land['trees'][number] = [];
    const mounds: Land['mounds'][number] = [];
    for (let column = 0; column < size; column++) {
      const x = x0 + column;
      const y = y0 + row;
      const { forest, hills } = terrainAt(x, y);
      land.forest[row * size + column] = forest;
      land.hills[row * size + column] = hills;
      for (const tree of treesOf(x, y, forest)) {
        trees.push({ x, tree });
      }
      const mound = moundOf(x, y, hills);
      if (mound !== undefined) {
        mounds.push({ x, mound });
      }
    }
    land.trees.push(trees);
    land.mounds.push(mounds);
  }
  return land;
}

/** A river takes steps this long (in fields), bends by up to a quarter radian per step, and drifts back to its course. */
const RIVER = { step: 0.8, bend: 0.5, bendScale: 6, pull: 0.08, maxStepsPerField: 3 } as const;

/**
 * The two rivers of a map, each a line of points (in fields) that meanders from one edge to another:
 * one from the top edge down, one from the right edge to the left. The map size is the only input,
 * so a world's rivers are the same for everyone.
 */
export function riversOf(mapSize: number): Array<Array<{ x: number; y: number }>> {
  const starts = [
    { x: mapSize * lerp(0.18, 0.38, hash(mapSize, 1, SALT.riverStart)), y: 0, angle: Math.PI / 2 + 0.25 },
    { x: mapSize, y: mapSize * lerp(0.55, 0.75, hash(mapSize, 2, SALT.riverStart)), angle: Math.PI - 0.15 },
  ];
  return starts.map((start, index) => {
    const points = [{ x: start.x, y: start.y }];
    let { x, y, angle } = start;
    // a walk, not a mapping: each step starts where the last one ended
    for (let step = 0; step < mapSize * RIVER.maxStepsPerField; step++) {
      angle += (noise(step, index * 50, RIVER.bendScale, SALT.riverBend + index) - 0.5) * RIVER.bend;
      angle = lerp(angle, start.angle, RIVER.pull);
      x += Math.cos(angle) * RIVER.step;
      y += Math.sin(angle) * RIVER.step;
      points.push({ x, y });
      if (x < -1 || y < -1 || x > mapSize + 1 || y > mapSize + 1) {
        break;
      }
    }
    return points;
  });
}

/**
 * The fields the rivers run through, each as `y * mapSize + x`: no trees or mounds stand in the
 * water. Each stretch between two points is followed in small steps, so no field it crosses is missed.
 */
export function riverFields(mapSize: number, rivers: Array<Array<{ x: number; y: number }>>): Set<number> {
  const fields = new Set<number>();
  for (const river of rivers) {
    river.forEach((point, index) => {
      const next = river[index + 1] ?? point;
      for (let part = 0; part < 4; part++) {
        const x = Math.floor(lerp(point.x, next.x, part / 4));
        const y = Math.floor(lerp(point.y, next.y, part / 4));
        if (x >= 0 && y >= 0 && x < mapSize && y < mapSize) {
          fields.add(y * mapSize + x);
        }
      }
    });
  }
  return fields;
}
