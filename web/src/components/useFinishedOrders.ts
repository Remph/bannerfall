import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { finishedOrders, withFinished, type FinishedOrder, type ShownOrder } from '../game/buildQueue';
import { prefersReducedMotion } from '../game/motion';
import { serverNow } from '../game/serverTime';

/** How long a finished order stays (green, then folding away; BuildOrderLine.module.css). */
export const FINISH_MS = 1400;

/**
 * A queue whose finished orders stay for a moment as "done" lines that fold away, and whose next
 * order slides into place, instead of the list jumping (UI review 1.3, motion). Cancelled orders
 * leave at once, as before; with reduced motion so do finished ones. `scope` (the village, the
 * building) starts afresh when it changes.
 */
export function useFinishedOrders<T extends { id: number; endAt: string }>(
  scope: string | number,
  queue: readonly T[],
): ShownOrder<T>[] {
  const previous = useRef({ scope, queue });
  const [finished, setFinished] = useState<FinishedOrder<T>[]>([]);
  const [entering, setEntering] = useState<number | null>(null);
  const timers = useRef(new Set<number>());

  // before the browser paints: otherwise the finished order is gone for a frame (or the queue
  // shows "empty") before its folding line comes back
  useLayoutEffect(() => {
    const before = previous.current;
    previous.current = { scope, queue };
    if (before.scope !== scope) {
      setFinished([]);
      setEntering(null);
      return;
    }
    if (before.queue === queue || prefersReducedMotion()) {
      return;
    }
    const done = finishedOrders(before.queue, queue, serverNow());
    if (done.length === 0) {
      return;
    }
    // the first order finished: the one now at the top starts
    const next = done.some(({ index }) => index === 0) ? (queue[0]?.id ?? null) : null;
    setFinished((current) => [...current, ...done]);
    if (next !== null) {
      setEntering(next);
    }
    const timer = window.setTimeout(() => {
      timers.current.delete(timer);
      setFinished((current) => current.filter((item) => !done.includes(item)));
      setEntering((current) => (current === next ? null : current));
    }, FINISH_MS);
    timers.current.add(timer);
  }, [scope, queue]);

  useEffect(() => {
    const pending = timers.current;
    return () => {
      for (const timer of pending) {
        window.clearTimeout(timer);
      }
      pending.clear();
    };
  }, []);

  return withFinished(queue, finished, entering);
}
