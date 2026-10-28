import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { TemplateLetter } from '../../game/farmTemplates';
import type { RecruitTemplate } from '../../game/recruitTemplates';
import { toUnitMap } from '../../game/troops';
import { api, expectData } from '../client';

export const recruitTemplatesQueryKey = ['recruit-templates'] as const;

/** GET /api/recruit-templates (1.4): the player's templates, A to E as far as he has made them. */
export function useRecruitTemplates() {
  return useQuery({
    queryKey: recruitTemplatesQueryKey,
    queryFn: () => expectData(api.GET('/api/recruit-templates')),
    select: (templates): RecruitTemplate[] =>
      templates.map((template) => ({
        letter: template.letter,
        name: template.name,
        units: toUnitMap(template.units),
      })),
  });
}

/** PUT /api/recruit-templates/{letter}: makes the template or changes it. */
export function useSaveRecruitTemplate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ letter, name, units }: RecruitTemplate) =>
      expectData(
        api.PUT('/api/recruit-templates/{letter}', {
          params: { path: { letter } },
          body: { name, units: units as Record<string, number> },
        }),
      ),
    onSettled: () => queryClient.invalidateQueries({ queryKey: recruitTemplatesQueryKey }),
  });
}

/** DELETE /api/recruit-templates/{letter}. */
export function useDeleteRecruitTemplate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (letter: TemplateLetter) => {
      await api.DELETE('/api/recruit-templates/{letter}', { params: { path: { letter } } });
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: recruitTemplatesQueryKey }),
  });
}
