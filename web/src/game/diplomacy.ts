import type { Diplomacy, Lord } from '../api/queries/diplomacy';

/** Why the player can invite no lord right now, the first cause that holds; null when he can. */
export type InviteBlock = 'notSolo' | 'needGuild' | 'notLeader' | 'guildFull';

/** 2.1 S2b (12-solo-and-allies §4): only a solo world, only the leader of a guild with room. */
export function inviteBlock(page: Diplomacy, inGuild: boolean): InviteBlock | null {
  const causes: readonly [boolean, InviteBlock][] = [
    [!page.soloMode, 'notSolo'],
    [!inGuild, 'needGuild'],
    [!page.leader, 'notLeader'],
    [page.guildFull, 'guildFull'],
  ];
  return causes.find(([holds]) => holds)?.[1] ?? null;
}

/** Where one lord stands: an ally, invited, waiting after a no (until when, ms), or free to ask. */
export type LordState = { kind: 'ally' } | { kind: 'invited' } | { kind: 'waiting'; until: number } | { kind: 'open' };

export function lordState(lord: Lord, nowMs: number): LordState {
  if (lord.ally) {
    return { kind: 'ally' };
  }
  if (lord.invitationId !== null) {
    return { kind: 'invited' };
  }
  const until = lord.inviteAfter === null ? null : Date.parse(lord.inviteAfter);
  return until !== null && until > nowMs ? { kind: 'waiting', until } : { kind: 'open' };
}

/** "+12", "−8", "0": with a typographic minus, like the change lines. */
export function signedTrust(value: number): string {
  if (value === 0) {
    return '0';
  }
  return value > 0 ? `+${value}` : `−${Math.abs(value)}`;
}
