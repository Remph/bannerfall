import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { refetchDelayMs } from '../../game/buildQueue';
import { byUnitOrder, type UnitType } from '../../game/gameData';
import { serverNow } from '../../game/serverTime';
import { api, expectData } from '../client';
import type { components } from '../schema';
import { villageQueryKey } from './villages';

/** GET …/smithy: every unit with its stats, its level and the next research (M30). */
export type Smithy = components['schemas']['SmithyDto'];
export type SmithyUnit = components['schemas']['SmithyUnitDto'];
export type NextResearch = components['schemas']['NextResearchDto'];
export type ResearchOrder = components['schemas']['ResearchOrderDto'];
/** GET …/banner-hall: the paladin's and the thief's levels and their next training (1.4). */
export type HallTraining = components['schemas']['HallTrainingDto'];

export function smithyQueryKey(villageId: number) {
  return ['smithy', villageId] as const;
}

export function hallTrainingQueryKey(villageId: number) {
  return ['hallTraining', villageId] as const;
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
    // the server appends the archers (1.6): the UI shows them next to their building's units
    select: (smithy): Smithy => ({ ...smithy, units: [...smithy.units].sort((a, b) => byUnitOrder(a.unit, b.unit)) }),
  });
}

/**
 * GET /api/villages/{id}/banner-hall (Hall training, 1.4): like the smithy, for the Banner Hall's
 * own units. Only in a world that has the hall (banners or the paladin on).
 */
export function useHallTraining(villageId: number, enabled = true) {
  return useQuery({
    queryKey: hallTrainingQueryKey(villageId),
    enabled,
    queryFn: () => expectData(api.GET('/api/villages/{villageId}/banner-hall', { params: { path: { villageId } } })),
  });
}

/**
 * A research or training costs resources and fills its building: the village, the smithy and the
 * hall are read again (the smithy also shows the hall's levels).
 */
function refreshResearchState(queryClient: QueryClient, villageId: number): Promise<unknown> {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: villageQueryKey(villageId) }),
    queryClient.invalidateQueries({ queryKey: smithyQueryKey(villageId) }),
    queryClient.invalidateQueries({ queryKey: hallTrainingQueryKey(villageId) }),
  ]);
}

/** POST …/research-orders: start the next level of a unit, in the smithy or the Banner Hall. */
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
 * Reads the smithy (or the hall) and the village again when the next research is done, so the new level shows
 * (05-frontend §4). Research has no events either: the server counts a level from its end time on,
 * so asking again is enough. One timer; every answer sets it anew, and an answer that still has the
 * research (server clock a bit behind) is asked again after a second.
 */
export function useRefreshWhenResearchEnds(
  villageId: number,
  smithy: ResearchPage | undefined,
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

/** What the smithy page and the hall's training have in common. */
type ResearchPage = Pick<Smithy, 'running' | 'units'>;

/** The end times (ms since 1970) of every research the page knows of: in this village and elsewhere. */
function researchEnds(smithy: ResearchPage): number[] {
  const orders = [smithy.running, ...smithy.units.map((unit) => unit.researching)];
  return orders.flatMap((order) => (order === null ? [] : [Date.parse(order.endAt)]));
}
