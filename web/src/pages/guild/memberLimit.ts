import type { Guild } from '../../api/queries/guilds';
import type { World } from '../../api/queries/world';

/**
 * The most members this guild may have: a guild of NPC lords has the lords' own limit
 * (`npcGuildMaxMembers`, 09-npc-lords §13), every other guild `guildMaxMembers`.
 */
export function memberLimit(
  guild: Pick<Guild, 'members'>,
  world: Pick<World, 'guildMaxMembers' | 'npcGuildMaxMembers'>,
): number {
  const lordGuild = guild.members.length > 0 && guild.members.every((member) => member.npc);
  return lordGuild ? world.npcGuildMaxMembers : world.guildMaxMembers;
}
