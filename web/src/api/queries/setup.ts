import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api, expectData } from '../client';
import { worldSettingsKey } from './admin';
import { meQueryKey } from './auth';

/**
 * POST /api/me/setup-claim (1.6, guided world setup): the code from `bannerfall-admin setup` makes
 * the logged-in account an admin. 400 SETUP_CODE_INVALID, 409 WORLD_ALREADY_OPEN.
 */
export function useClaimSetup() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (code: string) => {
      await api.POST('/api/me/setup-claim', { body: { code } });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: meQueryKey }),
  });
}

/** POST /api/admin/world-setup/done: the host finished (or skipped) the wizard; the pre-game page stops offering it. */
export function useSetupDone() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (finished: boolean) => expectData(api.POST('/api/admin/world-setup/done', { body: { finished } })),
    onSuccess: (settings) => queryClient.setQueryData(worldSettingsKey, settings),
  });
}
