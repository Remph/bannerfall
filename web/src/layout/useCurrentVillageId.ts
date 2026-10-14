import { useMatch } from 'react-router';
import { useCurrentAccount } from '../api/queries/auth';
import { loadRememberedVillage, resolveVillageId } from './currentVillage';

/**
 * The village the game screens show: the one in the URL (/v/:villageId/…), else the remembered
 * one, else the player's first (05-frontend §3). Screens without a village in the URL (/reports)
 * get the same one the header shows. Only inside <RequireLogin>.
 */
export function useCurrentVillageId(): number | undefined {
  const me = useCurrentAccount();
  // The layout sits above the village routes, so it reads the village from the URL with a pattern.
  const match = useMatch('/v/:villageId/*');
  return resolveVillageId(
    match?.params.villageId,
    loadRememberedVillage(),
    me.villages.map((village) => village.id),
  );
}
