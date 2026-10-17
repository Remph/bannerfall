import { describe, expect, it } from 'vitest';
import type { MapMovementLine } from '../api/queries/map';
import {
  badgeWins,
  chevronOffsets,
  foldedBadges,
  lineAnchor,
  lineDistance,
  lineFieldsLeft,
  lineHostile,
  lineKind,
  lineMarkerNear,
  lineNear,
  linePosition,
  lineProgress,
  lineTouches,
  mergeTouchingBadges,
  nextLineArrivalMs,
} from './mapLines';

const DEPART = Date.parse('2026-10-16T10:00:00Z');
const ARRIVE = Date.parse('2026-10-16T11:00:00Z');

function line(overrides: Partial<MapMovementLine> = {}): MapMovementLine {
  return {
    id: 1,
    kind: 'ATTACK',
    own: true,
    nobles: false,
    from: { id: 10, name: 'Home', x: 0, y: 0, playerId: 1, playerName: 'me' },
    to: { id: 20, name: null, x: 6, y: 8, playerId: null, playerName: null },
    departAt: new Date(DEPART).toISOString(),
    arriveAt: new Date(ARRIVE).toISOString(),
    ...overrides,
  };
}

describe('lineKind', () => {
  it('uses the colours of the markers', () => {
    expect(lineKind(line())).toBe('attack');
    expect(lineKind(line({ nobles: true }))).toBe('noble');
    expect(lineKind(line({ kind: 'SUPPORT' }))).toBe('support');
    expect(lineKind(line({ kind: 'RETURN' }))).toBe('return');
  });
});

describe('lineHostile', () => {
  it("is another player's attack only", () => {
    expect(lineHostile(line({ own: false }))).toBe(true);
    expect(lineHostile(line())).toBe(false);
    expect(lineHostile(line({ own: false, kind: 'SUPPORT' }))).toBe(false);
  });
});

describe('lineProgress', () => {
  it('runs from 0 at the start to 1 at the arrival', () => {
    expect(lineProgress(line(), DEPART)).toBe(0);
    expect(lineProgress(line(), DEPART + 15 * 60_000)).toBe(0.25);
    expect(lineProgress(line(), ARRIVE)).toBe(1);
  });

  it('stays within 0 and 1 when the clock is a little off', () => {
    expect(lineProgress(line(), DEPART - 5_000)).toBe(0);
    expect(lineProgress(line(), ARRIVE + 5_000)).toBe(1);
  });

  it("knows nothing where the start is hidden (another player's attack)", () => {
    const hidden = line({ own: false, departAt: null });
    expect(lineProgress(hidden, DEPART + 60_000)).toBeUndefined();
    expect(linePosition(hidden, DEPART + 60_000)).toBeUndefined();
    expect(lineFieldsLeft(hidden, DEPART + 60_000)).toBeUndefined();
  });
});

describe('position and distance', () => {
  it('moves the troops from the middle of the start field to the middle of the target field', () => {
    expect(lineDistance(line())).toBe(10);
    expect(linePosition(line(), DEPART)).toEqual({ x: 0.5, y: 0.5 });
    expect(linePosition(line(), DEPART + 30 * 60_000)).toEqual({ x: 3.5, y: 4.5 });
    expect(lineFieldsLeft(line(), DEPART + 30 * 60_000)).toBe(5);
  });

  it('points the bubble at the troops, or at the middle of the way where they are hidden', () => {
    expect(lineAnchor(line(), DEPART + 15 * 60_000)).toEqual({ x: 2, y: 2.5 });
    expect(lineAnchor(line({ departAt: null }), DEPART)).toEqual({ x: 3.5, y: 4.5 });
  });

  it('belongs to both of its villages', () => {
    expect(lineTouches(line(), 10)).toBe(true);
    expect(lineTouches(line(), 20)).toBe(true);
    expect(lineTouches(line(), 30)).toBe(false);
  });
});

describe('picking a line', () => {
  const half = DEPART + 30 * 60_000; // troops at 3.5|4.5

  it('finds the nearest troop badge within reach', () => {
    const near = line({ id: 1 });
    const other = line({ id: 2, departAt: new Date(DEPART - 30 * 60_000).toISOString() }); // a little ahead
    expect(lineMarkerNear([near, other], { x: 3.6, y: 4.4 }, half, 0.5)?.id).toBe(1);
    expect(lineMarkerNear([near], { x: 5, y: 4.5 }, half, 0.5)).toBeUndefined();
  });

  it('never finds the badge of an attack whose position is hidden', () => {
    expect(lineMarkerNear([line({ departAt: null })], { x: 3.5, y: 4.5 }, half, 0.5)).toBeUndefined();
  });

  it('finds a line passing within reach, also a hidden one, but not beyond its ends', () => {
    const hidden = line({ departAt: null });
    expect(lineNear([hidden], { x: 3.6, y: 4.4 }, 0.3)?.id).toBe(1);
    expect(lineNear([hidden], { x: 4.5, y: 3.5 }, 0.3)).toBeUndefined();
    expect(lineNear([hidden], { x: 7.5, y: 10.5 }, 0.3)).toBeUndefined();
  });
});

describe('badgeWins', () => {
  const village = { x: 4, y: 4 }; // middle 4.5|4.5
  const onBadge = 0.25;

  it('lets a tap on the village pick the village while troops stand next to it', () => {
    expect(badgeWins({ x: 4.5, y: 4.5 }, { x: 4.9, y: 4.2 }, village, onBadge)).toBe(false);
    // troops a moment after leaving: nearer to the tap than the middle, but the tap misses the badge
    expect(badgeWins({ x: 4.95, y: 4.5 }, { x: 4.6, y: 4.5 }, village, onBadge)).toBe(false);
  });

  it('lets a tap on the badge pick the troops, also when they are right on the village', () => {
    expect(badgeWins({ x: 4.85, y: 4.25 }, { x: 4.9, y: 4.2 }, village, onBadge)).toBe(true);
    expect(badgeWins({ x: 4.5, y: 4.5 }, { x: 4.5, y: 4.5 }, village, onBadge)).toBe(true);
  });

  it('keeps the whole reach for the troops away from villages', () => {
    expect(badgeWins({ x: 1, y: 1 }, { x: 1.4, y: 1 }, undefined, onBadge)).toBe(true);
  });
});

describe('nextLineArrivalMs', () => {
  it('is the earliest arrival, or undefined without lines', () => {
    expect(nextLineArrivalMs([])).toBeUndefined();
    expect(nextLineArrivalMs([line(), line({ id: 2, arriveAt: new Date(ARRIVE - 60_000).toISOString() })])).toBe(
      ARRIVE - 60_000,
    );
  });
});

describe('foldedBadges', () => {
  // 3 of 60 minutes: half a field of the 10 behind the troops
  const justSent = DEPART + 3 * 60_000;
  const none = { lineId: null, villageId: null };

  it('folds own troops that just left the same village into one badge', () => {
    const folded = foldedBadges(
      [line({ id: 1 }), line({ id: 2 }), line({ id: 3, from: { ...line().from, id: 11 } })],
      justSent,
      none,
    );

    expect(folded).toHaveLength(1);
    expect(folded[0]?.start.id).toBe(10);
    expect(folded[0]?.kind).toBe('attack');
    expect(folded[0]?.lines.map((entry) => entry.id)).toEqual([1, 2]);
  });

  it('leaves a single one, troops a field out, foreign and hidden ones alone', () => {
    expect(foldedBadges([line()], justSent, none)).toEqual([]);
    // 6 of 60 minutes: a whole field
    expect(foldedBadges([line({ id: 1 }), line({ id: 2 })], DEPART + 6 * 60_000, none)).toEqual([]);
    expect(foldedBadges([line({ id: 1, own: false }), line({ id: 2, own: false })], justSent, none)).toEqual([]);
    expect(foldedBadges([line({ id: 1, departAt: null }), line({ id: 2, departAt: null })], justSent, none)).toEqual(
      [],
    );
  });

  it('shows nobles before attacks before supports', () => {
    expect(foldedBadges([line({ id: 1, kind: 'SUPPORT' }), line({ id: 2 })], justSent, none)[0]?.kind).toBe('attack');
    expect(foldedBadges([line({ id: 1 }), line({ id: 2, nobles: true })], justSent, none)[0]?.kind).toBe('noble');
  });

  it('keeps the picked line and the lines of the selected village apart', () => {
    const round = [line({ id: 1 }), line({ id: 2 }), line({ id: 3 })];

    expect(foldedBadges(round, justSent, { lineId: 2, villageId: null })[0]?.lines.map((entry) => entry.id)).toEqual([
      1, 3,
    ]);
    expect(foldedBadges(round, justSent, { lineId: null, villageId: 10 })).toEqual([]);
  });
});

describe('mergeTouchingBadges', () => {
  const reach = 10;
  const first = line({ id: 1, arriveAt: new Date(ARRIVE - 60_000).toISOString() });
  const second = line({ id: 2 });

  it('merges touching badges of one way into the one arriving first', () => {
    const shown = mergeTouchingBadges(
      [
        { line: second, at: { x: 104, y: 100 } },
        { line: first, at: { x: 100, y: 100 } },
      ],
      reach,
      null,
    );

    expect(shown).toEqual([{ line: first, at: { x: 100, y: 100 }, count: 2 }]);
  });

  it('keeps badges apart that are further away, on another way or of another kind', () => {
    const at = { x: 100, y: 100 };
    const keepsApart = (other: MapMovementLine, otherAt = at) =>
      mergeTouchingBadges(
        [
          { line: first, at },
          { line: other, at: otherAt },
        ],
        reach,
        null,
      ).map((badge) => badge.count);

    expect(keepsApart(second, { x: 111, y: 100 })).toEqual([1, 1]);
    expect(keepsApart(line({ id: 2, to: { ...line().to, id: 21 } }))).toEqual([1, 1]);
    expect(keepsApart(line({ id: 2, nobles: true }))).toEqual([1, 1]);
  });

  it('never merges the picked line', () => {
    const at = { x: 100, y: 100 };
    const counts = (picked: number) =>
      mergeTouchingBadges(
        [
          { line: first, at },
          { line: second, at },
        ],
        reach,
        picked,
      ).map((badge) => badge.count);

    expect(counts(1)).toEqual([1, 1]);
    expect(counts(2)).toEqual([1, 1]);
  });
});

describe('chevronOffsets', () => {
  it('spreads the arrows evenly, clear of both ends', () => {
    expect(chevronOffsets(200, 20, 70, 28)).toEqual([20, 100, 180]);
  });

  it('puts one in the middle of a short way and none on a very short one', () => {
    expect(chevronOffsets(60, 20, 70, 28)).toEqual([30]);
    expect(chevronOffsets(20, 20, 70, 28)).toEqual([]);
  });
});
