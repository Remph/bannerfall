import { matchPath } from 'react-router';

/**
 * The pages a player without any village (all conquered, `needsRespawn`, M11) can still open: "/"
 * (the respawn screen) and everything about the whole world or the account rather than one
 * village (D10). The game pages under /v/:villageId stay closed; every other address bounces to "/".
 * The patterns are the router's (router.tsx).
 */
export const openWithoutVillage: readonly string[] = [
  '/',
  '/reports',
  '/reports/:reportId',
  '/messages',
  '/messages/:playerId',
  '/ranking',
  '/players/:playerId',
  '/achievements',
  '/guild',
  '/guilds/:guildId',
  '/about',
  '/privacy',
  '/password',
  // RequireAdmin still guards it for everybody else
  '/admin',
];

/** Whether `pathname` is one of the pages open without a village. */
export function isOpenWithoutVillage(pathname: string): boolean {
  return openWithoutVillage.some((pattern) => matchPath(pattern, pathname) !== null);
}
