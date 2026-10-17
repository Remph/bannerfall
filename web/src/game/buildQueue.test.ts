import { describe, expect, it } from 'vitest';
import {
  finishedOrders,
  hasStarted,
  isPaidSlot,
  moveOptions,
  progress,
  REFETCH_AFTER_END_MS,
  refetchDelayMs,
  withFinished,
  type QueuedOrder,
} from './buildQueue';
import type { BuildingType } from './gameData';

const now = Date.parse('2026-10-03T12:00:00Z');
const minute = 60_000;

/** An order from `fromMin` to `toMin` minutes after `now` (negative = in the past). */
function order(building: BuildingType, fromMin: number, toMin: number): QueuedOrder {
  return {
    building,
    startAt: new Date(now + fromMin * minute).toISOString(),
    endAt: new Date(now + toMin * minute).toISOString(),
  };
}

describe('hasStarted', () => {
  it('is true from the start on', () => {
    expect(hasStarted(order('WOODCUTTER', 0, 10), now)).toBe(true);
    expect(hasStarted(order('WOODCUTTER', -5, 10), now)).toBe(true);
    expect(hasStarted(order('WOODCUTTER', 1, 10), now)).toBe(false);
  });
});

describe('progress', () => {
  it('goes from 0 to 1 between start and end', () => {
    expect(progress(order('FARM', -5, 15), now)).toBe(0.25);
    expect(progress(order('FARM', 5, 15), now)).toBe(0);
    expect(progress(order('FARM', -15, -5), now)).toBe(1);
  });
});

describe('moveOptions', () => {
  const queue = [order('WOODCUTTER', -5, 10), order('CLAY_PIT', 10, 25), order('IRON_MINE', 25, 40)];

  it('never moves the running order, and nothing passes it', () => {
    expect(moveOptions(queue, 0, now)).toEqual({ up: false, down: false });
    expect(moveOptions(queue, 1, now)).toEqual({ up: false, down: true });
    expect(moveOptions(queue, 2, now)).toEqual({ up: true, down: false });
  });

  it('keeps upgrades of one building in their order', () => {
    const twice = [order('CLAY_PIT', -5, 10), order('WOODCUTTER', 10, 25), order('WOODCUTTER', 25, 40)];
    expect(moveOptions(twice, 1, now)).toEqual({ up: false, down: false });
    expect(moveOptions(twice, 2, now)).toEqual({ up: false, down: false });
  });

  it('treats every started order as fixed (the server is behind)', () => {
    const behind = [order('WOODCUTTER', -20, -5), order('CLAY_PIT', -5, 10), order('IRON_MINE', 10, 25)];
    expect(moveOptions(behind, 1, now)).toEqual({ up: false, down: false });
    expect(moveOptions(behind, 2, now)).toEqual({ up: false, down: false });
  });

  it('has no options for an index outside the queue', () => {
    expect(moveOptions(queue, 3, now)).toEqual({ up: false, down: false });
  });

  it('keeps the paid slots behind the queue size fixed (M13c)', () => {
    const long = [
      order('WOODCUTTER', -5, 10),
      order('CLAY_PIT', 10, 25),
      order('IRON_MINE', 25, 40),
      order('FARM', 40, 55),
    ];
    // queue size 3: the farm sits in the first paid slot
    expect(moveOptions(long, 2, now, 3)).toEqual({ up: true, down: false });
    expect(moveOptions(long, 3, now, 3)).toEqual({ up: false, down: false });
    // once it is within the queue size it moves like any other
    expect(moveOptions(long, 3, now, 4)).toEqual({ up: true, down: false });
  });
});

describe('isPaidSlot', () => {
  it('is every index from the queue size on', () => {
    expect(isPaidSlot(4, 5)).toBe(false);
    expect(isPaidSlot(5, 5)).toBe(true);
    expect(isPaidSlot(9, 5)).toBe(true);
  });
});

describe('refetchDelayMs', () => {
  it('waits until a moment after the end', () => {
    expect(refetchDelayMs(now + 10 * minute, now)).toBe(10 * minute + REFETCH_AFTER_END_MS);
  });

  it('asks again every second while the server has not applied an order that is over', () => {
    expect(refetchDelayMs(now - 2_000, now)).toBe(1_000);
  });

  it('slows down when the server stays behind', () => {
    expect(refetchDelayMs(now - 60_000, now)).toBe(15_000);
  });

  it('never waits longer than a day', () => {
    expect(refetchDelayMs(now + 30 * 24 * 60 * minute, now)).toBe(86_400_000);
  });
});

describe('finished orders (motion)', () => {
  const at = (id: number, endMin: number) => ({ id, endAt: new Date(now + endMin * minute).toISOString() });

  it('finds the orders that left the queue because they were done', () => {
    const before = [at(1, -0.1), at(2, 5), at(3, 9)];
    expect(finishedOrders(before, [at(2, 5), at(3, 9)], now)).toEqual([{ order: before[0], index: 0 }]);
  });

  it('leaves out cancelled orders, which end later', () => {
    const before = [at(1, 2), at(2, 5), at(3, 9)];
    expect(finishedOrders(before, [at(1, 2), at(3, 9)], now)).toEqual([]);
  });

  it('finds nothing when the queue is the same', () => {
    const queue = [at(1, -1), at(2, 5)];
    expect(finishedOrders(queue, [...queue], now)).toEqual([]);
  });

  it('puts the finished orders back at their places', () => {
    const one = at(1, -1);
    const two = at(2, -0.5);
    const queue = [at(3, 5), at(4, 9)];
    const shown = withFinished(
      queue,
      [
        { order: two, index: 1 },
        { order: one, index: 0 },
      ],
      3,
    );
    expect(shown.map(({ order, finished, entering }) => [order.id, finished, entering])).toEqual([
      [1, true, false],
      [2, true, false],
      [3, false, true],
      [4, false, false],
    ]);
  });

  it('shows a finished order at the end of a shorter queue', () => {
    const shown = withFinished([], [{ order: at(7, -1), index: 2 }]);
    expect(shown).toEqual([{ order: at(7, -1), finished: true, entering: false }]);
  });
});
