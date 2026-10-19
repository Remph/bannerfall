import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { PushKind } from '../../push/pushMessage';
import { api, expectData } from '../client';
import type { components } from '../schema';

export type PushSettings = components['schemas']['PushSettingsDto'];

export const pushQueryKey = ['push'] as const;

/** GET /api/push (1.5): whether the world sends push, its key, the player's devices and switches. */
export function usePushSettings() {
  return useQuery({ queryKey: pushQueryKey, queryFn: () => expectData(api.GET('/api/push')) });
}

/** PUT /api/push/preferences: one switch. The answer is the whole page's state. */
export function useSetPushKind() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ kind, enabled }: { kind: PushKind; enabled: boolean }) =>
      expectData(api.PUT('/api/push/preferences', { body: { kinds: [{ kind, enabled }] } })),
    onSuccess: (settings) => queryClient.setQueryData(pushQueryKey, settings),
  });
}

/** POST /api/push/test: a test to every device of the player; the answer says to how many. */
export function useSendPushTest() {
  return useMutation({ mutationFn: () => expectData(api.POST('/api/push/test')) });
}
