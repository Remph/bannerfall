import { useCurrentAccount } from '../../api/queries/auth';
import { useMyGuild } from '../../api/queries/guilds';
import type { Victory, VictorySide, WorldResult } from '../../api/queries/world';
import { formatShare } from '../../game/format';
import { isMine, sideName, villageShare } from '../../game/victory';
import type { Dictionary } from '../../i18n/en';
import type { Language } from '../../i18n/language';

/** Whether a side is the player's own: himself, or his guild (from `GET /api/guilds/mine`). */
export function useMySide(): (side: VictorySide | null | undefined) => boolean {
  const me = useCurrentAccount();
  const guildId = useMyGuild().data?.guild?.id;
  return (side) => side != null && isMine(side, me.playerId, guildId);
}

/**
 * Who holds what (1.3): "[IW] Iron Wolves holds 3 of 5 keeps", "Your guild holds …", "You hold 61 %
 * of the villages". `mine` says whether the side is the player's own.
 */
export function stateText(
  t: Dictionary,
  victory: Victory,
  side: VictorySide,
  mine: boolean,
  language: Language,
): string {
  const labels = t.victory;
  const you = mine && side.kind === 'PLAYER';
  const who = mine ? labels.yourGuild : sideName(side);
  if (victory.mode === 'DOMINATION') {
    const share = formatShare(villageShare(side, victory.allVillages), language);
    return you ? labels.shareStateYou(share) : labels.shareState(who, share);
  }
  return you
    ? labels.keepsStateYou(side.keeps, victory.keepCount)
    : labels.keepsState(who, side.keeps, victory.keepCount);
}

/** Who won an ended world (1.3): "Your guild won!", "You won!", "[IW] Iron Wolves won.", "Nobody won." */
export function endHeadline(t: Dictionary, winner: VictorySide | null, mine: boolean): string {
  if (winner === null) {
    return t.victory.noWinner;
  }
  if (!mine) {
    return t.victory.sideWon(sideName(winner));
  }
  return winner.kind === 'GUILD' ? t.victory.guildWon : t.victory.youWon;
}

/**
 * The player's place in the final standings when his side didn't win: "Your guild: place 2 of 7";
 * null when it won or isn't in the standings (no village at the end).
 */
export function placeText(t: Dictionary, result: WorldResult, mine: (side: VictorySide) => boolean): string | null {
  if (result.winner !== null && mine(result.winner)) {
    return null;
  }
  const index = result.sides.findIndex(mine);
  const own = result.sides[index];
  if (own === undefined) {
    return null;
  }
  const total = result.sides.length;
  return own.kind === 'GUILD' ? t.victory.yourPlaceGuild(index + 1, total) : t.victory.yourPlace(index + 1, total);
}
