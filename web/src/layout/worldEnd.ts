import { createContext, useContext } from 'react';
import { matchPath } from 'react-router';

/**
 * The end of the world on screen (v1.3, 02-game-rules §22): `ended` once it is over, `frozen` where
 * the game's actions are off then. A solo world the host paused (2.1) is frozen the same way until he
 * presses play, without having ended (`paused`). Outside the game frame (login, tests) all are false.
 */
export interface WorldEnd {
  ended: boolean;
  frozen: boolean;
  paused?: boolean;
  /** Whether the player is the host (admin), who presses play in a paused world. */
  host?: boolean;
}

export const WorldEndContext = createContext<WorldEnd>({ ended: false, frozen: false });

/** Whether the host paused this solo world (2.1): it stands still until he presses play. */
export function useWorldPaused(): boolean {
  return useContext(WorldEndContext).paused === true;
}

/**
 * Whether the world stands still (ended, or paused by the host), also on the pages that stay open
 * then (Messages): a game action there, like answering a lord's help request, is off all the same.
 */
export function useWorldStill(): boolean {
  const context = useContext(WorldEndContext);
  return context.ended || context.paused === true;
}

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
