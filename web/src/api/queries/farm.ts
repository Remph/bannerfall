import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import type { Coords } from '../../game/coords';
import type { UnitMap } from '../../game/troops';
import { api, expectData } from '../client';
import type { components } from '../schema';
import { meQueryKey } from './auth';
import { mapMovementsQueryKey } from './map';
import { troopsQueryKey } from './troops';
import { villageQueryKey } from './villages';

export type FarmTarget = components['schemas']['FarmTargetDto'];

export function farmQueryKey(villageId: number) {
  return ['farm', villageId] as const;
}

/** GET /api/villages/{id}/farm-targets: the village's list in the order the targets were added. */
export function useFarmTargets(villageId: number) {
  return useQuery({
    queryKey: farmQueryKey(villageId),
    queryFn: () => expectData(api.GET('/api/villages/{villageId}/farm-targets', { params: { path: { villageId } } })),
  });
}

/** POST …/farm-targets: add a target with its template (201). */
export function useAddFarmTarget(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ target, units }: { target: Coords; units: UnitMap }) =>
      expectData(
        api.POST('/api/villages/{villageId}/farm-targets', {
          params: { path: { villageId } },
          body: { target, units: units as Record<string, number> },
        }),
      ),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: farmQueryKey(villageId) }),
  });
}

/** PUT …/farm-targets/{id}: a new template for a target. */
export function useChangeFarmUnits(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ targetId, units }: { targetId: number; units: UnitMap }) =>
      expectData(
        api.PUT('/api/villages/{villageId}/farm-targets/{targetId}', {
          params: { path: { villageId, targetId } },
          body: { units: units as Record<string, number> },
        }),
      ),
    onSettled: () => queryClient.invalidateQueries({ queryKey: farmQueryKey(villageId) }),
  });
}

/** DELETE …/farm-targets/{id}. */
export function useRemoveFarmTarget(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (targetId: number) => {
      await api.DELETE('/api/villages/{villageId}/farm-targets/{targetId}', {
        params: { path: { villageId, targetId } },
      });
    },
    // also after an error: it may be gone already
    onSettled: () => queryClient.invalidateQueries({ queryKey: farmQueryKey(villageId) }),
  });
}

/** What a send changes: the list (last sent), the units at home, the rally point's lists, protection. */
function refreshAfterSend(queryClient: QueryClient, villageId: number): Promise<unknown> {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: farmQueryKey(villageId) }),
    queryClient.invalidateQueries({ queryKey: villageQueryKey(villageId) }),
    queryClient.invalidateQueries({ queryKey: troopsQueryKey(villageId) }),
    // an attack on a player ends the sender's own beginner protection (02-game-rules §6)
    queryClient.invalidateQueries({ queryKey: meQueryKey }),
    // the new attack's marker on the map (M26)
    queryClient.invalidateQueries({ queryKey: mapMovementsQueryKey }),
  ]);
}

/** POST …/farm-targets/{id}/send: one attack with the target's template; the answer is the movement. */
export function useSendFarm(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (targetId: number) =>
      expectData(
        api.POST('/api/villages/{villageId}/farm-targets/{targetId}/send', {
          params: { path: { villageId, targetId } },
        }),
      ),
    // also after an error: the units at home were probably not what the screen showed
    onSettled: () => refreshAfterSend(queryClient, villageId),
  });
}

/**
 * POST …/farm-targets/send-all (M39): the list from the top, one attack per entry, until it is done
 * or the troops run out. The answer says what went out, what was skipped and where it stopped.
 */
export function useSendAllFarm(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      expectData(
        api.POST('/api/villages/{villageId}/farm-targets/send-all', {
          params: { path: { villageId } },
        }),
      ),
    onSettled: () => refreshAfterSend(queryClient, villageId),
  });
}
