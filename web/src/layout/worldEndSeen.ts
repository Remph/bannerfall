/**
 * The end screen (v1.3) shows once per end of the world and browser: it remembers the end it was
 * closed for (the result's `endedAt`). A world that is reopened and ends again shows it again.
 */
const STORAGE_KEY = 'bannerfall.worldEndSeen';

/** Whether the screen is due for the end at `endedAt`. */
export function endScreenDue(endedAt: string, seen: string | null): boolean {
  return endedAt !== seen;
}

export function loadWorldEndSeen(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null; // storage blocked (private mode): the screen shows again after a reload
  }
}

export function saveWorldEndSeen(endedAt: string): void {
  try {
    localStorage.setItem(STORAGE_KEY, endedAt);
  } catch {
    // ignore: the screen stays closed until the page is reloaded
  }
}
