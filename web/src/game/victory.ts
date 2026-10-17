import type { Victory, VictorySide } from '../api/queries/world';
import { bracketTag } from './guilds';

/**
 * Helpers for the victory card (v1.3, 02-game-rules §22). The server decides who wins; these only
 * say how the standings read.
 */

/** "[KN] Knights of the North" for a guild, the name alone for a player. */
export function sideName(side: VictorySide): string {
  return side.tag === null ? side.name : `${bracketTag(side.tag)} ${side.name}`;
}

/** The side's share of all villages on the map in percent (barbarians count in the total), 0 on an empty map. */
export function villageShare(side: VictorySide, allVillages: number): number {
  return allVillages <= 0 ? 0 : (side.villages * 100) / allVillages;
}

/** The same side in another list: guilds and players are numbered apart. */
export function sameSide(a: VictorySide, b: VictorySide | null | undefined): boolean {
  return b != null && a.kind === b.kind && a.id === b.id;
}

/** Whether the asking player is this side: himself, not his guild. */
export function isPlayer(side: VictorySide, playerId: number): boolean {
  return side.kind === 'PLAYER' && side.id === playerId;
}

/** What the card puts at the end of a side's line: the village share, the keeps or the points. */
export type SideMeasure = 'share' | 'keeps' | 'points';

/** Ended worlds show the measure that decided them; running ones the measure of their mode. */
export function sideMeasure(victory: Victory): SideMeasure {
  const reason = victory.result?.reason;
  if (reason !== undefined) {
    return reason === 'DOMINATION' ? 'share' : reason === 'KEEPS' ? 'keeps' : 'points';
  }
  return victory.mode === 'DOMINATION' ? 'share' : victory.mode === 'KEEPS' ? 'keeps' : 'points';
}

/** How many of `keepCount` keeps a side must hold: `percent` of them rounded up, at least 1 (the server's `Victory.keepsNeeded`). */
export function keepsNeeded(keepCount: number, percent: number): number {
  return Math.max(1, Math.ceil((keepCount * percent) / 100 - 1e-9));
}

/** Whether the side is the asking player's own (1.3): himself, or his guild (`guildId`, undefined without one). */
export function isMine(side: VictorySide, playerId: number, guildId: number | undefined): boolean {
  return isPlayer(side, playerId) || (side.kind === 'GUILD' && side.id === guildId);
}

/** NPC lords show in the standings only among the first ones (1.3): a lord with one village isn't in the race. */
export const NPC_SHOWN_TOP = 3;

/** The standings as the card lists them: without the NPC lords below the top {@link NPC_SHOWN_TOP}. */
export function shownSides(sides: readonly VictorySide[]): VictorySide[] {
  return sides.filter((side, index) => !side.npc || index < NPC_SHOWN_TOP);
}
