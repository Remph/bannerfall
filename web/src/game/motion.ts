/**
 * Motion (UI review 1.3, 05-frontend §8): only where something changed in the game – an order
 * finished, resources arrived, a page changed, a report came in, a banner was made – and none when
 * the device asks for reduced motion. CSS animations follow that setting by themselves (global.css);
 * motion driven from script asks here first.
 */
export function prefersReducedMotion(): boolean {
  return (
    typeof window !== 'undefined' &&
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  );
}

/** Fast start, gentle stop (0 → 0, 1 → 1). */
export function easeOut(progress: number): number {
  const k = clamp01(progress);
  return 1 - (1 - k) ** 3;
}

/** Gentle start and stop (0 → 0, 1 → 1). */
export function easeInOut(progress: number): number {
  const k = clamp01(progress);
  return k < 0.5 ? 4 * k ** 3 : 1 - (-2 * k + 2) ** 3 / 2;
}

function clamp01(value: number): number {
  return Math.min(1, Math.max(0, value));
}

/** How long a resource value counts to its new amount. */
export const COUNT_MS = 700;

/**
 * Whether a resource value jumped instead of growing with the production: more than a few seconds'
 * worth of it (and at least 20), e.g. loot, a trade, an exchange or a payment. Only a jump counts.
 */
export function isResourceJump(from: number, to: number, perHour: number): boolean {
  return Math.abs(to - from) >= Math.max(20, perHour / 1200);
}

/** The value shown `elapsedMs` into counting from `from` to `to`, rounded to whole resources. */
export function countedValue(from: number, to: number, elapsedMs: number): number {
  return Math.round(from + (to - from) * easeOut(elapsedMs / COUNT_MS));
}
