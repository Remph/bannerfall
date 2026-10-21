import { useQuery } from '@tanstack/react-query';
import { api, expectData } from '../client';
import type { components } from '../schema';

/** The price of the player's next noble and why (1.6, 02-game-rules §5.7). */
export type Noble = components['schemas']['NobleDto'];

export const nobleQueryKey = ['noble'] as const;

/** GET /api/noble: rank, step and the next nobles' prices. */
export function useNoble(enabled = true) {
  return useQuery({
    queryKey: nobleQueryKey,
    queryFn: () => expectData(api.GET('/api/noble')),
    enabled,
  });
}
