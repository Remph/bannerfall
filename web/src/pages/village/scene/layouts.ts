import type { BuildingType } from '../../../game/gameData';

export interface Point {
  x: number;
  y: number;
}

/**
 * One arrangement of the village picture, in its own viewBox units. Every building stands at its
 * spot (the middle of its front edge); its plate hangs below the spot.
 */
export interface SceneLayout {
  width: number;
  height: number;
  /** Size of the drawings (they are drawn for the wide layout). */
  scale: number;
  /** Font size of the plates. */
  font: number;
  /** The palisade: an oval around the yard. */
  wall: { cx: number; cy: number; rx: number; ry: number };
  spots: Record<BuildingType, Point>;
  /**
   * Empty places inside the wall, kept for buildings a later version adds: a new building takes one
   * of these instead of moving the others. Nothing is drawn there.
   */
  free: readonly Point[];
  /** Trees as x, y and radius. */
  trees: readonly (readonly [number, number, number])[];
  paths: readonly string[];
}

/** Tablets and desktop (from 640 px): the village spread out sideways. */
export const WIDE: SceneLayout = {
  width: 960,
  height: 570,
  scale: 1,
  font: 14,
  wall: { cx: 480, cy: 290, rx: 335, ry: 225 },
  spots: {
    HEADQUARTERS: { x: 480, y: 285 },
    ACADEMY: { x: 380, y: 175 },
    BANNER_HALL: { x: 585, y: 175 },
    BARRACKS: { x: 250, y: 285 },
    SMITHY: { x: 712, y: 285 },
    STABLE: { x: 270, y: 395 },
    RALLY_POINT: { x: 480, y: 385 },
    MARKET: { x: 690, y: 395 },
    WORKSHOP: { x: 372, y: 478 },
    WAREHOUSE: { x: 592, y: 478 },
    WOODCUTTER: { x: 92, y: 140 },
    HIDING_PLACE: { x: 70, y: 330 },
    CLAY_PIT: { x: 100, y: 500 },
    IRON_MINE: { x: 868, y: 155 },
    FARM: { x: 862, y: 500 },
    WALL: { x: 480, y: 530 },
  },
  free: [
    { x: 262, y: 180 },
    { x: 700, y: 180 },
  ],
  trees: [
    [30, 60, 20],
    [70, 46, 16],
    [150, 58, 18],
    [190, 40, 14],
    [24, 200, 18],
    [150, 210, 15],
    [40, 410, 16],
    [160, 300, 13],
    [20, 290, 12],
    [130, 400, 12],
    [930, 300, 18],
    [890, 330, 14],
    [940, 380, 13],
    [760, 50, 15],
    [800, 36, 18],
    [700, 30, 12],
    [250, 30, 13],
    [930, 60, 12],
    [200, 545, 12],
    [760, 548, 13],
    [820, 280, 12],
  ],
  paths: [
    'M480 570 C 480 500, 480 460, 480 395',
    'M480 380 C 400 380, 330 395, 285 400',
    'M480 380 C 560 380, 640 395, 680 400',
    'M480 470 C 430 470, 400 478, 380 482',
    'M480 470 C 530 470, 570 478, 590 482',
    'M480 300 C 420 300, 330 290, 260 290',
    'M480 300 C 540 300, 640 290, 705 290',
    'M440 300 C 420 250, 400 210, 385 180',
    'M520 300 C 545 250, 565 210, 580 180',
  ],
};

/**
 * Phones: the whole village on the first screen. The resource sites and the hiding place stand
 * outside the wall, in the corners and between the woodcutter and the iron mine.
 */
export const COMPACT: SceneLayout = {
  width: 400,
  height: 600,
  scale: 0.64,
  font: 12,
  wall: { cx: 200, cy: 318, rx: 172, ry: 236 },
  spots: {
    ACADEMY: { x: 130, y: 172 },
    BANNER_HALL: { x: 272, y: 172 },
    BARRACKS: { x: 80, y: 282 },
    HEADQUARTERS: { x: 200, y: 278 },
    SMITHY: { x: 322, y: 282 },
    STABLE: { x: 84, y: 372 },
    RALLY_POINT: { x: 200, y: 366 },
    MARKET: { x: 316, y: 372 },
    WORKSHOP: { x: 128, y: 458 },
    WAREHOUSE: { x: 272, y: 462 },
    HIDING_PLACE: { x: 200, y: 48 },
    WALL: { x: 200, y: 548 },
    WOODCUTTER: { x: 56, y: 62 },
    IRON_MINE: { x: 344, y: 66 },
    CLAY_PIT: { x: 52, y: 566 },
    FARM: { x: 346, y: 566 },
  },
  free: [{ x: 200, y: 462 }],
  trees: [
    [132, 26, 10],
    [270, 28, 10],
    [12, 150, 11],
    [390, 160, 11],
    [10, 420, 10],
    [392, 430, 10],
    [130, 590, 9],
    [270, 592, 9],
  ],
  paths: [
    'M200 600 C 200 560, 200 520, 200 376',
    'M200 360 C 160 360, 120 372, 92 378',
    'M200 360 C 240 360, 280 372, 308 378',
    'M200 440 C 170 445, 150 460, 134 466',
    'M200 440 C 230 445, 250 460, 266 466',
    'M200 290 C 160 290, 120 288, 84 288',
    'M200 290 C 240 290, 280 288, 318 288',
  ],
};

/** The buildings that stand outside the palisade. */
export const outsideWall: ReadonlySet<BuildingType> = new Set([
  'WOODCUTTER',
  'CLAY_PIT',
  'IRON_MINE',
  'FARM',
  'HIDING_PLACE',
]);

/** Whether a point lies inside the wall's oval (1 = on the wall). */
export function wallDistance(layout: SceneLayout, point: Point): number {
  const { cx, cy, rx, ry } = layout.wall;
  return Math.hypot((point.x - cx) / rx, (point.y - cy) / ry);
}
