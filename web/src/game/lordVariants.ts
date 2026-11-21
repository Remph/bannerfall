/**
 * The nine NPC lord variants (2.1, 09-npc-lords §16.2). The server sends a lord's variant key only
 * to a player who has met the lord (§16.7); the title and the line come from the i18n.
 */
export const lordVariantKeys = [
  'ROBBER_KNIGHT',
  'CONQUEROR',
  'HOTHEAD',
  'CASTELLAN',
  'PROTECTOR',
  'MERCHANT',
  'SETTLER',
  'KNIGHT',
  'VULTURE',
] as const;

export type LordVariantKey = (typeof lordVariantKeys)[number];

/** The key as a known variant, or null for one this client doesn't know (a newer server). */
export function lordVariantKey(key: string): LordVariantKey | null {
  return (lordVariantKeys as readonly string[]).includes(key) ? (key as LordVariantKey) : null;
}

/** The variants of each personality (09-npc-lords §16.2), for the admin's spawn menu. */
export const lordVariantsOf = {
  OFFENSIVE: ['ROBBER_KNIGHT', 'CONQUEROR', 'HOTHEAD'],
  DEFENSIVE: ['CASTELLAN', 'PROTECTOR', 'MERCHANT'],
  BALANCED: ['SETTLER', 'KNIGHT', 'VULTURE'],
} as const satisfies Record<'OFFENSIVE' | 'DEFENSIVE' | 'BALANCED', readonly LordVariantKey[]>;

/** The female ranks among the lord names (`NpcLordService.NAMES`): Gräfin, Lady, Dame, Baronin. */
const femaleRanks = ['Gräfin ', 'Lady ', 'Dame ', 'Baronin '];

/** Whether a lord's name is a woman's (German titles have a female form: die Raubritterin). */
export function isFemaleLordName(name: string): boolean {
  return femaleRanks.some((rank) => name.startsWith(rank));
}
