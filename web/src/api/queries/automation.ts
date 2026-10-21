import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { fromApiLines, toApiLines, type BuildPlan, type PlanBuilding } from '../../game/buildPlans';
import type { TemplateLetter } from '../../game/farmTemplates';
import { api, expectData } from '../client';
import type { components } from '../schema';
import { villageQueryKey } from './villages';

/** GET …/automation: what the village's automation does now (02-game-rules §25, 1.6). */
export type VillageAutomation = components['schemas']['VillageAutomationDto'];
export type AutomationState = VillageAutomation['state'];

export const buildPlansQueryKey = ['build-plans'] as const;
const planBuildingsQueryKey = ['plan-buildings'] as const;
/** The prefix of every village's automation status. */
export const automationPrefix = ['automation'] as const;

export function automationQueryKey(villageId: number) {
  return [...automationPrefix, villageId] as const;
}

/** GET /api/build-plans: the player's plans, A to E as far as they have made them. */
export function useBuildPlans() {
  return useQuery({
    queryKey: buildPlansQueryKey,
    queryFn: () => expectData(api.GET('/api/build-plans')),
    select: (plans): BuildPlan[] =>
      plans.map((plan) => ({ letter: plan.letter, name: plan.name, lines: fromApiLines(plan.lines) })),
  });
}

/** GET /api/build-plans/buildings: start and top levels and requirements (fixed for the world). */
export function usePlanBuildings() {
  return useQuery({
    queryKey: planBuildingsQueryKey,
    queryFn: (): Promise<PlanBuilding[]> => expectData(api.GET('/api/build-plans/buildings')),
    staleTime: Infinity,
  });
}

/** PUT /api/build-plans/{letter}: makes the plan or changes it. Villages working through it look again. */
export function useSaveBuildPlan() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ letter, name, lines }: BuildPlan) =>
      expectData(
        api.PUT('/api/build-plans/{letter}', {
          params: { path: { letter } },
          body: { name, lines: toApiLines(lines) },
        }),
      ),
    onSettled: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: buildPlansQueryKey }),
        queryClient.invalidateQueries({ queryKey: automationPrefix }),
      ]),
  });
}

/** DELETE /api/build-plans/{letter}: villages working through it stop. */
export function useDeleteBuildPlan() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (letter: TemplateLetter) => {
      await api.DELETE('/api/build-plans/{letter}', { params: { path: { letter } } });
    },
    onSettled: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: buildPlansQueryKey }),
        queryClient.invalidateQueries({ queryKey: automationPrefix }),
      ]),
  });
}

/** GET /api/villages/{id}/automation. */
export function useVillageAutomation(villageId: number) {
  return useQuery({
    queryKey: automationQueryKey(villageId),
    queryFn: () => expectData(api.GET('/api/villages/{villageId}/automation', { params: { path: { villageId } } })),
  });
}

/**
 * PUT /api/villages/{id}/automation: the plan and the switch (plan null drops the automation).
 * Switched on, the village looks right away; the orders it places arrive as a village update.
 */
export function useSetVillageAutomation(villageId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { plan: TemplateLetter | null; enabled: boolean }) =>
      expectData(api.PUT('/api/villages/{villageId}/automation', { params: { path: { villageId } }, body })),
    onSuccess: (status) => queryClient.setQueryData(automationQueryKey(villageId), status),
    onSettled: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: automationQueryKey(villageId) }),
        queryClient.invalidateQueries({ queryKey: villageQueryKey(villageId) }),
      ]),
  });
}
