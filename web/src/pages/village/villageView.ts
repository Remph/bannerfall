/**
 * How the Buildings card on the village page shows the buildings: as the village picture (the
 * default, 1.3) or as the list. Stored per device, like the theme.
 */
const STORAGE_KEY = 'bannerfall.villageView';

export type VillageView = 'picture' | 'list';

/** Only the stored 'list' means the list; nothing stored or anything else is the picture. */
export function parseVillageView(value: string | null): VillageView {
  return value === 'list' ? 'list' : 'picture';
}

export function loadVillageView(): VillageView {
  try {
    return parseVillageView(localStorage.getItem(STORAGE_KEY));
  } catch {
    return 'picture'; // storage blocked (private mode)
  }
}

export function saveVillageView(view: VillageView): void {
  try {
    if (view === 'list') {
      localStorage.setItem(STORAGE_KEY, 'list');
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // ignore: the choice still holds until the page is reloaded
  }
}
