import { describe, expect, it } from 'vitest';
import { replayStates } from '../../game/chronicle';
import { chronicleFixture } from '../../testing/chronicleFixture';
import type { MapColors } from '../map/mapDraw';
import { drawReplay, sideColor, viewOf } from './replayDraw';

/** A 2D context that only records the squares it fills and the rings it strokes. */
function recorder() {
  const squares: { x: number; y: number; color: string }[] = [];
  let rings = 0;
  const ctx = {
    fillStyle: '',
    strokeStyle: '',
    lineWidth: 1,
    globalAlpha: 1,
    fillRect(x: number, y: number) {
      squares.push({ x, y, color: String(ctx.fillStyle) });
    },
    strokeRect() {},
    save() {},
    restore() {},
    beginPath() {},
    rect() {},
    clip() {},
    fill() {},
    moveTo() {},
    lineTo() {},
    closePath() {},
    arc() {
      rings += 1;
    },
    stroke() {},
  };
  return { ctx: ctx as unknown as CanvasRenderingContext2D, squares, rings: () => rings };
}

const colors = {
  grass: 'grass',
  edge: 'edge',
  outline: 'outline',
  marker: 'marker',
  kinds: { own: 'own', mate: 'mate', player: 'player', npc: 'npc', barbarian: 'barbarian' },
} as unknown as MapColors;

class FakePath {
  moveTo() {}
  arc() {}
}

describe('the replay map (2.1 I1)', () => {
  it('draws each village of a day in its owner colour, the reader in the own colour', () => {
    (globalThis as { Path2D?: unknown }).Path2D = FakePath;
    const states = replayStates(chronicleFixture.map);
    const { ctx, squares } = recorder();

    drawReplay(ctx, {
      side: 100,
      map: chronicleFixture.map,
      state: states[0]!,
      colors,
      theme: 'light',
      highlight: null,
    });

    // the ground first, then one square per village (10 px fields, the middle at x + 5)
    const villages = squares.slice(1);
    expect(villages.map((square) => square.color)).toEqual(['own', expect.stringMatching(/^hsl/), 'barbarian']);
    expect(villages[0]).toMatchObject({ x: 20, y: 20 });
  });

  it('shows the conquest on the day after it and rings the picked village', () => {
    (globalThis as { Path2D?: unknown }).Path2D = FakePath;
    const states = replayStates(chronicleFixture.map);
    const { ctx, squares, rings } = recorder();

    drawReplay(ctx, { side: 100, map: chronicleFixture.map, state: states[2]!, colors, theme: 'dark', highlight: 1 });

    expect(squares.slice(1, 4).map((square) => square.color)).toEqual(['own', 'own', 'own']);
    expect(rings()).toBeGreaterThan(0);
  });

  it('keeps every other side away from the own red', () => {
    for (let id = 1; id <= 60; id++) {
      for (const theme of ['light', 'dark'] as const) {
        const color = sideColor({ kind: 'GUILD', id, name: 'G', tag: 'G', npc: false, mine: false }, colors, theme);
        const hue = Number(/hsl\((\d+)/.exec(color)?.[1]);
        expect(Math.abs(((hue - 6 + 540) % 360) - 180)).toBeGreaterThanOrEqual(32);
      }
    }
  });

  it('zooms to the villages of the story with a margin, inside the map', () => {
    const villages = [
      { x: 180, y: 190, keep: false },
      { x: 210, y: 230, keep: false },
    ];
    expect(viewOf({ ...chronicleFixture.map, size: 400, villages })).toEqual({ x: 171, y: 186, span: 49 });
    expect(viewOf({ ...chronicleFixture.map, size: 400, villages: [{ x: 1, y: 398, keep: false }] })).toEqual({
      x: 0,
      y: 372,
      span: 28,
    });
  });
});
