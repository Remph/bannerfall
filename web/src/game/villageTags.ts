import type { VillageSummary } from '../api/queries/villages';

/**
 * The player's own village tags (1.7, 02-game-rules §26): 1 to 5 capital letters or digits, private.
 * The server decides; the web app shapes the input the same way before it sends it.
 */
export const VILLAGE_TAG_MAX_LENGTH = 5;

/** What the field keeps while typing: capitals and digits only (the server accepts A-Z and 0-9), at most 5. */
export function cleanVillageTag(input: string): string {
  return input
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '')
    .slice(0, VILLAGE_TAG_MAX_LENGTH);
}

/** The tags the villages use, each once, in alphabetical order. */
export function usedVillageTags(villages: readonly VillageSummary[]): string[] {
  const tags = new Set<string>();
  for (const village of villages) {
    if (village.tag !== null) {
      tags.add(village.tag);
    }
  }
  return [...tags].sort((a, b) => a.localeCompare(b));
}

/** The villages with this tag; `null` (the "All" chip) keeps them all. */
export function villagesWithTag(villages: readonly VillageSummary[], tag: string | null): VillageSummary[] {
  return tag === null ? [...villages] : villages.filter((village) => village.tag === tag);
}

/** The tag of one of the player's villages, by id; null for no tag or a village that isn't his. */
export function tagOfVillage(villages: readonly VillageSummary[], villageId: number): string | null {
  return villages.find((village) => village.id === villageId)?.tag ?? null;
}

const FILTER_KEY = 'bannerfall.villageTagFilter';

/** The chip chosen in the village switcher, for this browser session (sessionStorage, like the village). */
export function loadVillageTagFilter(): string | null {
  try {
    return sessionStorage.getItem(FILTER_KEY);
  } catch {
    return null;
  }
}

export function saveVillageTagFilter(tag: string | null): void {
  try {
    if (tag === null) {
      sessionStorage.removeItem(FILTER_KEY);
    } else {
      sessionStorage.setItem(FILTER_KEY, tag);
    }
  } catch {
    // ignore: the switcher then opens on "All"
  }
}
