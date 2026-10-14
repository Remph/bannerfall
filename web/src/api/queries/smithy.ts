import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { refetchDelayMs } from '../../game/buildQueue';
import type { UnitType } from '../../game/gameData';
import { serverNow } from '../../game/serverTime';
import { api, expectData } from '../client';
import type { components } from '../schema';
import { villageQueryKey } from './villages';

/** GET …/smithy: every unit with its stats, its level and the next research (M30). */
export type Smithy = components['schemas']['SmithyDto'];
export type SmithyUnit = components['schemas']['SmithyUnitDto'];
export type NextResearch = components['schemas']['NextResearchDto'];
export type ResearchOrder = components['schemas']['ResearchOrderDto'];

export function smithyQueryKey(villageId: number) {
  return ['smithy', villageId] as const;
}

/**
 * GET /api/villages/{id}/smithy. Levels have no events: the server counts a research as done once
 * its end time has passed, so asking again after the end is enough (see SmithyPage).
 */
export function useSmithy(villageId: number, enabled = true) {
  return useQuery({
    queryKey: smithyQueryKey(villageId),
    enabled,
    queryFn: () => expectData(api.GET('/api/villages/{villageId}/smithy', { params: { path: { villageId } } })),
  });
}

/** A research costs resources and fills the smithy: the village and the smithy are read again. */
function refreshResearchState(queryClient: QueryClient, villageId: number): Promise<unknown> {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: villageQueryKey(villageId) }),
    queryClient.invalidateQueries({ queryKey: smithyQueryKey(villageId) }),
  ]);
}

/** POST …/research-orders: start the next level of a unit. */
export function useStartResearch(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (unit: UnitType) =>
      expectData(
        api.POST('/api/villages/{villageId}/research-orders', {
          params: { path: { villageId } },
          body: { unit },
        }),
      ),
    // also after an error: the screen was probably out of date
    onSettled: () => refreshResearchState(queryClient, villageId),
  });
}

/** DELETE …/research-orders/{orderId}: stop a running research, the full cost comes back. */
export function useCancelResearch(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (orderId: number) => {
      await api.DELETE('/api/villages/{villageId}/research-orders/{orderId}', {
        params: { path: { villageId, orderId } },
      });
    },
    onSettled: () => refreshResearchState(queryClient, villageId),
  });
}

/**
 * Reads the smithy and the village again when the next research is done, so the new level shows
 * (05-frontend §4). Research has no events either: the server counts a level from its end time on,
 * so asking again is enough. One timer; every answer sets it anew, and an answer that still has the
 * research (server clock a bit behind) is asked again after a second.
 */
export function useRefreshWhenResearchEnds(
  villageId: number,
  smithy: Smithy | undefined,
  dataUpdatedAt: number,
  errorUpdatedAt: number,
) {
  const queryClient = useQueryClient();
  const ends = smithy === undefined ? [] : researchEnds(smithy);
  const nextEnd = ends.length === 0 ? undefined : Math.min(...ends);

  useEffect(() => {
    if (nextEnd === undefined) {
      return;
    }
    const timer = setTimeout(
      () => void refreshResearchState(queryClient, villageId),
      refetchDelayMs(nextEnd, serverNow()),
    );
    return () => clearTimeout(timer);
  }, [queryClient, villageId, nextEnd, dataUpdatedAt, errorUpdatedAt]);
}

/** The end times (ms since 1970) of every research the page knows of: in this village and elsewhere. */
function researchEnds(smithy: Smithy): number[] {
  const orders = [smithy.running, ...smithy.units.map((unit) => unit.researching)];
  return orders.flatMap((order) => (order === null ? [] : [Date.parse(order.endAt)]));
}
