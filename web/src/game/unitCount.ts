/**
 * The number typed into a unit count field (recruitment, later the rally point): only digits count,
 * an empty field is 0, and nothing above `limit`. Phones show a numeric keyboard, but pasted text or
 * a desktop keyboard can still bring in other characters.
 */
export function parseCount(text: string, limit: number): number {
  const digits = text.replace(/\D/g, '');
  if (digits === '') {
    return 0;
  }
  return Math.min(limit, Number(digits));
}

/** Where the controls of a count field stop (UnitCountInput). */
export interface CountBounds {
  /** The "max" chip's value: `max`, never above `limit`. */
  chip: number;
  /** The highest count + reaches. */
  plus: number;
}

/**
 * `max` is what makes sense right now (what the village can pay for, the units at home), `limit` the
 * hard stop. + normally stops at `max` too; `stepMax` lets it count further while the chip keeps
 * showing `max` – a farm template may hold more units than are home (more will be trained, D12).
 */
export function countBounds(max: number, limit: number, stepMax: number = max): CountBounds {
  return { chip: Math.min(max, limit), plus: Math.min(stepMax, limit) };
}
