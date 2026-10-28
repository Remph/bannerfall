import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import type { FarmTemplate, TemplateLetter } from '../../game/farmTemplates';
import { toUnitMap, type UnitMap } from '../../game/troops';
import { api, expectData } from '../client';
import { farmListsQueryKey } from './farm';

export const farmTemplatesQueryKey = ['farm-templates'] as const;

/** GET /api/farm-templates (1.4): the player's templates, A to E as far as he has made them. */
export function useFarmTemplates() {
  return useQuery({
    queryKey: farmTemplatesQueryKey,
    queryFn: () => expectData(api.GET('/api/farm-templates')),
    select: (templates): FarmTemplate[] =>
      templates.map((template) => ({ letter: template.letter, units: toUnitMap(template.units) })),
  });
}

/** A template changes the entries that use it, in every village's list. */
function refreshAfterChange(queryClient: QueryClient): Promise<unknown> {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: farmTemplatesQueryKey }),
    queryClient.invalidateQueries({ queryKey: farmListsQueryKey }),
  ]);
}

/** PUT /api/farm-templates/{letter}: makes the template or changes it. */
export function useSaveFarmTemplate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ letter, units }: { letter: TemplateLetter; units: UnitMap }) =>
      expectData(
        api.PUT('/api/farm-templates/{letter}', {
          params: { path: { letter } },
          body: { units: units as Record<string, number> },
        }),
      ),
    onSettled: () => refreshAfterChange(queryClient),
  });
}

/** DELETE /api/farm-templates/{letter}: its entries keep their units as their own. */
export function useDeleteFarmTemplate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (letter: TemplateLetter) => {
      await api.DELETE('/api/farm-templates/{letter}', { params: { path: { letter } } });
    },
    onSettled: () => refreshAfterChange(queryClient),
  });
}
