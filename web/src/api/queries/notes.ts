import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api, expectData } from '../client';
import { farmListsQueryKey } from './farm';
import { mapQueryKey, type MapVillage } from './map';

/**
 * PUT /api/villages/{id}/note (1.4): the player's own note on a village; a blank text deletes it.
 * The map chunks holding the village get the new note at once, the farm lists reload (their rows
 * show it too).
 */
export function useSaveVillageNote() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ villageId, text }: { villageId: number; text: string }) =>
      expectData(api.PUT('/api/villages/{id}/note', { params: { path: { id: villageId } }, body: { text } })),
    onSuccess: (saved) => {
      queryClient.setQueriesData<MapVillage[]>({ queryKey: mapQueryKey }, (villages) =>
        villages?.some((village) => village.id === saved.villageId)
          ? villages.map((village) => (village.id === saved.villageId ? { ...village, note: saved.text } : village))
          : villages,
      );
      void queryClient.invalidateQueries({ queryKey: farmListsQueryKey });
    },
  });
}
