/** Paladin slots on the client (M31, 02-game-rules §5.5): the server decides, this only shows the limit. */

/** Slots not taken; never negative (a lost village can leave more paladins than slots). */
export function freePaladinSlots(paladin: { slots: number; used: number }): number {
  return Math.max(0, paladin.slots - paladin.used);
}

/** A count cap lowered to the free slots; `undefined` means "not a paladin / still loading": no cap. */
export function cappedBySlots(max: number, freeSlots: number | undefined): number {
  return freeSlots === undefined ? max : Math.min(max, freeSlots);
}
