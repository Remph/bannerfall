import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, expectData } from '../client';
import type { components } from '../schema';

export type Tutorial = components['schemas']['TutorialDto'];
export type TutorialStepKey = components['schemas']['TutorialStepDto']['key'];
export type TutorialStepLine = components['schemas']['TutorialStepDto'];
export type TutorialCurrent = components['schemas']['TutorialCurrentDto'];

export const tutorialQueryKey = ['tutorial'] as const;

/**
 * POST /api/tutorial/sync (1.7): the player's tutorial. The server also records the steps the game
 * shows as done and pays their refunds, so this is a POST that the client uses like a read: it
 * runs on the village page and again whenever the village changes. Without a card the answer is
 * small (`active: false`).
 */
export function useTutorial() {
  return useQuery({
    queryKey: tutorialQueryKey,
    queryFn: () => expectData(api.POST('/api/tutorial/sync')),
    // the many screens that show a hint share one answer; the village's changes ask again (useTutorialSync)
    staleTime: 15_000,
  });
}

/** The answer of any tutorial call is the new tutorial: it goes straight into the cache. */
function useTutorialMutation<Variables>(call: (variables: Variables) => Promise<Tutorial>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: call,
    onSuccess: (tutorial) => queryClient.setQueryData(tutorialQueryKey, tutorial),
  });
}

/** POST /api/tutorial/steps/{key}/done: the player did what the server cannot see, or read a lesson. */
export function useTutorialDone() {
  return useTutorialMutation((key: TutorialStepKey) =>
    expectData(api.POST('/api/tutorial/steps/{key}/done', { params: { path: { key } } })),
  );
}

/** POST /api/tutorial/steps/{key}/skip. */
export function useTutorialSkip() {
  return useTutorialMutation((key: TutorialStepKey) =>
    expectData(api.POST('/api/tutorial/steps/{key}/skip', { params: { path: { key } } })),
  );
}

/** POST /api/tutorial/start and /stop: the menu row. */
export function useTutorialSwitch() {
  return useTutorialMutation((on: boolean) =>
    expectData(on ? api.POST('/api/tutorial/start') : api.POST('/api/tutorial/stop')),
  );
}
