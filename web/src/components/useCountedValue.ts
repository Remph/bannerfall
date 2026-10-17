import { useEffect, useRef, useState } from 'react';
import { COUNT_MS, countedValue, isResourceJump, prefersReducedMotion } from '../game/motion';

export interface CountedValue {
  /** What to show right now. */
  value: number;
  /** While counting after a jump (and a moment after): which way it went. */
  direction: 'up' | 'down' | null;
}

/** How long the direction (the gold glow of a gain) stays after the count. */
export const AFTERGLOW_MS = 700;

/**
 * A resource value that counts to its new amount when it jumps (loot, a trade, an exchange, a
 * payment) instead of switching at once (UI review 1.3, motion). The normal growth with the
 * production follows directly. While a count runs, a further change becomes its new end.
 */
export function useCountedValue(target: number, perHour: number): CountedValue {
  const [shown, setShown] = useState(target);
  const [direction, setDirection] = useState<'up' | 'down' | null>(null);
  const shownRef = useRef(target);
  const targetRef = useRef(target);
  const frameRef = useRef<number | null>(null);
  const afterglowRef = useRef<number | null>(null);

  useEffect(() => {
    targetRef.current = target;
    if (frameRef.current !== null) {
      return; // the running count picks up the new end
    }
    const from = shownRef.current;
    if (from === target) {
      return;
    }
    if (!isResourceJump(from, target, perHour) || prefersReducedMotion()) {
      shownRef.current = target;
      setShown(target);
      return;
    }
    if (afterglowRef.current !== null) {
      window.clearTimeout(afterglowRef.current);
      afterglowRef.current = null;
    }
    setDirection(target > from ? 'up' : 'down');
    const startedAt = performance.now();
    const step = (now: number) => {
      const elapsed = now - startedAt;
      if (elapsed < COUNT_MS) {
        const value = countedValue(from, targetRef.current, elapsed);
        shownRef.current = value;
        setShown(value);
        frameRef.current = requestAnimationFrame(step);
        return;
      }
      frameRef.current = null;
      shownRef.current = targetRef.current;
      setShown(targetRef.current);
      afterglowRef.current = window.setTimeout(() => {
        afterglowRef.current = null;
        setDirection(null);
      }, AFTERGLOW_MS);
    };
    frameRef.current = requestAnimationFrame(step);
  }, [target, perHour]);

  useEffect(
    () => () => {
      if (frameRef.current !== null) {
        cancelAnimationFrame(frameRef.current);
      }
      if (afterglowRef.current !== null) {
        window.clearTimeout(afterglowRef.current);
      }
    },
    [],
  );

  return { value: shown, direction };
}
