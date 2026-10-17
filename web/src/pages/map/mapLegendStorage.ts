/** Whether the legend in the desktop side column is open, stored per device (1.3). Folded by default. */
const STORAGE_KEY = 'bannerfall.mapLegendOpen';

export function loadLegendOpen(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) === '1';
  } catch {
    return false; // storage blocked (private mode)
  }
}

export function saveLegendOpen(open: boolean): void {
  try {
    if (open) {
      localStorage.setItem(STORAGE_KEY, '1');
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // ignore: the choice still holds until the page is reloaded
  }
}
