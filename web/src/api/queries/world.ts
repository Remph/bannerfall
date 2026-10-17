import { useQuery } from '@tanstack/react-query';
import { api, expectData } from '../client';
import type { components } from '../schema';

export type World = components['schemas']['WorldDto'];
export type PublicWorld = components['schemas']['PublicWorldDto'];
export type Victory = components['schemas']['VictoryDto'];
export type VictorySide = components['schemas']['VictorySide'];
export type WorldResult = components['schemas']['WorldResultDto'];
export type VictoryMode = Victory['mode'];
export type EndReason = WorldResult['reason'];

export const worldQueryKey = ['world'] as const;
export const publicWorldQueryKey = ['world', 'status'] as const;
/** Under ['world'], so whatever reloads the world reloads this too. */
export const victoryQueryKey = ['world', 'victory'] as const;

/** The standings move with every conquest; the server checks the condition every 5 minutes. */
const VICTORY_REFRESH_MS = 60_000;

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

/**
 * GET /api/world/victory (v1.3, 02-game-rules §22): how the world is won, the leading sides and the
 * clock, or how it ended. Asked for again every minute while the world runs.
 */
export function useVictory(enabled = true) {
  return useQuery({
    queryKey: victoryQueryKey,
    queryFn: () => expectData(api.GET('/api/world/victory')),
    enabled,
    staleTime: 30_000,
    refetchInterval: (query) => (query.state.data?.phase === 'OPEN' ? VICTORY_REFRESH_MS : false),
  });
}
