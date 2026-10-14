import { useQuery } from '@tanstack/react-query';
import { api, expectData } from '../client';
import type { components } from '../schema';

/** The player's paladin slots (M31, 02-game-rules §5.5). */
export type Paladin = components['schemas']['PaladinDto'];

export const paladinQueryKey = ['paladin'] as const;

/** GET /api/paladin: slots, paladins that take one and when the next slot opens. */
export function usePaladin(enabled = true) {
  return useQuery({
    queryKey: paladinQueryKey,
    queryFn: () => expectData(api.GET('/api/paladin')),
    enabled,
  });
}
