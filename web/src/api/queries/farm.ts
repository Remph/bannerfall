import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import type { Coords } from '../../game/coords';
import type { TemplateLetter } from '../../game/farmTemplates';
import { templateOf } from '../../game/farm';
import type { UnitMap } from '../../game/troops';
import { api, expectData } from '../client';
import type { components } from '../schema';
import { meQueryKey } from './auth';
import { mapMovementsQueryKey } from './map';
import { troopsQueryKey } from './troops';
import { villageQueryKey } from './villages';

export type FarmTarget = components['schemas']['FarmTargetDto'];

/** Every village's farm list (a prefix for `invalidateQueries`). */
export const farmListsQueryKey = ['farm'] as const;

export function farmQueryKey(villageId: number) {
  return [...farmListsQueryKey, villageId] as const;
}

/** GET /api/villages/{id}/farm-targets: the village's list in the order the targets were added. */
export function useFarmTargets(villageId: number) {
  return useQuery({
    queryKey: farmQueryKey(villageId),
    queryFn: () => expectData(api.GET('/api/villages/{villageId}/farm-targets', { params: { path: { villageId } } })),
  });
}

/**
 * What an entry sends (1.4): one of the player's templates, or its own units. The server takes
 * exactly one of the two.
 */
export type FarmTroops = { template: TemplateLetter } | { units: UnitMap };

function troopsBody(troops: FarmTroops) {
  return 'template' in troops
    ? { units: null, template: troops.template }
    : { units: troops.units as Record<string, number>, template: null };
}

/** POST …/farm-targets: add a target with its template or own units (201). */
export function useAddFarmTarget(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ target, troops }: { target: Coords; troops: FarmTroops }) =>
      expectData(
        api.POST('/api/villages/{villageId}/farm-targets', {
          params: { path: { villageId } },
          body: { target, ...troopsBody(troops) },
        }),
      ),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: farmQueryKey(villageId) }),
  });
}

/** PUT …/farm-targets/{id}: another template or own units for a target. */
export function useChangeFarmUnits(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ targetId, troops }: { targetId: number; troops: FarmTroops }) =>
      expectData(
        api.PUT('/api/villages/{villageId}/farm-targets/{targetId}', {
          params: { path: { villageId, targetId } },
          body: troopsBody(troops),
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
 * POST …/farm-targets/send-all (M39): the whole list from the top, one attack per entry; an entry the
 * troops don't cover is skipped (1.3). The answer says what went out and what was skipped and why.
 * `bannerChanceOnly` (1.2.3) leaves out the entries whose banner roll is used.
 */
export function useSendAllFarm(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (bannerChanceOnly: boolean) =>
      expectData(
        api.POST('/api/villages/{villageId}/farm-targets/send-all', {
          params: { path: { villageId }, query: { bannerChanceOnly } },
        }),
      ),
    onSettled: () => refreshAfterSend(queryClient, villageId),
  });
}

/** The troops of a template choice (`TemplateChoice`): the template, or the own counts for `null`. */
export function troopsOfChoice(letter: TemplateLetter | null, counts: UnitMap): FarmTroops {
  return letter === null ? { units: templateOf(counts) } : { template: letter };
}
