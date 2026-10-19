import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api, expectData } from '../client';
import type { components, operations } from '../schema';

export type RankingPage = components['schemas']['RankingPageDto'];
export type RankingEntry = components['schemas']['RankingEntryDto'];
export type PlayerProfile = components['schemas']['PlayerProfileDto'];
export type PlayerStats = components['schemas']['PlayerStatsDto'];
export type PlayerHistory = components['schemas']['PlayerHistoryDto'];
/** What a ranking sorts by: points, or a statistics total (the tabs, 1.5). */
export type RankingKind = NonNullable<NonNullable<operations['ranking']['parameters']['query']>['by']>;

/** Lines per page of the ranking. */
export const RANKING_PAGE_SIZE = 50;

export const rankingQueryKey = ['ranking'] as const;
export const playerQueryKey = ['player'] as const;

/**
 * GET /api/ranking: one page (0-based) of one tab; the old page stays on screen while the next
 * loads.
 */
export function useRanking(page: number, by: RankingKind = 'POINTS') {
  return useQuery({
    queryKey: [...rankingQueryKey, by, page],
    queryFn: () => expectData(api.GET('/api/ranking', { params: { query: { page, size: RANKING_PAGE_SIZE, by } } })),
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

/** GET /api/players/{id}/history: points, villages and rank per day, today's last (1.5). */
export function usePlayerHistory(playerId: number) {
  return useQuery({
    queryKey: [...playerQueryKey, playerId, 'history'],
    queryFn: () => expectData(api.GET('/api/players/{id}/history', { params: { path: { id: playerId } } })),
    retry: false,
  });
}
