/**
 * Which village the game screens show. The village is part of the URL (/v/:villageId/…), so links
 * and reloads keep it (05-frontend §3). Screens without a village in the URL (/, /reports)
 * use the last village the player looked at in this browser session, or else their first one, the
 * main village. The village is remembered in sessionStorage (1.4.1; localStorage before), so the
 * game opens on the main village again after it was closed instead of on the village looked at last.
 */

const STORAGE_KEY = 'bannerfall.village';
const VILLAGE_PATH = /^\/v\/\d+/;

/** "12" → 12; anything that isn't a plain positive number → undefined. */
export function parseVillageId(param: string | undefined): number | undefined {
  return param !== undefined && /^\d{1,15}$/.test(param) ? Number(param) : undefined;
}

/**
 * The village to show: the one in the URL (even if it isn't ours – then the server answers
 * VILLAGE_NOT_FOUND and the page says so), else the remembered one if still ours, else the first.
 */
export function resolveVillageId(
  urlParam: string | undefined,
  remembered: number | null,
  ownVillageIds: readonly number[],
): number | undefined {
  const fromUrl = parseVillageId(urlParam);
  if (fromUrl !== undefined) {
    return fromUrl;
  }
  if (remembered !== null && ownVillageIds.includes(remembered)) {
    return remembered;
  }
  return ownVillageIds[0];
}

/** The same screen for another village: /v/1/hq → /v/2/hq. From other screens: that village's overview. */
export function villagePath(currentPathname: string, villageId: number): string {
  return VILLAGE_PATH.test(currentPathname)
    ? currentPathname.replace(VILLAGE_PATH, `/v/${villageId}`)
    : `/v/${villageId}`;
}

export function loadRememberedVillage(): number | null {
  try {
    return parseVillageId(sessionStorage.getItem(STORAGE_KEY) ?? undefined) ?? null;
  } catch {
    return null;
  }
}

export function rememberVillage(villageId: number): void {
  try {
    sessionStorage.setItem(STORAGE_KEY, String(villageId));
  } catch {
    // ignore: then "/" just opens the first village
  }
  try {
    localStorage.removeItem(STORAGE_KEY); // where 1.4 and earlier kept it
  } catch {
    // ignore
  }
}
