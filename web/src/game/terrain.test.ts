import { describe, expect, it } from 'vitest';
import { hash, landOf, moundOf, riverFields, riversOf, terrainAt, TREE_MIN_FOREST, treesOf } from './terrain';

/** Every field of a square area. */
function fields(size: number): Array<{ x: number; y: number }> {
  return Array.from({ length: size * size }, (_, i) => ({ x: i % size, y: Math.floor(i / size) }));
}

describe('hash', () => {
  it('gives the same number in [0, 1) for the same inputs, and different ones for others', () => {
    expect(hash(3, 4, 5)).toBe(hash(3, 4, 5));
    const values = fields(30).map(({ x, y }) => hash(x, y, 1));
    expect(values.every((value) => value >= 0 && value < 1)).toBe(true);
    expect(new Set(values).size).toBe(values.length);
    expect(hash(3, 4, 5)).not.toBe(hash(3, 4, 6));
  });
});

describe('terrainAt', () => {
  const land = fields(120).map(({ x, y }) => terrainAt(x, y));

  it('is the same for everyone: made from the coordinates alone', () => {
    expect(terrainAt(17, 42)).toEqual(terrainAt(17, 42));
  });

  it('stays in 0–1 and never puts forest on a hilly field', () => {
    for (const { forest, hills } of land) {
      expect(forest).toBeGreaterThanOrEqual(0);
      expect(forest).toBeLessThanOrEqual(1);
      expect(hills).toBeGreaterThanOrEqual(0);
      expect(hills).toBeLessThanOrEqual(1);
      expect(forest > 0 && hills > 0.15).toBe(false);
    }
  });

  it('leaves most of the land open, with some woods and some hills', () => {
    const share = (test: (field: { forest: number; hills: number }) => boolean) =>
      land.filter(test).length / land.length;
    expect(share((field) => field.forest > 0.2)).toBeGreaterThan(0.05);
    expect(share((field) => field.forest > 0.2)).toBeLessThan(0.3);
    expect(share((field) => field.hills > 0.2)).toBeGreaterThan(0.05);
    expect(share((field) => field.hills > 0.2)).toBeLessThan(0.3);
    expect(share((field) => field.forest === 0 && field.hills === 0)).toBeGreaterThan(0.5);
  });
});

describe('treesOf', () => {
  it('has no trees below the forest threshold and 1 to 4 above it', () => {
    expect(treesOf(5, 5, 0)).toEqual([]);
    expect(treesOf(5, 5, TREE_MIN_FOREST - 0.01)).toEqual([]);
    expect(treesOf(5, 5, TREE_MIN_FOREST)).toHaveLength(1);
    expect(treesOf(5, 5, 1)).toHaveLength(4);
  });

  it('stands its trees inside the field, back ones first, all of one kind', () => {
    for (const { x, y } of fields(40)) {
      const trees = treesOf(x, y, 1);
      for (const tree of trees) {
        expect(tree.dx).toBeGreaterThan(0.1);
        expect(tree.dx).toBeLessThan(0.9);
        expect(tree.dy).toBeGreaterThan(0.1);
        expect(tree.dy).toBeLessThan(0.9);
        expect(tree.size).toBeLessThan(0.3);
      }
      expect(trees.map((tree) => tree.dy)).toEqual(trees.map((tree) => tree.dy).sort((a, b) => a - b));
      expect(new Set(trees.map((tree) => tree.conifer)).size).toBe(1);
    }
  });

  it('has stands of firs as well as broadleaf woods', () => {
    const kinds = new Set(fields(60).map(({ x, y }) => treesOf(x, y, 1)[0]?.conifer));
    expect(kinds).toEqual(new Set([true, false]));
  });
});

describe('moundOf', () => {
  it('puts no mound on flat land', () => {
    expect(fields(30).every(({ x, y }) => moundOf(x, y, 0) === undefined)).toBe(true);
    expect(fields(30).every(({ x, y }) => moundOf(x, y, 0.2) === undefined)).toBe(true);
  });

  it('puts one on most of the hilliest fields, its foot inside the field', () => {
    const mounds = fields(30).map(({ x, y }) => moundOf(x, y, 1));
    expect(mounds.filter((mound) => mound !== undefined).length / mounds.length).toBeGreaterThan(0.85);
    for (const mound of mounds) {
      if (mound !== undefined) {
        expect(mound.cx).toBeGreaterThanOrEqual(0.3);
        expect(mound.cx).toBeLessThanOrEqual(0.7);
        expect(mound.cy).toBeGreaterThanOrEqual(0.6);
        expect(mound.cy).toBeLessThanOrEqual(0.85);
      }
    }
  });
});

describe('landOf', () => {
  it('is the land of each of its fields', () => {
    const land = landOf(40, 60, 20);
    for (const { x, y } of fields(20)) {
      const field = terrainAt(40 + x, 60 + y);
      expect(land.forest[y * 20 + x]).toBeCloseTo(field.forest, 5);
      expect(land.hills[y * 20 + x]).toBeCloseTo(field.hills, 5);
    }
    land.trees.forEach((row, y) => {
      const expected = fields(20)
        .filter((field) => field.y === 0)
        .flatMap(({ x }) =>
          treesOf(40 + x, 60 + y, terrainAt(40 + x, 60 + y).forest).map((tree) => ({ x: 40 + x, tree })),
        );
      expect(row).toEqual(expected);
    });
    land.mounds.forEach((row, y) => {
      const expected = fields(20)
        .filter((field) => field.y === 0)
        .flatMap(({ x }) => {
          const mound = moundOf(40 + x, 60 + y, terrainAt(40 + x, 60 + y).hills);
          return mound === undefined ? [] : [{ x: 40 + x, mound }];
        });
      expect(row).toEqual(expected);
    });
    expect(land.trees.flat().length).toBeGreaterThan(0);
  });
});

describe('riversOf', () => {
  it('gives the same two rivers for the same map size', () => {
    expect(riversOf(100)).toEqual(riversOf(100));
    expect(riversOf(100)).toHaveLength(2);
    expect(riversOf(100)).not.toEqual(riversOf(101));
  });

  it.each([20, 50, 100, 237, 500])('runs from one edge across the map and out at another (%i fields)', (mapSize) => {
    const [fromTop, fromRight] = riversOf(mapSize);
    expect(fromTop?.[0]?.y).toBe(0);
    expect(fromRight?.[0]?.x).toBe(mapSize);
    for (const river of [fromTop ?? [], fromRight ?? []]) {
      const end = river.at(-1);
      expect(end !== undefined && (end.x < 0 || end.y < 0 || end.x > mapSize || end.y > mapSize)).toBe(true);
      expect(river.every(({ x, y }) => x > -2 && y > -2 && x < mapSize + 2 && y < mapSize + 2)).toBe(true);
      // across the map, not just a bend at its edge
      expect(river.length * 0.8).toBeGreaterThan(mapSize / 2);
    }
  });
});

describe('riverFields', () => {
  it('holds every field inside the map that a river runs through, and no others', () => {
    const rivers = riversOf(100);
    const wet = riverFields(100, rivers);
    for (const point of rivers.flat()) {
      const x = Math.floor(point.x);
      const y = Math.floor(point.y);
      if (x >= 0 && y >= 0 && x < 100 && y < 100) {
        expect(wet.has(y * 100 + x)).toBe(true);
      }
    }
    expect([...wet].every((field) => field >= 0 && field < 100 * 100)).toBe(true);
    // two rivers across the map, a field or two wide where they bend
    expect(wet.size).toBeGreaterThan(100);
    expect(wet.size).toBeLessThan(500);
  });
});
