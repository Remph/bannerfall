import { useMutation, useQueryClient } from '@tanstack/react-query';
import { unitTypes, type UnitType } from '../../game/gameData';
import { api, expectData } from '../client';
import { buildingsQueryKey } from './buildings';
import { mapMovementsQueryKey, mapQueryKey } from './map';
import { marketQueryKey, offersQueryKey } from './market';
import { recruitmentQueryKey } from './recruitment';
import { refreshReports } from './reports';
import { troopsQueryKey } from './troops';
import { villageQueryKey } from './villages';

/*
 * Dev cheats (04-api, "Dev cheats"): only the server's `dev` profile has these endpoints, and the UI
 * only shows them in `pnpm dev` (import.meta.env.DEV). The village cheats answer with the village afterwards.
 */

/** POST /api/dev/villages/{id}/resources: adds the amounts, ignoring the warehouse capacity. */
export function useDevAddResources(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (amount: number) =>
      expectData(
        api.POST('/api/dev/villages/{villageId}/resources', {
          params: { path: { villageId } },
          body: { wood: amount, clay: amount, iron: amount },
        }),
      ),
    onSuccess: (village) => {
      queryClient.setQueryData(villageQueryKey(villageId), village);
    },
  });
}

/**
 * POST /api/dev/villages/{id}/units: adds `amount` of every unit to the home stack, ignoring the
 * farm (the free population can go below zero).
 */
export function useDevAddUnits(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (amount: number) => {
      // { SPEARMAN: 100, SWORDSMAN: 100, … }: Object.fromEntries builds an object from [key, value] pairs
      const units: Partial<Record<UnitType, number>> = Object.fromEntries(unitTypes.map((unit) => [unit, amount]));
      return expectData(
        api.POST('/api/dev/villages/{villageId}/units', { params: { path: { villageId } }, body: { units } }),
      );
    },
    onSuccess: (village) => {
      queryClient.setQueryData(villageQueryKey(villageId), village);
      return queryClient.invalidateQueries({ queryKey: recruitmentQueryKey(villageId) });
    },
  });
}

/** POST /api/dev/villages/{id}/finish-orders: completes all build and recruit orders right now. */
export function useDevFinishOrders(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      expectData(api.POST('/api/dev/villages/{villageId}/finish-orders', { params: { path: { villageId } } })),
    onSuccess: (village) => {
      queryClient.setQueryData(villageQueryKey(villageId), village);
      return Promise.all([
        queryClient.invalidateQueries({ queryKey: buildingsQueryKey(villageId) }),
        queryClient.invalidateQueries({ queryKey: recruitmentQueryKey(villageId) }),
      ]);
    },
  });
}

/**
 * POST /api/dev/villages/{id}/finish-movements: every movement of the village's own units arrives
 * now, and so do the merchants it sent. An attack fights on arrival (reports for both sides) and its
 * survivors walk home as a new return, so a second call brings them home.
 */
export function useDevFinishMovements(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      expectData(api.POST('/api/dev/villages/{villageId}/finish-movements', { params: { path: { villageId } } })),
    onSuccess: (village) => {
      queryClient.setQueryData(villageQueryKey(villageId), village);
      return Promise.all([
        queryClient.invalidateQueries({ queryKey: troopsQueryKey(villageId) }),
        // the merchants it sent arrive too (M13b)
        queryClient.invalidateQueries({ queryKey: marketQueryKey(villageId) }),
        queryClient.invalidateQueries({ queryKey: offersQueryKey }),
        queryClient.invalidateQueries({ queryKey: mapMovementsQueryKey }),
        refreshReports(queryClient),
      ]);
    },
  });
}

/**
 * POST /api/dev/barbarians: a barbarian village on exactly this field (it must be free and on the
 * map, else FIELD_NOT_FREE). The map is loaded again, so the new village shows up.
 */
export function useDevSpawnBarbarian() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (field: { x: number; y: number }) => expectData(api.POST('/api/dev/barbarians', { body: field })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: mapQueryKey }),
  });
}
