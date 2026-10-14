/**
 * Phones: whether the resource strip shows its details (maximum, production per hour) or only the
 * amounts in one slim row (the default, so the header doesn't take a third of the screen). Stored
 * per device, like the theme.
 */
const STORAGE_KEY = 'bannerfall.resourceDetails';

/** Only the stored 'shown' means details; nothing stored or anything else is the slim row. */
export function parseResourceDetails(value: string | null): boolean {
  return value === 'shown';
}

export function loadResourceDetails(): boolean {
  try {
    return parseResourceDetails(localStorage.getItem(STORAGE_KEY));
  } catch {
    return false; // storage blocked (private mode)
  }
}

export function saveResourceDetails(shown: boolean): void {
  try {
    if (shown) {
      localStorage.setItem(STORAGE_KEY, 'shown');
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // ignore: the choice still holds until the page is reloaded
  }
}
