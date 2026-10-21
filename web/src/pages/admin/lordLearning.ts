import type { NpcLord } from '../../api/queries/admin';

export type LordLearning = NonNullable<NpcLord['learning']>;

/** A share of the mix (0 to 1) in whole percent. */
export function percentOf(share: number): number {
  return Math.round(share * 100);
}

/**
 * The largest mix changes of a learning lord (09-npc-lords §15.5), signed: "Spearman +14 %,
 * Axeman −9 %". The minus is a real minus sign, the space before "%" a no-break space.
 */
export function mixChanges(
  learning: LordLearning,
  unitName: (unit: LordLearning['changes'][number]['unit']) => string,
): string {
  return learning.changes
    .map((change) => `${unitName(change.unit)} ${change.points > 0 ? '+' : '−'}${Math.abs(change.points)}\u00a0%`)
    .join(', ');
}
