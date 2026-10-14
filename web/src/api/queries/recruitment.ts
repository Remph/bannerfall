import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { refetchDelayMs } from '../../game/buildQueue';
import type { UnitType } from '../../game/gameData';
import { nextUnitDueMs } from '../../game/recruitment';
import { serverNow } from '../../game/serverTime';
import { api, expectData } from '../client';
import type { components } from '../schema';
import { paladinQueryKey } from './paladin';
import { villageQueryKey } from './villages';

/** One recruiting building of GET …/recruitment: its level, its units and its queue. */
export type RecruitBuilding = components['schemas']['RecruitBuildingDto'];
export type RecruitUnit = components['schemas']['RecruitUnitDto'];
export type RecruitOrder = components['schemas']['RecruitOrderDto'];
export type DismissOrder = components['schemas']['DismissOrderDto'];

export function recruitmentQueryKey(villageId: number) {
  return ['recruitment', villageId] as const;
}

/** GET /api/villages/{id}/recruitment: the recruiting buildings (the Banner Hall only with banners on), also those not built yet. */
export function useRecruitment(villageId: number) {
  return useQuery({
    queryKey: recruitmentQueryKey(villageId),
    queryFn: () => expectData(api.GET('/api/villages/{villageId}/recruitment', { params: { path: { villageId } } })),
  });
}

/**
 * Recruiting touches the village (resources, population, units at home) and the recruitment list.
 * Returning the promise keeps a mutation "pending" until the fresh data is there.
 */
function refreshRecruitState(queryClient: QueryClient, villageId: number): Promise<unknown> {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: villageQueryKey(villageId) }),
    queryClient.invalidateQueries({ queryKey: recruitmentQueryKey(villageId) }),
    // the paladin's slots follow his orders (M31)
    queryClient.invalidateQueries({ queryKey: paladinQueryKey }),
  ]);
}

/** POST …/recruit-orders: train `count` units of one kind (in the queue of their building). */
export function useEnqueueRecruit(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ unit, count }: { unit: UnitType; count: number }) =>
      expectData(
        api.POST('/api/villages/{villageId}/recruit-orders', {
          params: { path: { villageId } },
          body: { unit, count },
        }),
      ),
    // also after an error: the screen was probably out of date (e.g. resources spent elsewhere)
    onSettled: () => refreshRecruitState(queryClient, villageId),
  });
}

/**
 * POST …/recruit-orders/all ("recruit all", M27): several orders at once, all or none. When one is
 * refused, nothing is queued and the error's `unit` says which one it was.
 */
export function useEnqueueRecruitAll(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (orders: readonly { unit: UnitType; count: number }[]) =>
      expectData(
        api.POST('/api/villages/{villageId}/recruit-orders/all', {
          params: { path: { villageId } },
          body: { orders: [...orders] },
        }),
      ),
    onSettled: () => refreshRecruitState(queryClient, villageId),
  });
}

/** DELETE …/recruit-orders/{orderId}: cancel the units of an order that aren't done yet. */
export function useCancelRecruit(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (orderId: number) => {
      // 204 No Content: nothing to read, errors are thrown by the client middleware
      await api.DELETE('/api/villages/{villageId}/recruit-orders/{orderId}', {
        params: { path: { villageId, orderId } },
      });
    },
    onSettled: () => refreshRecruitState(queryClient, villageId),
  });
}

/** POST …/dismiss-orders: dismiss `count` units of the home stack (they leave it at once). */
export function useDismiss(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ unit, count }: { unit: UnitType; count: number }) =>
      expectData(
        api.POST('/api/villages/{villageId}/dismiss-orders', {
          params: { path: { villageId } },
          body: { unit, count },
        }),
      ),
    onSettled: () => refreshRecruitState(queryClient, villageId),
  });
}

/** DELETE …/dismiss-orders/{orderId}: the units not dismissed yet go back home. */
export function useCancelDismiss(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (orderId: number) => {
      await api.DELETE('/api/villages/{villageId}/dismiss-orders/{orderId}', {
        params: { path: { villageId, orderId } },
      });
    },
    onSettled: () => refreshRecruitState(queryClient, villageId),
  });
}

/**
 * Reloads the village and its recruitment when the next unit of a running order is done, so it shows
 * up at home (05-frontend §4). Recruitment has no events: the server moves finished units home
 * whenever it reads the village, so asking is enough. Used by the screens that show units (the
 * recruit page and the village overview), not in AppLayout: that would cost an extra request on
 * every screen. One timer; every answer sets it anew (`dataUpdatedAt`, `errorUpdatedAt`), and an
 * answer that doesn't have the unit yet (server clock a bit behind) is asked again after a second.
 */
export function useRefreshWhenUnitsDone(
  villageId: number,
  buildings: readonly RecruitBuilding[] | undefined,
  dataUpdatedAt: number,
  errorUpdatedAt: number,
) {
  const queryClient = useQueryClient();
  // dismissed units free their population one by one, so the village is read when one is due too
  const nextDue =
    buildings === undefined
      ? undefined
      : nextUnitDueMs(buildings.flatMap((info) => [...info.queue, ...info.dismissQueue]));

  useEffect(() => {
    if (nextDue === undefined) {
      return;
    }
    const timer = setTimeout(
      () => void refreshRecruitState(queryClient, villageId),
      refetchDelayMs(nextDue, serverNow()),
    );
    return () => clearTimeout(timer);
  }, [queryClient, villageId, nextDue, dataUpdatedAt, errorUpdatedAt]);
}
