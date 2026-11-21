import type { PushSettings } from '../api/queries/push';

type Kinds = PushSettings['kinds'];

/**
 * The push kinds this world can send (UI review 2.1, finding 11): help requests come only from the
 * lords of a solo world (the server's `NpcHelp`), guild invitations only where guilds are on. Unknown
 * world (still loading): all of them.
 */
export function kindsInWorld(kinds: Kinds, world: { soloMode: boolean; guildsEnabled: boolean } | undefined): Kinds {
  if (world === undefined) {
    return kinds;
  }
  return kinds.filter(
    ({ kind }) => (kind !== 'HELP_REQUEST' || world.soloMode) && (kind !== 'GUILD_INVITE' || world.guildsEnabled),
  );
}
