import { useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { withBroadcast } from '../../game/broadcast';
import { api, expectData } from '../client';
import type { components } from '../schema';

/** A popup message of the host as `GET /api/broadcasts` and `/topic/broadcast` send it (1.7). */
export type Broadcast = components['schemas']['BroadcastDto'];

/** The broadcasts of the last ten minutes, oldest first; live ones are added by the realtime layer. */
export const broadcastsQueryKey = ['broadcasts'] as const;

/** Adds a broadcast that came over STOMP. */
export function addBroadcast(queryClient: QueryClient, broadcast: Broadcast): void {
  queryClient.setQueryData<Broadcast[]>(broadcastsQueryKey, (list) => withBroadcast(list ?? [], broadcast));
}

export function useBroadcasts() {
  const queryClient = useQueryClient();
  return useQuery({
    queryKey: broadcastsQueryKey,
    queryFn: async () => {
      const latest = await expectData(api.GET('/api/broadcasts'));
      // live ones may have come while the answer was on its way
      return (queryClient.getQueryData<Broadcast[]>(broadcastsQueryKey) ?? []).reduce(
        (list, live) => withBroadcast(list, live),
        latest,
      );
    },
  });
}
