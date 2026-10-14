import { useQuery } from '@tanstack/react-query';
import { api, expectData } from '../client';
import type { components } from '../schema';

export type World = components['schemas']['WorldDto'];
export type PublicWorld = components['schemas']['PublicWorldDto'];

export const worldQueryKey = ['world'] as const;
export const publicWorldQueryKey = ['world', 'status'] as const;

/** While the world has not opened, the page asks again this often (a missed notification can't strand anybody). */
const PREGAME_REFRESH_MS = 30_000;

/**
 * GET /api/world. The settings hardly ever change, so the answer is reused for 10 minutes; in the
 * pre-game (M14g) it is asked for again every 30 seconds, because the opening ends it.
 */
export function useWorld() {
  return useQuery({
    queryKey: worldQueryKey,
    queryFn: () => expectData(api.GET('/api/world')),
    staleTime: 10 * 60_000,
    refetchInterval: (query) => (query.state.data?.phase === 'PREGAME' ? PREGAME_REFRESH_MS : false),
  });
}

export const fetchPublicWorld = () => expectData(api.GET('/api/world/status'));

/** GET /api/world/status: the phase and the opening time for the login page (no login needed, M14g). */
export function usePublicWorld() {
  return useQuery({
    queryKey: publicWorldQueryKey,
    queryFn: fetchPublicWorld,
    refetchInterval: (query) => (query.state.data?.phase === 'PREGAME' ? PREGAME_REFRESH_MS : false),
  });
}
