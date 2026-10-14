/** `/guild`: the player's own guild (or the way to found or join one). */
export const OWN_GUILD_PATH = '/guild';

/** `/guilds/7`: any guild's page. */
export function guildPath(guildId: number): string {
  return `/guilds/${guildId}`;
}

/** `/guilds/7` → 7; anything else → undefined. */
export function parseGuildId(param: string | undefined): number | undefined {
  return param !== undefined && /^\d{1,15}$/.test(param) && Number(param) > 0 ? Number(param) : undefined;
}
