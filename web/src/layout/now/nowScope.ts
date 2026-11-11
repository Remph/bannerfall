/**
 * Which villages the Now list covers: the one the player looks at, or all of them. Stored per
 * device, like the village view.
 */
import type { NowScope } from '../../game/now';

const STORAGE_KEY = 'bannerfall.nowScope';

/** Only the stored 'all' means all villages; nothing stored or anything else is this village. */
export function parseNowScope(value: string | null): NowScope {
  return value === 'all' ? 'all' : 'this';
}

export function loadNowScope(): NowScope {
  try {
    return parseNowScope(localStorage.getItem(STORAGE_KEY));
  } catch {
    return 'this'; // storage blocked (private mode)
  }
}

export function saveNowScope(scope: NowScope): void {
  try {
    if (scope === 'all') {
      localStorage.setItem(STORAGE_KEY, 'all');
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // ignore: the choice still holds until the page is reloaded
  }
}
