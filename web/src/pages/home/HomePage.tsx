import { Navigate } from 'react-router';
import { useCurrentAccount } from '../../api/queries/auth';
import { useWorld } from '../../api/queries/world';
import { useT } from '../../i18n';
import { loadRememberedVillage, resolveVillageId } from '../../layout/currentVillage';
import { PregamePage } from '../pregame/PregamePage';
import { RespawnPage } from '../respawn/RespawnPage';

/**
 * "/" opens the last village the player looked at, or their first one. A player who lost his last
 * village (M11) gets the respawn screen here.
 */
export function HomePage() {
  const t = useT();
  const me = useCurrentAccount();
  const world = useWorld();
  // before the world opens nobody has a village: the countdown and the settings instead (M14g)
  if (world.data?.phase === 'PREGAME') {
    return <PregamePage />;
  }
  if (me.needsRespawn) {
    return <RespawnPage />;
  }
  const villageId = resolveVillageId(
    undefined,
    loadRememberedVillage(),
    me.villages.map((village) => village.id),
  );
  if (villageId === undefined) {
    // Only for a moment: a player without villages has needsRespawn (an NPC lord can't log in).
    return <p>{t.home.noVillage}</p>;
  }
  return <Navigate to={`/v/${villageId}`} replace />;
}
