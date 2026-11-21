import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, expectData } from '../client';
import type { components } from '../schema';
import { guildsQueryKey } from './guilds';

export type Diplomacy = components['schemas']['DiplomacyDto'];
export type Lord = components['schemas']['LordDto'];
export type TrustChange = components['schemas']['TrustChangeDto'];
export type TrustReason = TrustChange['reason'];

/**
 * GET /api/diplomacy (2.1 S2b): under the guilds' prefix, so every guild change (a lord joined,
 * said no or left: GUILD_CHANGED) loads it again.
 */
export const diplomacyQueryKey = [...guildsQueryKey, 'diplomacy'] as const;

/** The court, the allies and the other lords with their trust (12-solo-and-allies §7). */
export function useDiplomacy(enabled = true) {
  return useQuery({
    queryKey: diplomacyQueryKey,
    queryFn: () => expectData(api.GET('/api/diplomacy')),
    enabled,
  });
}

/** POST /api/diplomacy/lords/{id}/invite: the lord answers by letter in its next round. */
export function useInviteLord() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (lordId: number) =>
      expectData(api.POST('/api/diplomacy/lords/{lordId}/invite', { params: { path: { lordId } } })),
    onSuccess: (page) => {
      queryClient.setQueryData(diplomacyQueryKey, page);
      return queryClient.invalidateQueries({ queryKey: guildsQueryKey, refetchType: 'active' });
    },
    onError: () => queryClient.invalidateQueries({ queryKey: diplomacyQueryKey }),
  });
}

export type OrderSheet = components['schemas']['OrderSheetDto'];
export type OrderAlly = components['schemas']['OrderAllyDto'];
export type Order = components['schemas']['OrderDto'];
export type OrderKind = Order['kind'];
export type OrderRequest = components['schemas']['OrderRequest'];

/** The order sheet of one village; under the diplomacy key, so a given order reloads both. */
export const orderSheetQueryKey = (villageId: number) => [...diplomacyQueryKey, 'orders', villageId] as const;

/** GET /api/diplomacy/orders/{villageId} (2.1 S3): the orders the village takes and the allies' reach. */
export function useOrderSheet(villageId: number, enabled = true) {
  return useQuery({
    queryKey: orderSheetQueryKey(villageId),
    queryFn: () => expectData(api.GET('/api/diplomacy/orders/{villageId}', { params: { path: { villageId } } })),
    enabled,
  });
}

/** POST /api/diplomacy/orders: each lord's new order replaces its old one; it answers by letter. */
export function useGiveOrder() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: OrderRequest) => expectData(api.POST('/api/diplomacy/orders', { body: request })),
    onSuccess: (sheet) => {
      queryClient.setQueryData(orderSheetQueryKey(sheet.target.id), sheet);
      return queryClient.invalidateQueries({ queryKey: diplomacyQueryKey, refetchType: 'active' });
    },
  });
}

/** DELETE /api/diplomacy/orders/{lordId}: takes the order back; answers with the diplomacy page. */
export function useCancelOrder() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (lordId: number) =>
      expectData(api.DELETE('/api/diplomacy/orders/{lordId}', { params: { path: { lordId } } })),
    onSuccess: (page) => {
      queryClient.setQueryData(diplomacyQueryKey, page);
      return queryClient.invalidateQueries({ queryKey: diplomacyQueryKey, refetchType: 'active' });
    },
    onError: () => queryClient.invalidateQueries({ queryKey: diplomacyQueryKey }),
  });
}

export type HelpRequest = components['schemas']['HelpRequestDto'];

/** The open help requests of the allies (2.1 S4); under the diplomacy key, so a trust change reloads them too. */
export const helpRequestsQueryKey = [...diplomacyQueryKey, 'requests'] as const;

/**
 * GET /api/diplomacy/requests (2.1 S4, 12-solo-and-allies §9): the allies' open requests, nearest
 * deadline first. HELP_REQUESTED and HELP_REQUESTS_CHANGED load it again.
 */
export function useHelpRequests(enabled = true) {
  return useQuery({
    queryKey: helpRequestsQueryKey,
    queryFn: () => expectData(api.GET('/api/diplomacy/requests')),
    enabled,
  });
}

/** POST /api/diplomacy/requests/{id}/decline: "Not this time"; answers with the requests still open. */
export function useDeclineRequest() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (requestId: number) =>
      expectData(api.POST('/api/diplomacy/requests/{requestId}/decline', { params: { path: { requestId } } })),
    onSuccess: (requests) => {
      queryClient.setQueryData(helpRequestsQueryKey, requests);
      return queryClient.invalidateQueries({ queryKey: diplomacyQueryKey, refetchType: 'active' });
    },
    onError: () => queryClient.invalidateQueries({ queryKey: helpRequestsQueryKey }),
  });
}
