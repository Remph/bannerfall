/**
 * The text rules of a guild, the same as the server's `GuildText` (02-game-rules §16). The server
 * decides; the form uses these only to say what is wrong before a request goes out.
 */

export const NAME_MIN = 3;
export const NAME_MAX = 30;
export const TAG_MIN = 2;
export const TAG_MAX = 5;
export const DESCRIPTION_MAX = 500;

// Letters and digits of any language, single spaces and ' . _ - in between; starts and ends with a
// letter or digit.
const NAME = /^[\p{L}\p{N}](?:[\p{L}\p{N}'._-]| (?! ))*[\p{L}\p{N}]$/u;
const TAG = /^[A-Za-z0-9]+$/;

/** Characters the way the server counts them (code points, not UTF-16 units). */
export function charCount(text: string): number {
  return [...text].length;
}

export type GuildTextProblem = 'tooShort' | 'tooLong' | 'badCharacters';

/** What is wrong with a guild name (after trimming), or undefined if it is fine. */
export function nameProblem(raw: string): GuildTextProblem | undefined {
  const name = raw.trim();
  const length = charCount(name);
  if (length < NAME_MIN) {
    return 'tooShort';
  }
  if (length > NAME_MAX) {
    return 'tooLong';
  }
  return NAME.test(name) ? undefined : 'badCharacters';
}

/** What is wrong with a guild tag (after trimming), or undefined if it is fine. */
export function tagProblem(raw: string): GuildTextProblem | undefined {
  const tag = raw.trim();
  if (tag.length < TAG_MIN) {
    return 'tooShort';
  }
  if (tag.length > TAG_MAX) {
    return 'tooLong';
  }
  return TAG.test(tag) ? undefined : 'badCharacters';
}

/** "[IW] Iron Wolves" – the tag in brackets, as the lists show it before a player's name. */
export function bracketTag(tag: string): string {
  return `[${tag}]`;
}

/** The guild section is shown while the host has guilds on, and to the members of a guild always. */
export function guildSectionVisible(guildsEnabled: boolean, inGuild: boolean): boolean {
  return guildsEnabled || inGuild;
}

/**
 * What a player's profile offers the viewer about inviting that player into the viewer's guild:
 * - `hidden`: nothing, because guilds are off, the viewer leads no guild, or the player is the viewer,
 *   an NPC lord or in a guild already (only the leader may invite, 02-game-rules §16);
 * - `invite`: the button;
 * - `invited`: a note that the invitation is open (taking it back stays on the guild page);
 * - `full`: a note instead of the button, since the server would answer GUILD_FULL anyway.
 */
export type ProfileInvite = 'hidden' | 'invite' | 'invited' | 'full';

export function profileInvite(input: {
  guildsEnabled: boolean;
  /** The viewer's guild as `GET /api/guilds/mine` reports it; undefined while that loads. */
  mine:
    | {
        role?: 'LEADER' | 'MEMBER' | null;
        guild?: { members: readonly unknown[]; invitations?: readonly { playerId: number }[] | null } | null;
      }
    | undefined;
  maxMembers: number;
  viewerPlayerId: number | null | undefined;
  profile: { id: number; npc: boolean; guild?: unknown };
}): ProfileInvite {
  const { guildsEnabled, mine, maxMembers, viewerPlayerId, profile } = input;
  const guild = mine?.guild;
  if (
    !guildsEnabled ||
    guild == null ||
    mine?.role !== 'LEADER' ||
    profile.id === viewerPlayerId ||
    profile.npc ||
    profile.guild != null
  ) {
    return 'hidden';
  }
  if ((guild.invitations ?? []).some((invitation) => invitation.playerId === profile.id)) {
    return 'invited';
  }
  return guild.members.length >= maxMembers ? 'full' : 'invite';
}
