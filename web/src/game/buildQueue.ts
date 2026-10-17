import type { BuildingType } from './gameData';

/**
 * Helpers for the build queue (02-game-rules §4.3). Times come as ISO strings from the API and are
 * compared with the server clock (serverNow / useServerNow).
 */

/** What the helpers need of an order. The generated BuildOrderDto has these fields, so it fits. */
export interface QueuedOrder {
  building: BuildingType;
  startAt: string;
  endAt: string;
}

/** An order has started once its start is reached (the running one; more if the server is behind). */
export function hasStarted(order: QueuedOrder, nowMs: number): boolean {
  return Date.parse(order.startAt) <= nowMs;
}

/** How far the order is, from 0 to 1 (for the progress bar of the running order). */
export function progress(order: QueuedOrder, nowMs: number): number {
  const start = Date.parse(order.startAt);
  const end = Date.parse(order.endAt);
  if (end <= start) {
    return 1;
  }
  return Math.min(1, Math.max(0, (nowMs - start) / (end - start)));
}

/**
 * Whether the order at `index` (0-based) sits in a paid slot: behind the world's `buildQueueSize`
 * (02-game-rules §4.3, M13c). Orders there cost a surcharge and can't be moved.
 */
export function isPaidSlot(index: number, queueSize: number): boolean {
  return index >= queueSize;
}

/**
 * Whether the up/down buttons can move the order at `index` (02-game-rules §4.3, *Move*). Only
 * orders that haven't started move, never in front of a started one, and never past an upgrade of
 * the same building (its levels keep their order). Orders in the paid slots (from `queueSize` on,
 * M13c) don't move and nothing moves there. Requirements between different buildings are left to
 * the server, which answers REQUIREMENTS_NOT_MET.
 */
export function moveOptions(
  queue: readonly QueuedOrder[],
  index: number,
  nowMs: number,
  queueSize: number = Number.POSITIVE_INFINITY,
): { up: boolean; down: boolean } {
  const order = queue[index];
  if (order === undefined || hasStarted(order, nowMs) || isPaidSlot(index, queueSize)) {
    return { up: false, down: false };
  }
  const above = queue[index - 1];
  const below = isPaidSlot(index + 1, queueSize) ? undefined : queue[index + 1];
  return {
    up: above !== undefined && !hasStarted(above, nowMs) && above.building !== order.building,
    down: below !== undefined && below.building !== order.building,
  };
}

/** The server's event worker runs every 200 ms; ask for the result a little later than that. */
export const REFETCH_AFTER_END_MS = 1000;
const OVERDUE_RETRY_MS = 1000;
const OVERDUE_FAST_FOR_MS = 10_000;
const SLOW_RETRY_MS = 15_000;
// setTimeout can't wait longer than ~24.8 days; a day is plenty, the timer is simply set again.
const MAX_DELAY_MS = 86_400_000;

/**
 * How long to wait before reloading the village, given when its running order ends. If the last
 * answer still had an order that is already over, the worker hasn't applied it yet: ask again every
 * second for a little while, then only now and then (server busy or down).
 */
export function refetchDelayMs(endAtMs: number, nowMs: number): number {
  if (endAtMs > nowMs) {
    return Math.min(MAX_DELAY_MS, endAtMs - nowMs + REFETCH_AFTER_END_MS);
  }
  return nowMs - endAtMs < OVERDUE_FAST_FOR_MS ? OVERDUE_RETRY_MS : SLOW_RETRY_MS;
}

/** An order that left its queue because it was done, with its place there (UI review 1.3, motion). */
export interface FinishedOrder<T> {
  order: T;
  index: number;
}

/**
 * The orders of `before` that are gone from `after` because they were done by `nowMs` – not
 * cancelled ones, which end later. Works for build, recruit and dismiss queues alike.
 */
export function finishedOrders<T extends { id: number; endAt: string }>(
  before: readonly T[],
  after: readonly T[],
  nowMs: number,
): FinishedOrder<T>[] {
  const still = new Set(after.map((order) => order.id));
  return before.flatMap((order, index) =>
    !still.has(order.id) && Date.parse(order.endAt) <= nowMs ? [{ order, index }] : [],
  );
}

/** A line of a queue as shown: an order, or one that just finished and is folding away. */
export interface ShownOrder<T> {
  order: T;
  /** Done and about to fold away (the order is no longer in the queue). */
  finished: boolean;
  /** Just moved up to run, after the one before it finished. */
  entering: boolean;
}

/** The queue with the just finished orders back at their places. */
export function withFinished<T extends { id: number }>(
  queue: readonly T[],
  finished: readonly FinishedOrder<T>[],
  enteringId: number | null = null,
): ShownOrder<T>[] {
  const shown = queue.map((order) => ({ order, finished: false, entering: order.id === enteringId }));
  for (const { order, index } of [...finished].sort((a, b) => a.index - b.index)) {
    shown.splice(Math.min(index, shown.length), 0, { order, finished: true, entering: false });
  }
  return shown;
}
