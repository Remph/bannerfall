import { skipToken, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, expectData } from '../client';
import type { components } from '../schema';
import { meQueryKey } from './auth';
import { mapQueryKey } from './map';

export type Village = components['schemas']['VillageDto'];
export type VillageSummary = components['schemas']['VillageSummaryDto'];

export function villageQueryKey(villageId: number | undefined) {
  return ['village', villageId] as const;
}

/** GET /api/villages/{id}. Without an id (no village known yet) the query simply doesn't run. */
export function useVillage(villageId: number | undefined) {
  return useQuery({
    queryKey: villageQueryKey(villageId),
    // skipToken = "don't fetch"; TypeScript then knows villageId is a number inside the arrow function.
    queryFn:
      villageId === undefined
        ? skipToken
        : () => expectData(api.GET('/api/villages/{id}', { params: { path: { id: villageId } } })),
  });
}

/** PATCH /api/villages/{id}. The answer is the updated village; the name also appears in /api/me and on the map. */
export function useRenameVillage(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (name: string) =>
      expectData(api.PATCH('/api/villages/{id}', { params: { path: { id: villageId } }, body: { name } })),
    onSuccess: (village) => {
      queryClient.setQueryData(villageQueryKey(villageId), village);
      void queryClient.invalidateQueries({ queryKey: meQueryKey });
      void queryClient.invalidateQueries({ queryKey: mapQueryKey }); // the name is on the map too
    },
  });
}

/**
 * PUT /api/villages/{id}/tag (1.7): the player's own tag on his village, 1 to 5 capital letters or
 * digits; a blank one removes it. Only /api/me carries tags, so that reloads.
 */
export function useSaveVillageTag(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (tag: string) =>
      expectData(api.PUT('/api/villages/{id}/tag', { params: { path: { id: villageId } }, body: { tag } })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: meQueryKey }),
  });
}
