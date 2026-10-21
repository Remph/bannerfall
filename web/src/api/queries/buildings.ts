import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { refetchDelayMs } from '../../game/buildQueue';
import type { BuildingType } from '../../game/gameData';
import { serverNow } from '../../game/serverTime';
import { api, expectData } from '../client';
import type { components } from '../schema';
import { automationQueryKey } from './automation';
import { recruitmentQueryKey } from './recruitment';
import { villageQueryKey, type Village } from './villages';

export type BuildOrder = components['schemas']['BuildOrderDto'];
export type NextLevel = components['schemas']['NextLevelDto'];
export type BuildingEffect = components['schemas']['BuildingEffect'];
/** The next demolition of a building (1.6); null at its lowest level. */
export type Demolition = components['schemas']['DemolishDto'];

/** One building of GET …/buildings; `next` is null at the maximum level. */
export type BuildingInfo = components['schemas']['BuildingInfoDto'];

export function buildingsQueryKey(villageId: number) {
  return ['buildings', villageId] as const;
}

/** GET /api/villages/{id}/buildings: every building with its next upgrade (Town Hall screen). */
export function useBuildings(villageId: number) {
  return useQuery({
    queryKey: buildingsQueryKey(villageId),
    queryFn: (): Promise<BuildingInfo[]> =>
      expectData(api.GET('/api/villages/{villageId}/buildings', { params: { path: { villageId } } })),
  });
}

/**
 * A change of the build queue touches the village (resources, queue, population), the building
 * list and the automation's status (cancelling one of its orders pauses it, 1.6). Returning the promise keeps a mutation "pending" until the fresh data is there, so buttons
 * don't flash enabled in between.
 */
function refreshBuildState(queryClient: QueryClient, villageId: number): Promise<unknown> {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: villageQueryKey(villageId) }),
    queryClient.invalidateQueries({ queryKey: buildingsQueryKey(villageId) }),
    queryClient.invalidateQueries({ queryKey: automationQueryKey(villageId) }),
  ]);
}

/** POST …/build-orders: queue the next level of a building. */
export function useEnqueueBuild(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (building: BuildingType) =>
      expectData(
        api.POST('/api/villages/{villageId}/build-orders', { params: { path: { villageId } }, body: { building } }),
      ),
    // also after an error: the screen was probably out of date (e.g. resources spent elsewhere)
    onSettled: () => refreshBuildState(queryClient, villageId),
  });
}

/** POST …/demolitions: queue a demolition of one level (1.6, 02-game-rules §4.5). */
export function useDemolish(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (building: BuildingType) =>
      expectData(
        api.POST('/api/villages/{villageId}/demolitions', { params: { path: { villageId } }, body: { building } }),
      ),
    onSettled: () => refreshBuildState(queryClient, villageId),
  });
}

/** DELETE …/build-orders/{orderId}: cancel an order (and the ones that depend on it). */
export function useCancelBuild(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (orderId: number) => {
      // 204 No Content: nothing to read, errors are thrown by the client middleware
      await api.DELETE('/api/villages/{villageId}/build-orders/{orderId}', {
        params: { path: { villageId, orderId } },
      });
    },
    onSettled: () => refreshBuildState(queryClient, villageId),
  });
}

/** POST …/build-orders/{orderId}/move: put an order at another place (0-based) in the queue. */
export function useMoveBuild(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ orderId, position }: { orderId: number; position: number }) =>
      expectData(
        api.POST('/api/villages/{villageId}/build-orders/{orderId}/move', {
          params: { path: { villageId, orderId } },
          body: { position },
        }),
      ),
    // The answer is the new queue. Only the queue changed, so the cached village gets a copy with the
    // new queue instead of a reload. (Cached data is never changed in place: React would miss it.)
    onSuccess: (buildQueue) => {
      queryClient.setQueryData<Village>(villageQueryKey(villageId), (village) =>
        village === undefined ? undefined : { ...village, buildQueue },
      );
    },
    onError: () => refreshBuildState(queryClient, villageId),
  });
}

/**
 * Reloads the village and its building list shortly after the running order ends, so the new
 * level, production and points appear by themselves (05-frontend §4). The recruitment too: a new
 * level unlocks units (Smithy 2 → Axeman) and shortens recruit times; it only refetches while a
 * screen shows it. Used once in AppLayout, so it works on every screen. One timer for the next
 * end; every answer from the server sets it anew:
 * a new village (`dataUpdatedAt`) covers "the server hasn't applied it yet, ask again", a failed
 * reload (`errorUpdatedAt`) keeps it trying instead of stopping at 0:00:00.
 */
export function useRefreshWhenBuildsEnd(village: Village | undefined, dataUpdatedAt: number, errorUpdatedAt: number) {
  const queryClient = useQueryClient();
  const villageId = village?.id;
  const nextEnd = village?.buildQueue[0]?.endAt;

  useEffect(() => {
    if (villageId === undefined || nextEnd === undefined) {
      return;
    }
    const timer = setTimeout(
      () => {
        void refreshBuildState(queryClient, villageId);
        void queryClient.invalidateQueries({ queryKey: recruitmentQueryKey(villageId) });
      },
      refetchDelayMs(Date.parse(nextEnd), serverNow()),
    );
    // The cleanup runs before the effect runs again and when the component goes away: no stray timers.
    return () => clearTimeout(timer);
  }, [queryClient, villageId, nextEnd, dataUpdatedAt, errorUpdatedAt]);
}
