import { parseVersion } from '../game/version';

/**
 * The "new version, see the changelog" banner (M28) remembers the last version the player dismissed
 * (or opened the changelog for), per browser like the theme. A version change shows it again; a
 * dismissed one stays gone.
 */
const STORAGE_KEY = 'bannerfall.changelogSeen';

/** The version to announce, or null: development builds and servers without a version announce nothing. */
export function versionToAnnounce(raw: string | undefined | null, seen: string | null): string | null {
  const parsed = parseVersion(raw);
  return parsed.kind === 'release' && parsed.version !== seen ? parsed.version : null;
}

export function loadChangelogSeen(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null; // storage blocked (private mode): the banner shows again after a reload
  }
}

export function saveChangelogSeen(version: string): void {
  try {
    localStorage.setItem(STORAGE_KEY, version);
  } catch {
    // ignore: the banner stays hidden until the page is reloaded
  }
}

/**
 * A new account starts on the current version, so its very first screen doesn't announce what is
 * new to others. (A player who joined under 1.0.0 never stored anything and sees the banner once.)
 */
export function markVersionSeen(raw: string | undefined | null): void {
  const parsed = parseVersion(raw);
  if (parsed.kind === 'release') {
    saveChangelogSeen(parsed.version);
  }
}
