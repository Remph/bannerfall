import { describe, expect, it } from 'vitest';
import {
  axisValues,
  cameraAt,
  cameraOn,
  chunkArea,
  chunksFor,
  clampCamera,
  clipSegment,
  dashRuns,
  dragViewRect,
  fieldAt,
  growRect,
  hitRadius,
  intersectRect,
  mediumLabels,
  middleField,
  moveOverview,
  onRect,
  overviewLabelStep,
  overviewScale,
  overviewShows,
  overviewView,
  panBy,
  pinch,
  revealField,
  steppedTile,
  focusTile,
  TILE_FOCUS,
  TILE_MAX,
  TILE_MIN,
  toMap,
  toScreen,
  villageKind,
  villageNear,
  villageSize,
  viewRect,
  visibleFields,
  wheelAction,
  wheelTile,
  zoomAt,
  type Camera,
} from './mapView';

const size = { width: 400, height: 300 };
const camera: Camera = { x: 50, y: 50, tile: 40 };

describe('toMap / toScreen', () => {
  it('puts the camera point in the middle of the canvas', () => {
    expect(toMap(camera, size, { x: 200, y: 150 })).toEqual({ x: 50, y: 50 });
    expect(toScreen(camera, size, { x: 50, y: 50 })).toEqual({ x: 200, y: 150 });
  });

  it('are each other’s inverse', () => {
    const point = { x: 13, y: 287 };
    const back = toScreen(camera, size, toMap(camera, size, point));
    expect(back.x).toBeCloseTo(point.x);
    expect(back.y).toBeCloseTo(point.y);
  });

  it('finds the field under a point', () => {
    expect(fieldAt(camera, size, { x: 200, y: 150 })).toEqual({ x: 50, y: 50 });
    expect(fieldAt(camera, size, { x: 199, y: 149 })).toEqual({ x: 49, y: 49 });
    expect(fieldAt(camera, size, { x: 0, y: 0 })).toEqual({ x: 45, y: 46 }); // 200 px = 5 fields, 150 px = 3.75
  });
});

describe('cameraOn / middleField', () => {
  it('centres a field', () => {
    expect(cameraOn({ x: 48, y: 52 }, 48)).toEqual({ x: 48.5, y: 52.5, tile: 48 });
    expect(cameraOn({ x: 1, y: 1 }, 500).tile).toBe(TILE_MAX);
  });

  it('gives the field in the middle, on the map', () => {
    expect(middleField({ x: 48.9, y: 52.1, tile: 48 }, 100)).toEqual({ x: 48, y: 52 });
    expect(middleField({ x: 100, y: 0, tile: 48 }, 100)).toEqual({ x: 99, y: 0 });
  });
});

describe('panBy', () => {
  it('moves the camera against the drag', () => {
    expect(panBy(camera, 40, -80, 100)).toEqual({ x: 49, y: 52, tile: 40 });
  });

  it('stops with the map edge in the middle', () => {
    expect(panBy(camera, -10_000, 10_000, 100)).toEqual({ x: 100, y: 0, tile: 40 });
  });
});

describe('clampCamera', () => {
  it('keeps the middle and the zoom in bounds', () => {
    expect(clampCamera({ x: -3, y: 120, tile: 4 }, 100)).toEqual({ x: 0, y: 100, tile: TILE_MIN });
  });
});

describe('zoomAt', () => {
  it('keeps the map point under the pointer', () => {
    const pointer = { x: 320, y: 40 };
    const before = toMap(camera, size, pointer);
    const zoomed = zoomAt(camera, size, pointer, 60, 100);
    const after = toMap(zoomed, size, pointer);
    expect(zoomed.tile).toBe(60);
    expect(after.x).toBeCloseTo(before.x);
    expect(after.y).toBeCloseTo(before.y);
  });

  it('stays within the zoom limits', () => {
    expect(zoomAt(camera, size, { x: 200, y: 150 }, 1000, 100).tile).toBe(TILE_MAX);
    expect(zoomAt(camera, size, { x: 200, y: 150 }, 1, 100)).toEqual({ x: 50, y: 50, tile: TILE_MIN });
  });
});

describe('pinch', () => {
  it('zooms by the change of the finger distance around their midpoint', () => {
    const result = pinch(
      camera,
      size,
      [
        { x: 150, y: 150 },
        { x: 250, y: 150 },
      ],
      [
        { x: 125, y: 150 },
        { x: 275, y: 150 },
      ],
      100,
    );
    expect(result).toEqual({ x: 50, y: 50, tile: 60 }); // distance 100 → 150, midpoint unchanged
  });

  it('pans with the midpoint', () => {
    const result = pinch(
      camera,
      size,
      [
        { x: 100, y: 100 },
        { x: 200, y: 100 },
      ],
      [
        { x: 140, y: 100 },
        { x: 240, y: 100 },
      ],
      100,
    );
    expect(result).toEqual({ x: 49, y: 50, tile: 40 });
  });

  it('only pans when both fingers are on one spot', () => {
    const spot = { x: 100, y: 100 };
    expect(pinch(camera, size, [spot, spot], [spot, { x: 300, y: 100 }], 100).tile).toBe(40);
  });
});

describe('focusTile', () => {
  it('opens a village link at least TILE_FOCUS close, closer if the player’s own zoom already is', () => {
    expect(focusTile(TILE_MIN)).toBe(TILE_FOCUS);
    expect(focusTile(48)).toBe(TILE_FOCUS);
    expect(focusTile(TILE_MAX)).toBe(TILE_MAX);
  });
});

describe('zoom steps', () => {
  it('multiplies by 1.25 per step, within the limits', () => {
    expect(steppedTile(48, 1)).toBe(60);
    expect(steppedTile(48, -1)).toBeCloseTo(38.4);
    expect(steppedTile(60, 1)).toBe(TILE_MAX);
    expect(steppedTile(9, -1)).toBe(TILE_MIN);
  });

  it('turns wheel pixels into zoom: one notch (100 px) = one step, down = out', () => {
    expect(wheelTile(48, -100)).toBeCloseTo(60);
    expect(wheelTile(48, 100)).toBeCloseTo(38.4);
    expect(wheelTile(48, 0)).toBe(48);
  });

  it('zooms with a vertical wheel and Ctrl + wheel, pans with a sideways scroll', () => {
    const wheel = { deltaX: 0, deltaY: 100, deltaMode: 0, ctrlKey: false };
    expect(wheelAction(wheel)).toEqual({ kind: 'zoom', pixels: 100 });
    expect(wheelAction({ ...wheel, deltaY: 3, deltaMode: 1 })).toEqual({ kind: 'zoom', pixels: 120 }); // lines
    expect(wheelAction({ ...wheel, deltaY: 1, deltaMode: 2 })).toEqual({ kind: 'zoom', pixels: 300 }); // pages
    expect(wheelAction({ ...wheel, deltaX: 5, deltaY: -2, ctrlKey: true })).toEqual({ kind: 'zoom', pixels: -2 });
    expect(wheelAction({ ...wheel, deltaX: 12, deltaY: -4 })).toEqual({ kind: 'pan', dx: -12, dy: 4 });
    expect(wheelAction({ ...wheel, deltaY: 0 })).toBeUndefined();
  });
});

describe('visibleFields', () => {
  it('lists every field that is at least partly visible', () => {
    // 400 × 300 px at 40 px = 10 × 7.5 fields around (50|50)
    expect(visibleFields(camera, size, 100)).toEqual({ minX: 45, maxX: 54, minY: 46, maxY: 53 });
  });

  it('is cut at the map edge', () => {
    expect(visibleFields({ x: 0, y: 100, tile: 40 }, size, 100)).toEqual({ minX: 0, maxX: 4, minY: 96, maxY: 99 });
  });
});

describe('chunks', () => {
  it('covers a range with 20 × 20 chunks', () => {
    expect(chunksFor({ minX: 15, maxX: 41, minY: 38, maxY: 39 })).toEqual([
      { cx: 0, cy: 1 },
      { cx: 1, cy: 1 },
      { cx: 2, cy: 1 },
    ]);
    expect(chunksFor({ minX: 0, maxX: 19, minY: 0, maxY: 19 })).toEqual([{ cx: 0, cy: 0 }]);
  });

  it('asks the server for the fields of a chunk', () => {
    expect(chunkArea({ cx: 1, cy: 2 })).toEqual({ x: 20, y: 40, w: 20, h: 20 });
  });
});

describe('axisValues', () => {
  it('labels every fifth coordinate', () => {
    expect(axisValues(43, 61)).toEqual([45, 50, 55, 60]);
    expect(axisValues(0, 9)).toEqual([0, 5]);
    expect(axisValues(46, 49)).toEqual([]);
  });

  it('takes another step for the overview grid', () => {
    expect(axisValues(3, 47, 10)).toEqual([10, 20, 30, 40]);
  });
});

describe('villageKind', () => {
  it('tells own, player, NPC lord and barbarian villages apart', () => {
    expect(villageKind({ playerId: 7, npc: false }, 7)).toBe('own');
    expect(villageKind({ playerId: 8, npc: false }, 7)).toBe('player');
    expect(villageKind({ playerId: 9, npc: true }, 7)).toBe('npc');
    expect(villageKind({ playerId: null, npc: false }, 7)).toBe('barbarian');
  });

  it('shows the villages of guild mates in their own colour (1.6.3)', () => {
    expect(villageKind({ playerId: 8, npc: false, guildId: 3 }, 7, 3)).toBe('mate');
    expect(villageKind({ playerId: 8, npc: false, guildId: 4 }, 7, 3)).toBe('player');
    expect(villageKind({ playerId: 8, npc: false, guildId: null }, 7, 3)).toBe('player');
    // without a guild of one's own nobody is a mate, even a player without a guild
    expect(villageKind({ playerId: 8, npc: false, guildId: null }, 7, null)).toBe('player');
    // the own villages and the lords keep their colours
    expect(villageKind({ playerId: 7, npc: false, guildId: 3 }, 7, 3)).toBe('own');
    expect(villageKind({ playerId: 9, npc: true, guildId: 3 }, 7, 3)).toBe('npc');
  });
});

describe('mediumLabels', () => {
  const village = (id: number, x: number, y: number, playerId: number | null, points = 100) => ({
    id,
    x,
    y,
    playerId,
    playerName: playerId === null ? null : `P${playerId}`,
    points,
  });

  it('labels own villages and one village per foreign player and 5 × 5 block, the biggest', () => {
    const villages = [
      village(1, 10, 10, 7),
      village(2, 11, 10, 7),
      village(3, 10, 11, 8, 50),
      village(4, 12, 12, 8, 300),
      village(5, 13, 13, 8, 300),
      village(6, 15, 10, 8),
      village(7, 11, 11, 9),
      village(8, 12, 10, null),
    ];
    const labels = mediumLabels(villages, 7, new Set());
    expect(labels.map((label) => [label.village.id, label.own])).toEqual([
      [1, true],
      [2, true],
      [4, false], // the biggest of player 8 in block 2|2; on equal points the lower id
      [6, false], // the next block
      [7, false],
    ]);
  });

  it('leaves out the skipped villages, which may let another one of that player take the label', () => {
    const villages = [village(1, 10, 10, 8, 300), village(2, 11, 10, 8, 100), village(3, 12, 10, 7)];
    const labels = mediumLabels(villages, 7, new Set([1, 3]));
    expect(labels.map((label) => label.village.id)).toEqual([2]);
  });
});

describe('villageSize', () => {
  it('has three sizes by points', () => {
    expect(villageSize(0)).toBe(0);
    expect(villageSize(279)).toBe(0);
    expect(villageSize(280)).toBe(1);
    expect(villageSize(1599)).toBe(1);
    expect(villageSize(1600)).toBe(2);
  });
});

describe('villageNear / hitRadius', () => {
  const villages = [
    { id: 1, x: 10, y: 10 },
    { id: 2, x: 12, y: 10 },
  ];

  it('finds the nearest village within the radius', () => {
    expect(villageNear(villages, { x: 10.5, y: 10.5 }, 0.7)?.id).toBe(1);
    expect(villageNear(villages, { x: 12.2, y: 10.9 }, 0.7)?.id).toBe(2);
    expect(villageNear(villages, { x: 11.5, y: 10.5 }, 0.7)).toBeUndefined();
  });

  it('picks the nearer one when the radius reaches two villages', () => {
    expect(villageNear(villages, { x: 11.2, y: 10.5 }, 3)?.id).toBe(1);
    expect(villageNear(villages, { x: 11.8, y: 10.5 }, 3)?.id).toBe(2);
  });

  it('is the field, but at least 10 px for a mouse and a fingertip for touch', () => {
    expect(hitRadius(48, 'mouse')).toBeCloseTo(Math.SQRT1_2);
    expect(hitRadius(8, 'mouse')).toBe(1.25);
    expect(hitRadius(24, 'touch')).toBe(1);
    expect(hitRadius(8, 'touch')).toBe(3);
    expect(hitRadius(64, 'touch')).toBeCloseTo(Math.SQRT1_2);
  });
});

describe('revealField', () => {
  const cover = { top: 200, right: 300 }; // a panel over the bottom 100 px, 300 px wide

  it('leaves a visible field alone', () => {
    expect(revealField(camera, size, { x: 50, y: 50 }, cover, 100)).toBe(camera); // middle of the field at y = 170
    expect(revealField(camera, size, { x: 53, y: 52 }, cover, 100)).toBe(camera); // right of the panel
  });

  it('moves a hidden field into the middle of the free part', () => {
    const moved = revealField(camera, size, { x: 50, y: 52 }, cover, 100); // its middle is at y = 250
    expect(toScreen(moved, size, { x: 50.5, y: 52.5 }).y).toBeCloseTo(100);
    expect(moved.x).toBe(camera.x);
  });
});

describe('overview map', () => {
  const square = { width: 300, height: 300 };

  it('shows 100 fields across the shorter side, or the whole smaller map', () => {
    expect(overviewScale(square, 500)).toBe(3);
    expect(overviewScale({ width: 390, height: 600 }, 500)).toBeCloseTo(3.9);
    expect(overviewScale(square, 60)).toBe(5);
  });

  it('is centred on the village in the middle of a big map', () => {
    expect(overviewView({ x: 250.5, y: 240.5 }, square, 500)).toEqual({ x: 250.5, y: 240.5, tile: 3 });
  });

  it('stays on the map at its edges', () => {
    expect(overviewView({ x: 10.5, y: 495.5 }, square, 500)).toEqual({ x: 50, y: 450, tile: 3 });
  });

  it('shows the whole map, centred, while the map is smaller than its area', () => {
    expect(overviewView({ x: 12.5, y: 80.5 }, square, 100)).toEqual({ x: 50, y: 50, tile: 3 });
    // a phone in portrait: 100 fields across, the map is shorter than the screen is tall
    const tall = overviewView({ x: 12.5, y: 80.5 }, { width: 390, height: 600 }, 100);
    expect(tall).toEqual({ x: 50, y: 50, tile: 3.9 });
  });

  it('extends along the longer side when the map is big enough', () => {
    // 100 × 150 fields: the middle can go from 75 to 425 vertically
    expect(overviewView({ x: 250, y: 10 }, { width: 300, height: 450 }, 500)).toEqual({ x: 250, y: 75, tile: 3 });
  });

  it('moves by fields and stops at the map edge', () => {
    expect(moveOverview({ x: 250, y: 250 }, 20, -20, square, 500)).toEqual({ x: 270, y: 230 });
    expect(moveOverview({ x: 440, y: 250 }, 20, 0, square, 500)).toEqual({ x: 450, y: 250 });
    // starting from a middle beyond the edge (the stored village middle): first held, then moved
    expect(moveOverview({ x: 10, y: 250 }, 20, 0, square, 500)).toEqual({ x: 70, y: 250 });
    // the whole map is shown: nothing moves
    expect(moveOverview({ x: 50, y: 50 }, 20, 20, square, 100)).toEqual({ x: 50, y: 50 });
  });

  it('knows which map points it shows', () => {
    const overview = overviewView({ x: 250, y: 250 }, square, 500);
    expect(overviewShows(overview, square, { x: 200, y: 300 })).toBe(true);
    expect(overviewShows(overview, square, { x: 199, y: 250 })).toBe(false);
    expect(overviewShows(overview, square, { x: 250, y: 301 })).toBe(false);
  });

  it('converts between fields and overview pixels', () => {
    const overview = overviewView({ x: 250, y: 250 }, square, 500);
    expect(toScreen(overview, square, { x: 200, y: 200 })).toEqual({ x: 0, y: 0 });
    expect(toScreen(overview, square, { x: 210, y: 230 })).toEqual({ x: 30, y: 90 });
    expect(toMap(overview, square, { x: 150, y: 150 })).toEqual({ x: 250, y: 250 });
  });

  it('marks the part the detailed map shows', () => {
    const overview = overviewView({ x: 250, y: 250 }, square, 500);
    // the detailed map: 400 × 300 px at 40 px per field = 10 × 7.5 fields around 240|260
    const rect = viewRect({ x: 240, y: 260, tile: 40 }, size, overview, square);
    expect(rect.left).toBeCloseTo(105); // field 235 → (235 − 200) × 3
    expect(rect.top).toBeCloseTo(168.75); // field 256.25
    expect(rect.width).toBeCloseTo(30);
    expect(rect.height).toBeCloseTo(22.5);
  });

  it('hits the rectangle with some room around it', () => {
    const rect = { left: 100, top: 100, width: 30, height: 20 };
    expect(onRect(rect, { x: 115, y: 110 }, 0)).toBe(true);
    expect(onRect(rect, { x: 135, y: 110 }, 0)).toBe(false);
    expect(onRect(rect, { x: 135, y: 110 }, 10)).toBe(true);
    expect(onRect(rect, { x: 115, y: 89 }, 10)).toBe(false);
  });

  it('moves the detailed map to a click on the overview, keeping the zoom', () => {
    const overview = overviewView({ x: 250, y: 250 }, square, 500);
    const target = toMap(overview, square, { x: 30, y: 90 });
    expect(cameraAt(camera, target, 500)).toEqual({ x: 210, y: 230, tile: 40 });
    // a click beyond the map edge: the middle stops at the edge
    expect(cameraAt(camera, { x: -4, y: 520 }, 500)).toEqual({ x: 0, y: 500, tile: 40 });
  });

  it('drags the detailed map with the rectangle', () => {
    // 6 px on an overview of 3 px per field = 2 fields, the same way as the pointer
    expect(dragViewRect(camera, 6, -3, 3, 100)).toEqual({ x: 52, y: 49, tile: 40 });
    expect(dragViewRect(camera, 600, 0, 3, 100)).toEqual({ x: 100, y: 50, tile: 40 });
  });

  it('labels the grid every 10 fields, or less often when they would crowd', () => {
    expect(overviewLabelStep(5)).toBe(10);
    expect(overviewLabelStep(3)).toBe(20);
    expect(overviewLabelStep(1.5)).toBe(30);
  });

  it('cuts a segment to a rectangle, keeping how far along it the cut part starts', () => {
    const rect = { left: 0, top: 0, width: 100, height: 50 };
    expect(clipSegment({ x: -100, y: 25 }, { x: 300, y: 25 }, rect)).toEqual({
      from: { x: 0, y: 25 },
      to: { x: 100, y: 25 },
      skipped: 100,
    });
    // inside: unchanged
    expect(clipSegment({ x: 10, y: 10 }, { x: 20, y: 30 }, rect)).toEqual({
      from: { x: 10, y: 10 },
      to: { x: 20, y: 30 },
      skipped: 0,
    });
    // a diagonal from far away
    const part = clipSegment({ x: -3000, y: -3000 }, { x: 25, y: 25 }, rect);
    expect(part?.from.x).toBeCloseTo(0);
    expect(part?.from.y).toBeCloseTo(0);
    expect(part?.skipped).toBeCloseTo(3000 * Math.SQRT2);
    // beside the rectangle, and parallel to a side outside it
    expect(clipSegment({ x: -50, y: 60 }, { x: 60, y: 200 }, rect)).toBeUndefined();
    expect(clipSegment({ x: -10, y: 60 }, { x: 200, y: 60 }, rect)).toBeUndefined();
  });

  it('works out the dashes of a cut line where they lie on the whole line', () => {
    // dashes of 4 every 10 px from the line's start: the first one cut by the skipped 12 px
    expect(dashRuns(25, 12, 4, 6)).toEqual([
      [0, 2],
      [8, 12],
      [18, 22],
    ]);
    // a dash just ending where the part starts leaves a sliver of 0, which the map leaves out
    expect(dashRuns(5, 4, 4, 6)[0]).toEqual([0, 0]);
    // the last dash is cut at the end
    expect(dashRuns(20, 0, 4, 6)).toEqual([
      [0, 4],
      [10, 14],
      [20, 20],
    ]);
    // dots (on 0): every 5 px of the whole line, the ones before the part skipped
    expect(dashRuns(12, 7, 0, 5)).toEqual([
      [3, 3],
      [8, 8],
    ]);
    // a long way far off: the dots keep their place and the count stays small
    const far = dashRuns(800, 123_456.5, 0, 4.2);
    expect(far.length).toBeGreaterThanOrEqual(190);
    expect(far.length).toBeLessThanOrEqual(191);
    const first = (far[0]?.[0] ?? Number.NaN) + 123_456.5;
    expect(first / 4.2).toBeCloseTo(Math.round(first / 4.2), 6);
    // nothing for a broken pattern
    expect(dashRuns(10, 0, 0, 0)).toEqual([]);
  });

  it('grows a rectangle and cuts it to another', () => {
    const canvas = growRect({ left: 0, top: 0, width: 400, height: 800 }, 64);
    expect(canvas).toEqual({ left: -64, top: -64, width: 528, height: 928 });
    // the map's edge far off the canvas lands just outside it
    expect(intersectRect({ left: -5000, top: 100, width: 9000, height: 9000 }, canvas)).toEqual({
      left: -64,
      top: 100,
      width: 528,
      height: 764,
    });
    expect(intersectRect({ left: 900, top: 0, width: 10, height: 10 }, canvas).width).toBe(0);
  });
});
