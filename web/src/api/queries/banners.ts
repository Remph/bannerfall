import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { api, expectData } from '../client';
import type { BannerType } from '../../game/gameData';
import type { components } from '../schema';
import { villageQueryKey } from './villages';

/** The player's banners and what the Banner Hall page needs (M32a, 02-game-rules §21). */
export type Banners = components['schemas']['BannersDto'];
export type OwnBanner = components['schemas']['BannerDto'];
export type BannerVillage = components['schemas']['BannerVillageDto'];

export const bannersQueryKey = ['banners'] as const;

/** GET /api/banners: the stash, the slots of all villages, the effect table. */
export function useBanners(enabled = true) {
  return useQuery({
    queryKey: bannersQueryKey,
    enabled,
    queryFn: () => expectData(api.GET('/api/banners')),
  });
}

/** A banner changes what the village makes and stores: the answer replaces the list, the village is read again. */
function applyBanners(queryClient: QueryClient, villageId: number, banners: Banners) {
  queryClient.setQueryData(bannersQueryKey, banners);
  void queryClient.invalidateQueries({ queryKey: villageQueryKey(villageId) });
}

/**
 * PUT /api/villages/{villageId}/banner/{bannerId}: put a banner of the stash into the slot (a swap if one
 * flies there); a paladin standard goes into the standard slot (1.4).
 */
export function useEquipBanner(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (bannerId: number) =>
      expectData(api.PUT('/api/villages/{villageId}/banner/{bannerId}', { params: { path: { villageId, bannerId } } })),
    onSuccess: (banners) => applyBanners(queryClient, villageId, banners),
    // also after an error: the screen was probably out of date (an attack came in, the stash changed)
    onError: () => void queryClient.invalidateQueries({ queryKey: bannersQueryKey }),
  });
}

/** DELETE /api/villages/{villageId}/banner: take the banner out of the slot into the stash. */
export function useUnequipBanner(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => expectData(api.DELETE('/api/villages/{villageId}/banner', { params: { path: { villageId } } })),
    onSuccess: (banners) => applyBanners(queryClient, villageId, banners),
    onError: () => void queryClient.invalidateQueries({ queryKey: bannersQueryKey }),
  });
}

/** DELETE /api/villages/{villageId}/standard (1.4): take the paladin standard out of its slot into the stash. */
export function useUnequipStandard(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => expectData(api.DELETE('/api/villages/{villageId}/standard', { params: { path: { villageId } } })),
    onSuccess: (banners) => applyBanners(queryClient, villageId, banners),
    onError: () => void queryClient.invalidateQueries({ queryKey: bannersQueryKey }),
  });
}

/** POST /api/banners/fuse (M32c): three stash banners of one tier become one of the next; the answer replaces the list. */
export function useFuseBanners() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { bannerIds: number[]; type: BannerType }) =>
      expectData(api.POST('/api/banners/fuse', { body })),
    onSuccess: (banners) => queryClient.setQueryData(bannersQueryKey, banners),
    onError: () => void queryClient.invalidateQueries({ queryKey: bannersQueryKey }),
  });
}
