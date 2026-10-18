/**
 * Whether the recruit screen's template row is folded to its one-line toggle (1.4). Open by default;
 * stored per device, like the resource strip's details.
 */
const STORAGE_KEY = 'bannerfall.recruitTemplatesFolded';

/** Only the stored 'folded' folds the row; nothing stored or anything else keeps it open. */
export function parseTemplatesFolded(value: string | null): boolean {
  return value === 'folded';
}

export function loadTemplatesFolded(): boolean {
  try {
    return parseTemplatesFolded(localStorage.getItem(STORAGE_KEY));
  } catch {
    return false; // storage blocked (private mode)
  }
}

export function saveTemplatesFolded(folded: boolean): void {
  try {
    if (folded) {
      localStorage.setItem(STORAGE_KEY, 'folded');
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // ignore: the choice still holds until the page is reloaded
  }
}
