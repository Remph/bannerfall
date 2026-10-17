import type { NpcGuildAction, NpcLord } from '../../api/queries/admin';

/** A lord guild as the NPC lords card lists it (09-npc-lords §13.7). */
export interface LordGuild {
  id: number;
  name: string;
  tag: string;
  lords: NpcLord[];
  points: number;
}

/** The guilds of the lords with their members and points, strongest first (ties: by name). */
export function lordGuilds(lords: readonly NpcLord[]): LordGuild[] {
  const byId = new Map<number, LordGuild>();
  for (const lord of lords) {
    if (lord.guildId == null || lord.guildName == null || lord.guildTag == null) {
      continue;
    }
    const guild = byId.get(lord.guildId) ?? {
      id: lord.guildId,
      name: lord.guildName,
      tag: lord.guildTag,
      lords: [],
      points: 0,
    };
    guild.lords.push(lord);
    guild.points += lord.points;
    byId.set(lord.guildId, guild);
  }
  return [...byId.values()].sort((a, b) => b.points - a.points || a.name.localeCompare(b.name));
}

/** One thing the admin can do with a lord's guild. */
export interface GuildChoice {
  action: NpcGuildAction;
  /** The guild to move into (JOIN). */
  guild?: LordGuild;
}

/**
 * What the admin can do with this lord (§13.7): move it into another lord guild with room, take
 * it out, found a new guild with it (not when it is alone in its guild already) and, once placed,
 * let it choose again. Nothing for a lord out of the game.
 */
export function guildChoices(lord: NpcLord, guilds: readonly LordGuild[], maxMembers: number): GuildChoice[] {
  if (lord.villageCount === 0) {
    return [];
  }
  const choices: GuildChoice[] = guilds
    .filter((guild) => guild.id !== lord.guildId && guild.lords.length < maxMembers)
    .map((guild) => ({ action: 'JOIN', guild }));
  if (lord.guildId != null) {
    choices.push({ action: 'LEAVE' });
  }
  const alone = guilds.find((guild) => guild.id === lord.guildId)?.lords.length === 1;
  if (!alone) {
    choices.push({ action: 'FOUND' });
  }
  if (lord.guildByAdmin) {
    choices.push({ action: 'AUTO' });
  }
  return choices;
}
