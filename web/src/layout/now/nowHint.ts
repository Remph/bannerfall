/** The phone's one-time hint about the pull-down sheet (2.0); once seen, never again on this device. */
const STORAGE_KEY = 'bannerfall.nowHintSeen';

export function nowHintSeen(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) === '1';
  } catch {
    return true; // storage blocked: better no hint than one on every page
  }
}

export function markNowHintSeen(): void {
  try {
    localStorage.setItem(STORAGE_KEY, '1');
  } catch {
    // ignore
  }
}
