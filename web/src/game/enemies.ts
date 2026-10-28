/**
 * The enemy marks of the player's guild (1.7, 02-game-rules §16.3) as sets of ids, and what a
 * marked guild or player means for the villages and people below it. The server lists the marks
 * that count; the red markers everywhere are derived from them here.
 */

export interface EnemySets {
  guilds: ReadonlySet<number>;
  players: ReadonlySet<number>;
  villages: ReadonlySet<number>;
}

interface EnemyList {
  guilds: readonly { id: number }[];
  players: readonly { id: number }[];
  villages: readonly { id: number }[];
}

export const NO_ENEMIES: EnemySets = { guilds: new Set(), players: new Set(), villages: new Set() };

export function enemySets(list: EnemyList | undefined): EnemySets {
  if (list === undefined) {
    return NO_ENEMIES;
  }
  return {
    guilds: new Set(list.guilds.map((enemy) => enemy.id)),
    players: new Set(list.players.map((enemy) => enemy.id)),
    villages: new Set(list.villages.map((enemy) => enemy.id)),
  };
}

/** A player is an enemy when he is marked or his guild is. */
export function isEnemyPlayer(enemies: EnemySets, player: { id: number; guildId?: number | null }): boolean {
  return enemies.players.has(player.id) || (player.guildId != null && enemies.guilds.has(player.guildId));
}

/** A village is an enemy when it, its owner or its owner's guild is marked. Barbarian villages are marked by nothing but themselves. */
export function isEnemyVillage(
  enemies: EnemySets,
  village: { id: number; playerId?: number | null; guildId?: number | null },
): boolean {
  if (enemies.villages.has(village.id)) {
    return true;
  }
  return village.playerId != null && isEnemyPlayer(enemies, { id: village.playerId, guildId: village.guildId });
}

/**
 * What the viewer can do about a target:
 * - `hidden`: nothing, because guilds are off, the viewer does not lead a guild, or the target is
 *   the viewer's own guild or a guild mate (the server would refuse), or a barbarian village;
 * - `mark` / `unmark`: the leader's action.
 * `marked` is whether this very target (not its owner or guild) is marked.
 */
export type EnemyAction = 'hidden' | 'mark' | 'unmark';

export function enemyAction(input: {
  guildsEnabled: boolean;
  /** The viewer's guild state as `GET /api/guilds/mine` reports it. */
  mine:
    | { role?: 'LEADER' | 'MEMBER' | null; guild?: { id: number; members: readonly { playerId: number }[] } | null }
    | undefined;
  target:
    | { kind: 'GUILD'; id: number }
    | { kind: 'PLAYER'; id: number }
    | { kind: 'VILLAGE'; id: number; ownerId: number | null | undefined };
  marked: boolean;
}): EnemyAction {
  const { guildsEnabled, mine, target, marked } = input;
  const guild = mine?.guild;
  if (!guildsEnabled || guild == null || mine?.role !== 'LEADER') {
    return 'hidden';
  }
  const isMate = (playerId: number) => guild.members.some((member) => member.playerId === playerId);
  if (target.kind === 'GUILD' && target.id === guild.id) {
    return 'hidden';
  }
  if (target.kind === 'PLAYER' && isMate(target.id)) {
    return 'hidden';
  }
  if (target.kind === 'VILLAGE' && (target.ownerId == null || isMate(target.ownerId))) {
    return 'hidden';
  }
  return marked ? 'unmark' : 'mark';
}
