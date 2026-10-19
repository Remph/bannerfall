/**
 * Pure helpers of the statistics charts (1.5, 05-frontend §3): the scale and where the lines go.
 * The drawing is in components/LineChart.tsx.
 */

/** One value of a line on a day ("2026-10-19"). */
export interface ChartPoint {
  day: string;
  value: number;
}

/**
 * The top of the value axis: the smallest "nice" number (1, 2, 2.5 or 5 times a power of ten,
 * times `steps`) at or above `max`, so the grid lines fall on round values. 0 or less gives `steps`
 * (an empty chart still has an axis).
 */
export function niceMax(max: number, steps: number): number {
  if (max <= 0) {
    return steps;
  }
  const raw = max / steps;
  const power = 10 ** Math.floor(Math.log10(raw));
  for (const factor of [1, 2, 2.5, 5, 10]) {
    const step = factor * power;
    if (step >= raw) {
      return Math.max(1, Math.ceil(step)) * steps;
    }
  }
  return 10 * power * steps;
}

/** Every day of the lines, sorted and once each. */
export function chartDays(lines: readonly (readonly ChartPoint[])[]): string[] {
  const days = new Set<string>();
  for (const line of lines) {
    for (const point of line) {
      days.add(point.day);
    }
  }
  return [...days].sort();
}

/** The index of the day closest to `x` (0 … 1 across the plot), for a tap or the keyboard. */
export function nearestIndex(x: number, count: number): number {
  if (count <= 1) {
    return 0;
  }
  return Math.min(count - 1, Math.max(0, Math.round(x * (count - 1))));
}

/** The line's value on `day`, or undefined if it has none (the player wasn't there yet). */
export function valueOn(line: readonly ChartPoint[], day: string): number | undefined {
  return line.find((point) => point.day === day)?.value;
}
