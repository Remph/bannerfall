import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api, expectData } from '../client';
import type { components } from '../schema';

export type RankingPage = components['schemas']['RankingPageDto'];
export type RankingEntry = components['schemas']['RankingEntryDto'];
export type PlayerProfile = components['schemas']['PlayerProfileDto'];

/** Lines per page of the ranking. */
export const RANKING_PAGE_SIZE = 50;

export const rankingQueryKey = ['ranking'] as const;
export const playerQueryKey = ['player'] as const;

/** GET /api/ranking: one page (0-based); the old page stays on screen while the next loads. */
export function useRanking(page: number) {
  return useQuery({
    queryKey: [...rankingQueryKey, page],
    queryFn: () => expectData(api.GET('/api/ranking', { params: { query: { page, size: RANKING_PAGE_SIZE } } })),
    placeholderData: keepPreviousData,
  });
}

/** GET /api/players/{id}: 404 PLAYER_NOT_FOUND for a player that doesn't exist or was deleted. */
export function usePlayerProfile(playerId: number) {
  return useQuery({
    queryKey: [...playerQueryKey, playerId],
    queryFn: () => expectData(api.GET('/api/players/{id}', { params: { path: { id: playerId } } })),
    retry: false,
  });
}
