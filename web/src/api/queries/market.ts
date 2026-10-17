import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { refetchDelayMs } from '../../game/buildQueue';
import { nextMarketChangeMs } from '../../game/market';
import { serverNow } from '../../game/serverTime';
import { api, expectData } from '../client';
import type { components } from '../schema';
import { refreshReports } from './reports';
import { villageQueryKey } from './villages';

export type Market = components['schemas']['MarketDto'];
export type Transport = components['schemas']['TransportDto'];
export type Offer = components['schemas']['OfferDto'];
export type OfferRequest = components['schemas']['OfferRequest'];
export type TransportRequest = components['schemas']['TransportRequest'];
export type Exchange = components['schemas']['ExchangeDto'];
export type ExchangeRequest = components['schemas']['ExchangeRequest'];

export function marketQueryKey(villageId: number) {
  return ['market', villageId] as const;
}

/** Prefix of all offer lists: the offers of other players are the same for every village. */
export const offersQueryKey = ['offers'] as const;

export function villageOffersQueryKey(villageId: number) {
  return [...offersQueryKey, villageId] as const;
}

/** GET /api/villages/{id}/market: merchants, transports on the way and the village's own offers. */
export function useMarket(villageId: number) {
  return useQuery({
    queryKey: marketQueryKey(villageId),
    queryFn: () => expectData(api.GET('/api/villages/{villageId}/market', { params: { path: { villageId } } })),
  });
}

/** GET /api/offers?fromVillageId=: the open offers of other players, nearest first, from this village. */
export function useOffers(villageId: number) {
  return useQuery({
    queryKey: villageOffersQueryKey(villageId),
    queryFn: () => expectData(api.GET('/api/offers', { params: { query: { fromVillageId: villageId } } })),
  });
}

/**
 * Sending, offering, cancelling and accepting change the merchants, the offers and the stock (the
 * village). The promise keeps a mutation "pending" until the fresh data is there. The offers of
 * every village are outdated too (somebody else's offer is gone or smaller), hence the prefixes.
 */
function refreshMarket(queryClient: QueryClient, villageId: number): Promise<unknown> {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: marketQueryKey(villageId) }),
    queryClient.invalidateQueries({ queryKey: offersQueryKey }),
    queryClient.invalidateQueries({ queryKey: villageQueryKey(villageId) }),
  ]);
}

/** POST …/transports: resources leave now and arrive after the merchants' travel time. */
export function useSendResources(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: TransportRequest) =>
      expectData(api.POST('/api/villages/{villageId}/transports', { params: { path: { villageId } }, body: request })),
    // also after an error: the screen was probably out of date
    onSettled: () => refreshMarket(queryClient, villageId),
  });
}

/** POST …/offers: the offered resources are paid and the merchants reserved at once. */
export function useCreateOffer(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: OfferRequest) =>
      expectData(api.POST('/api/villages/{villageId}/offers', { params: { path: { villageId } }, body: request })),
    onSettled: () => refreshMarket(queryClient, villageId),
  });
}

/** POST …/market/exchange: trading with the market itself, paid and received at once. */
export function useExchange(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: ExchangeRequest) =>
      expectData(
        api.POST('/api/villages/{villageId}/market/exchange', { params: { path: { villageId } }, body: request }),
      ),
    onSettled: () => refreshMarket(queryClient, villageId),
  });
}

/** DELETE /api/offers/{id}: what is left of the offer comes back (as far as the warehouse has room). */
export function useCancelOffer(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (offerId: number) => {
      // failures are thrown by the client's middleware (api/client.ts)
      await api.DELETE('/api/offers/{offerId}', { params: { path: { offerId } } });
    },
    onSettled: () => refreshMarket(queryClient, villageId),
  });
}

/** POST /api/offers/{id}/accept: `count` units, paid from the village at once; two transports start. */
export function useAcceptOffer(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ offerId, count }: { offerId: number; count: number }) =>
      expectData(
        api.POST('/api/offers/{offerId}/accept', { params: { path: { offerId } }, body: { villageId, count } }),
      ),
    onSettled: () => refreshMarket(queryClient, villageId),
  });
}

/**
 * Reloads the market (and the village's stock) shortly after the next transport arrives or comes
 * home, or a new day of trading with the market starts (v1.3), so deliveries, free merchants and the
 * day's amounts show up by themselves while the screen is open. Works like
 * the rally point's refresh: one timer, set anew by every answer. Arrivals add a report.
 */
export function useRefreshWhenTransportsChange(
  villageId: number,
  market: Market | undefined,
  dataUpdatedAt: number,
  errorUpdatedAt: number,
) {
  const queryClient = useQueryClient();
  const next =
    market === undefined
      ? undefined
      : nextMarketChangeMs(
          [...market.outgoing, ...market.incoming],
          market.exchange.ratio > 0 ? market.exchange.resetsAt : undefined,
        );

  useEffect(() => {
    if (next === undefined) {
      return;
    }
    const timer = setTimeout(
      () => {
        void refreshMarket(queryClient, villageId);
        void refreshReports(queryClient);
      },
      refetchDelayMs(next, serverNow()),
    );
    return () => clearTimeout(timer);
  }, [queryClient, villageId, next, dataUpdatedAt, errorUpdatedAt]);
}
