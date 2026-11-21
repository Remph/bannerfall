import { useContext } from 'react';
import { useI18n } from '../i18n';
import { WorldEndContext } from './worldEnd';

/**
 * Why the game's actions are off, or null while the world runs: it has ended, or the host paused it
 * (2.1). The host reads that he presses play himself (UI review 2.1, finding 04).
 */
export function useFrozenReason(): string | null {
  const { t } = useI18n();
  const world = useContext(WorldEndContext);
  if (world.ended) {
    return t.worldEnd.frozen;
  }
  if (world.paused === true) {
    return world.host === true ? t.pausedBanner.frozenHost : t.pausedBanner.frozen;
  }
  return null;
}
