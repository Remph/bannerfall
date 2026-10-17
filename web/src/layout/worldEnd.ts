import { createContext, useContext } from 'react';
import { matchPath } from 'react-router';

/**
 * The end of the world on screen (v1.3, 02-game-rules §22): `ended` once it is over, `frozen` where
 * the game's actions are off then. Outside the game frame (login, tests) both are false.
 */
export interface WorldEnd {
  ended: boolean;
  frozen: boolean;
}

export const WorldEndContext = createContext<WorldEnd>({ ended: false, frozen: false });

/** Whether the world has ended: timers and production stand still, attack warnings go. */
export function useWorldEnded(): boolean {
  return useContext(WorldEndContext).ended;
}

/** Whether the game's buttons are off here: the world has ended and this part doesn't stay open. */
export function useActionsFrozen(): boolean {
  return useContext(WorldEndContext).frozen;
}

/**
 * The pages that still work in an ended world, like the server's list of requests it still takes
 * (`PregameGateFilter.isOpenWhenEnded`): reports, messages, the password, the admin page and the
 * simulator, which changes nothing. The chat stays open too (its panel, outside the pages).
 * The patterns are the router's (router.tsx).
 */
export const openWhenEnded: readonly string[] = [
  '/reports',
  '/reports/:reportId',
  '/messages',
  '/messages/:playerId',
  '/password',
  '/admin',
  '/v/:villageId/rally/simulator',
];

/** Whether `pathname` is one of the pages that stay open in an ended world. */
export function isOpenWhenEnded(pathname: string): boolean {
  return openWhenEnded.some((pattern) => matchPath(pattern, pathname) !== null);
}
