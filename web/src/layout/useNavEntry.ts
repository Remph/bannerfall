import { useLocation } from 'react-router';
import { useCurrentAccount } from '../api/queries/auth';
import { useWorld } from '../api/queries/world';
import { navEntry, type NavEntry } from './sections';

/**
 * The navigation entry of the page that is open (1.5.1, `navEntry`): the bars, the More sheet and
 * the gear button light it.
 */
export function useNavEntry(): NavEntry | undefined {
  const { pathname } = useLocation();
  const me = useCurrentAccount();
  const bannersOn = useWorld().data?.bannersEnabled !== false;
  return navEntry(pathname, me.playerId, bannersOn);
}
