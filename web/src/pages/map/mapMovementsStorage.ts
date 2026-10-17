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

/**
 * Whether the map draws the movement lines (v1.3), stored per device. Off by default: only the
 * choice to show them is stored.
 */
const LINES_STORAGE_KEY = 'bannerfall.mapLines';

export function loadShowLines(): boolean {
  try {
    return localStorage.getItem(LINES_STORAGE_KEY) === 'on';
  } catch {
    return false; // storage blocked (private mode)
  }
}

export function saveShowLines(show: boolean): void {
  try {
    if (show) {
      localStorage.setItem(LINES_STORAGE_KEY, 'on');
    } else {
      localStorage.removeItem(LINES_STORAGE_KEY);
    }
  } catch {
    // ignore: the choice still holds until the page is reloaded
  }
}
