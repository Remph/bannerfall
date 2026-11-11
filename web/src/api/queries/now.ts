import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { refetchDelayMs } from '../../game/buildQueue';
import { nextNowEndMs } from '../../game/now';
import { serverNow } from '../../game/serverTime';
import { api, expectData } from '../client';
import type { components } from '../schema';

/** GET /api/now: everything that runs in the player's villages (2.0, the Now column). */
export type Now = components['schemas']['NowDto'];
export type NowVillage = components['schemas']['NowVillageDto'];

export const nowQueryKey = ['now'] as const;

/** The running things of all villages in one answer; the Now column, drawer and sheet show it. */
export function useNow() {
  return useQuery({
    queryKey: nowQueryKey,
    queryFn: () => expectData(api.GET('/api/now')),
  });
}

/** Pages whose data follows the list's timers (from 1600 px they no longer show their own lists). */
const REFRESHED_WITH_NOW = ['village', 'troops', 'recruitment', 'market'] as const;

/**
 * Reloads the list shortly after its next timer ends (a build, a unit, an arrival), so the line
 * folds away and what follows shows up by itself. One timer for the next end; every answer sets it
 * anew, like `useRefreshWhenBuildsEnd`. Used where the list is shown.
 */
export function useRefreshWhenNowEnds(now: Now | undefined, dataUpdatedAt: number, errorUpdatedAt: number) {
  const queryClient = useQueryClient();
  const nextEnd = now === undefined ? undefined : nextNowEndMs(now);
  useEffect(() => {
    if (nextEnd === undefined) {
      return;
    }
    const timer = setTimeout(
      () => {
        // what ended changes the pages too (a level, units at home, troops back, merchants home);
        // only the ones on screen are fetched again, and the list follows them (followWhatFeedsNow)
        for (const prefix of REFRESHED_WITH_NOW) {
          void queryClient.invalidateQueries({ queryKey: [prefix] });
        }
      },
      refetchDelayMs(nextEnd, serverNow()),
    );
    return () => clearTimeout(timer);
  }, [queryClient, nextEnd, dataUpdatedAt, errorUpdatedAt]);
}

/** The queries whose change changes the list too (their keys start with these). */
const FEEDS_NOW = new Set(['village', 'troops', 'recruitment', 'market', 'smithy']);

/**
 * Every order, command and arrival that touches the list also invalidates the village, its troops,
 * its recruitment, its market or its smithy, so the list follows them: one place instead of a line
 * in every mutation. Called once, where the query client is made.
 */
export function followWhatFeedsNow(queryClient: ReturnType<typeof useQueryClient>): void {
  // a mutation invalidates several of them at once: the list is asked for once, not once per query
  let pending = false;
  queryClient.getQueryCache().subscribe((event) => {
    if (
      !pending &&
      event.type === 'updated' &&
      event.action.type === 'invalidate' &&
      FEEDS_NOW.has(String(event.query.queryKey[0]))
    ) {
      pending = true;
      queueMicrotask(() => {
        pending = false;
        void queryClient.invalidateQueries({ queryKey: nowQueryKey });
      });
    }
  });
}
