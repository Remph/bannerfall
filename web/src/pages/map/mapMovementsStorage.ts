/**
 * Whether the map shows the movement markers (M26), stored per device. On by default: only the
 * choice to hide them is stored.
 */
const STORAGE_KEY = 'bannerfall.mapMovements';

export function loadShowMovements(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) !== 'off';
  } catch {
    return true; // storage blocked (private mode)
  }
}

export function saveShowMovements(show: boolean): void {
  try {
    if (show) {
      localStorage.removeItem(STORAGE_KEY);
    } else {
      localStorage.setItem(STORAGE_KEY, 'off');
    }
  } catch {
    // ignore: the choice still holds until the page is reloaded
  }
}
