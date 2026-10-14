import { useQuery } from '@tanstack/react-query';
import { api, expectData } from '../client';
import type { components } from '../schema';

export type Achievement = components['schemas']['AchievementDto'];
export type EarnedAchievement = components['schemas']['EarnedAchievementDto'];

export const achievementsQueryKey = ['achievements'] as const;

/** GET /api/achievements: all achievements with the player's own progress (earned ones are public, progress is not). */
export function useAchievements() {
  return useQuery({
    queryKey: achievementsQueryKey,
    queryFn: () => expectData(api.GET('/api/achievements')),
  });
}
