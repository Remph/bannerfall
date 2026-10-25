import { describe, expect, it } from 'vitest';
import type { MapMovement, MapMovementLine } from '../api/queries/map';
import {
  lineOfVillage,
  markerCount,
  movementMarkers,
  movementsOfLines,
  nextMovementArrivalMs,
  returnMarker,
} from './mapMovements';

function movement(attacks: number, nobleAttacks: number, supports: number, nextArriveAt = '2026-10-13T10:00:00Z') {
  return {
    villageId: 1,
    x: 5,
    y: 7,
    attacks,
    nobleAttacks,
    supports,
    returns: 0,
    nextArriveAt,
    nextReturnAt: null,
  } satisfies MapMovement;
}

/** The player's own village with only his troops walking home to it (M36). */
function returning(returns: number, nextReturnAt = '2026-10-13T10:00:00Z'): MapMovement {
  return { ...movement(0, 0, 0), returns, nextArriveAt: null, nextReturnAt };
}

describe('movementMarkers', () => {
  it('shows attacks as red crossed swords and supports as a green sword', () => {
    expect(movementMarkers(movement(2, 0, 0))).toEqual([{ kind: 'attack', count: 2 }]);
    expect(movementMarkers(movement(0, 0, 3))).toEqual([{ kind: 'support', count: 3 }]);
  });

  it('turns the attack badge yellow when one of the own attacks carries nobles, counting all attacks', () => {
    expect(movementMarkers(movement(3, 1, 0))).toEqual([{ kind: 'noble', count: 3 }]);
  });

  it('shows attacks and supports side by side, attacks first', () => {
    expect(movementMarkers(movement(1, 0, 2))).toEqual([
      { kind: 'attack', count: 1 },
      { kind: 'support', count: 2 },
    ]);
  });

  it('has no badge without movements', () => {
    expect(movementMarkers(movement(0, 0, 0))).toEqual([]);
  });

  it('leaves returns to their own corner', () => {
    expect(movementMarkers(returning(2))).toEqual([]);
    expect(movementMarkers({ ...returning(1), attacks: 1, nextArriveAt: '2026-10-13T10:00:00Z' })).toEqual([
      { kind: 'attack', count: 1 },
    ]);
  });
});

describe('returnMarker', () => {
  it('shows the troops walking home with their count', () => {
    expect(returnMarker(returning(1))).toEqual({ kind: 'return', count: 1 });
    expect(returnMarker(returning(4))).toEqual({ kind: 'return', count: 4 });
  });

  it('is there next to attacks and supports on the same village', () => {
    expect(returnMarker({ ...movement(2, 0, 1), returns: 1, nextReturnAt: '2026-10-13T10:00:00Z' })).toEqual({
      kind: 'return',
      count: 1,
    });
  });

  it('is missing without returns', () => {
    expect(returnMarker(movement(1, 0, 1))).toBeUndefined();
  });
});

describe('markerCount', () => {
  it('shows no number for a single movement and caps at 9+', () => {
    expect(markerCount(1)).toBe('');
    expect(markerCount(2)).toBe('2');
    expect(markerCount(9)).toBe('9');
    expect(markerCount(10)).toBe('9+');
  });
});

describe('nextMovementArrivalMs', () => {
  it('is the earliest arrival of all villages', () => {
    const list = [movement(1, 0, 0, '2026-10-13T10:05:00Z'), movement(0, 0, 1, '2026-10-13T10:01:00Z')];
    expect(nextMovementArrivalMs(list)).toBe(Date.parse('2026-10-13T10:01:00Z'));
  });

  it('counts the returns too, skipping the times an entry does not have', () => {
    const list = [movement(1, 0, 0, '2026-10-13T10:05:00Z'), returning(1, '2026-10-13T10:02:00Z')];
    expect(nextMovementArrivalMs(list)).toBe(Date.parse('2026-10-13T10:02:00Z'));
    const both = { ...movement(1, 0, 0, '2026-10-13T10:03:00Z'), returns: 1, nextReturnAt: '2026-10-13T10:04:00Z' };
    expect(nextMovementArrivalMs([both])).toBe(Date.parse('2026-10-13T10:03:00Z'));
  });

  it('is undefined without movements', () => {
    expect(nextMovementArrivalMs([])).toBeUndefined();
  });
});

function ref(id: number) {
  return { id, name: `V${id}`, x: id, y: id * 2, playerId: 7, playerName: 'ben' };
}

function line(
  id: number,
  kind: MapMovementLine['kind'],
  from: number,
  to: number,
  arriveAt: string,
  extra: Partial<MapMovementLine> = {},
): MapMovementLine {
  return { id, kind, own: true, nobles: false, from: ref(from), to: ref(to), departAt: null, arriveAt, ...extra };
}

describe('lineOfVillage', () => {
  it('keeps what leaves from or arrives at the village', () => {
    expect(lineOfVillage(line(1, 'ATTACK', 1, 9, '2026-10-13T10:00:00Z'), 1)).toBe(true);
    expect(lineOfVillage(line(2, 'ATTACK', 9, 1, '2026-10-13T10:00:00Z'), 1)).toBe(true);
    expect(lineOfVillage(line(3, 'ATTACK', 2, 9, '2026-10-13T10:00:00Z'), 1)).toBe(false);
  });
});

describe('movementsOfLines', () => {
  it('counts the lines per target village like the server’s markers', () => {
    const lines = [
      line(1, 'ATTACK', 1, 9, '2026-10-13T12:00:00Z', { nobles: true }),
      line(2, 'ATTACK', 3, 9, '2026-10-13T11:00:00Z'),
      line(3, 'SUPPORT', 1, 9, '2026-10-13T13:00:00Z'),
      line(4, 'RETURN', 9, 1, '2026-10-13T09:00:00Z'),
      line(5, 'RETURN', 9, 1, '2026-10-13T08:00:00Z'),
    ];

    expect(movementsOfLines(lines)).toEqual([
      {
        villageId: 1,
        x: 1,
        y: 2,
        attacks: 0,
        nobleAttacks: 0,
        supports: 0,
        returns: 2,
        nextArriveAt: null,
        nextReturnAt: '2026-10-13T08:00:00Z',
      },
      {
        villageId: 9,
        x: 9,
        y: 18,
        attacks: 2,
        nobleAttacks: 1,
        supports: 1,
        returns: 0,
        nextArriveAt: '2026-10-13T11:00:00Z',
        nextReturnAt: null,
      },
    ]);
  });

  it('has nothing for no lines', () => {
    expect(movementsOfLines([])).toEqual([]);
  });
});
