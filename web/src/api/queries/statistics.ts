import { useQuery } from '@tanstack/react-query';
import { api, expectData } from '../client';
import type { components } from '../schema';

export type WorldStatistics = components['schemas']['WorldStatisticsDto'];

export const worldStatisticsQueryKey = ['statistics', 'world'] as const;

/** GET /api/statistics/world: the world per day and its leaders' points (1.5). */
export function useWorldStatistics() {
  return useQuery({
    queryKey: worldStatisticsQueryKey,
    queryFn: () => expectData(api.GET('/api/statistics/world')),
  });
}
