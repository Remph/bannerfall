import {
  queryOptions,
  skipToken,
  useMutation,
  useQueries,
  useQuery,
  useQueryClient,
  type QueryClient,
} from '@tanstack/react-query';
import { useEffect } from 'react';
import { refetchDelayMs } from '../../game/buildQueue';
import { serverNow } from '../../game/serverTime';
import { nextArrivalMs, type UnitMap } from '../../game/troops';
import { api, expectData } from '../client';
import type { components } from '../schema';
import { meQueryKey } from './auth';
import { mapMovementsQueryKey } from './map';
import { refreshReports } from './reports';
import { villageQueryKey } from './villages';

/** GET …/troops: units at home, supports here, own units elsewhere, outgoing and incoming movements. */
export type Troops = components['schemas']['TroopsDto'];
export type Movement = components['schemas']['MovementDto'];
export type IncomingMovement = components['schemas']['IncomingDto'];
export type SupportHere = components['schemas']['SupportHereDto'];
export type UnitsElsewhere = components['schemas']['UnitsElsewhereDto'];
/** A village inside movements and stacks: `name`, `playerId` and `playerName` are null for barbarians. */
export type VillageRef = components['schemas']['VillageRefDto'];
export type CommandRequest = components['schemas']['CommandRequest'];
export type CommandPreview = components['schemas']['CommandPreviewDto'];
/** An attack train (M37): several attacks at one target, sent together. */
export type TrainRequest = components['schemas']['TrainRequest'];
export type TrainPreview = components['schemas']['TrainPreviewDto'];

export function troopsQueryKey(villageId: number) {
  return ['troops', villageId] as const;
}

/** Prefix of all preview queries (a failed send makes the open preview ask again). */
const previewQueryKey = ['commandPreview'] as const;

/** GET /api/villages/{id}/troops (rally point). */
export function useTroops(villageId: number) {
  return useQuery({
    queryKey: troopsQueryKey(villageId),
    queryFn: () => expectData(api.GET('/api/villages/{villageId}/troops', { params: { path: { villageId } } })),
  });
}

/**
 * Sending, cancelling or withdrawing changes the troops lists and the units at home (the village).
 * Returning the promise keeps a mutation "pending" until the fresh data is there.
 */
function refreshTroops(queryClient: QueryClient, villageId: number): Promise<unknown> {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: troopsQueryKey(villageId) }),
    queryClient.invalidateQueries({ queryKey: villageQueryKey(villageId) }),
    // the markers on the map (M26)
    queryClient.invalidateQueries({ queryKey: mapMovementsQueryKey }),
  ]);
}

/**
 * POST …/commands/preview: the command as it would be if it were sent now (target village,
 * distance, travel time) and every rule it breaks (`errors`). It is a POST but changes nothing, so
 * it is a query: it runs while the confirmation sheet shows it (`request` set) and is forgotten
 * when the sheet closes (`gcTime: 0`). Only a missing target village is an error (404).
 */
export function useCommandPreview(villageId: number, request: CommandRequest | undefined) {
  return useQuery(previewQuery(villageId, request));
}

/**
 * The previews of several commands at once, e.g. one per farm template on the map (1.4: when each
 * would arrive, for the night bonus); none are asked for while `enabled` is false.
 */
export function useCommandPreviews(villageId: number, requests: CommandRequest[], enabled: boolean) {
  return useQueries({ queries: requests.map((request) => previewQuery(villageId, enabled ? request : undefined)) });
}

function previewQuery(villageId: number, request: CommandRequest | undefined) {
  return queryOptions({
    queryKey: [...previewQueryKey, villageId, request],
    queryFn:
      request === undefined
        ? skipToken
        : () =>
            expectData(
              api.POST('/api/villages/{villageId}/commands/preview', {
                params: { path: { villageId } },
                body: request,
              }),
            ),
    staleTime: 0,
    gcTime: 0,
  });
}

/** POST …/commands: send an attack or support. The answer is the new movement (201). */
export function useSendCommand(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CommandRequest) =>
      expectData(api.POST('/api/villages/{villageId}/commands', { params: { path: { villageId } }, body: request })),
    onSuccess: (movement) => {
      if (movement.kind === 'ATTACK') {
        // an attack on a player ends the sender's own beginner protection, one on a barbarian village doesn't (02-game-rules §6)
        void queryClient.invalidateQueries({ queryKey: meQueryKey });
      }
    },
    // the open preview shows why it failed (e.g. units went elsewhere meanwhile)
    onError: () => queryClient.invalidateQueries({ queryKey: previewQueryKey }),
    // also after an error: the screen was probably out of date
    onSettled: () => refreshTroops(queryClient, villageId),
  });
}

/**
 * POST …/commands/train/preview (M37): the train as it would be if it were sent now, with the rules
 * each attack breaks. A query like `useCommandPreview`.
 */
export function useTrainPreview(villageId: number, request: TrainRequest | undefined) {
  return useQuery({
    queryKey: [...previewQueryKey, 'train', villageId, request],
    queryFn:
      request === undefined
        ? skipToken
        : () =>
            expectData(
              api.POST('/api/villages/{villageId}/commands/train/preview', {
                params: { path: { villageId } },
                body: request,
              }),
            ),
    staleTime: 0,
    gcTime: 0,
  });
}

/** POST …/commands/train: send all attacks of the train, or none. The answer is the new movements (201). */
export function useSendTrain(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: TrainRequest) =>
      expectData(
        api.POST('/api/villages/{villageId}/commands/train', { params: { path: { villageId } }, body: request }),
      ),
    // like a single attack: one on a player ends the sender's own beginner protection
    onSuccess: () => queryClient.invalidateQueries({ queryKey: meQueryKey }),
    onError: () => queryClient.invalidateQueries({ queryKey: previewQueryKey }),
    onSettled: () => refreshTroops(queryClient, villageId),
  });
}

/** POST /api/movements/{id}/cancel: the command turns around (it becomes a RETURN). */
export function useCancelMovement(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (movementId: number) =>
      expectData(api.POST('/api/movements/{movementId}/cancel', { params: { path: { movementId } } })),
    onSettled: () => refreshTroops(queryClient, villageId),
  });
}

/**
 * POST /api/stacks/{id}/return: some or all units of a stack walk home – withdrawing own units from
 * another village, or sending a support in this village back.
 */
export function useReturnStack(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ stackId, units }: { stackId: number; units: UnitMap }) =>
      expectData(api.POST('/api/stacks/{stackId}/return', { params: { path: { stackId } }, body: { units } })),
    onSettled: () => refreshTroops(queryClient, villageId),
  });
}

/**
 * Reloads the troops and the village shortly after the next movement arrives (outgoing or
 * incoming), so arrived supports, returning units and loot show up by themselves; the reports too
 * (battles and supports write them on arrival). Arrivals are events of the server's worker, so
 * this works like the build queue's refresh (05-frontend §4): one timer, set anew by every answer;
 * an answer that still has a movement that is due asks again. Used by the rally point while it is
 * open.
 */
export function useRefreshWhenMovementsArrive(
  villageId: number,
  troops: Troops | undefined,
  dataUpdatedAt: number,
  errorUpdatedAt: number,
) {
  const queryClient = useQueryClient();
  const nextArrival = troops === undefined ? undefined : nextArrivalMs([...troops.outgoing, ...troops.incoming]);

  useEffect(() => {
    if (nextArrival === undefined) {
      return;
    }
    const timer = setTimeout(
      () => {
        void refreshTroops(queryClient, villageId);
        void refreshReports(queryClient);
      },
      refetchDelayMs(nextArrival, serverNow()),
    );
    return () => clearTimeout(timer);
  }, [queryClient, villageId, nextArrival, dataUpdatedAt, errorUpdatedAt]);
}
