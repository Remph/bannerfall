import type { Language } from '../i18n/language';

/**
 * What the server reports as its version (GET /api/world, M28): the release name the Docker image
 * was built with ("1.1.0"). A local Gradle build is "0.0.1-SNAPSHOT" and a build without build info
 * "unknown"; those are no release, so the client shows "dev" / nothing and no changelog banner.
 */
export type ReleaseVersion = { kind: 'release'; version: string } | { kind: 'dev' } | { kind: 'unknown' };

export function parseVersion(raw: string | undefined | null): ReleaseVersion {
  const version = (raw ?? '').trim().replace(/^v(?=\d)/i, '');
  if (version === '' || version === 'unknown') {
    return { kind: 'unknown' };
  }
  if (/snapshot/i.test(version)) {
    return { kind: 'dev' };
  }
  return { kind: 'release', version };
}

/** The tag next to the server time: "v1.1.0", "dev", or nothing if the server doesn't know. */
export function versionLabel(raw: string | undefined | null): string | null {
  const parsed = parseVersion(raw);
  switch (parsed.kind) {
    case 'release':
      return `v${parsed.version}`;
    case 'dev':
      return 'dev';
    case 'unknown':
      return null;
  }
}

/**
 * The entry of this version on the changelog page of the landing page (bannerfall-hub: German at
 * /changelog/, English at /en/changelog/, one anchor "v1.1.0" per entry). Not configurable on
 * purpose: it is the project's own site, like the credits link.
 */
export function changelogUrl(version: string, language: Language): string {
  const path = language === 'de' ? '/changelog/' : '/en/changelog/';
  return `https://bannerfall.de${path}#v${version}`;
}

/**
 * The public repository with the game's source code (1.6), linked as "GitHub" next to the server
 * time and in the settings menu. Not configurable either: a host running a modified version
 * changes it here, so the link points at the code that actually runs.
 */
export const SOURCE_URL = 'https://github.com/Remph/bannerfall';

/** What a version in an API answer means for the page. */
export type VersionVerdict =
  /** Nothing new: the version the page was loaded with, or no version in the answer. */
  | { readonly kind: 'same' }
  /** A patch of the same 1.x: the old code still fits the new server, so the player may choose when to reload. */
  | { readonly kind: 'patch'; readonly version: string }
  /** Anything else (a new 1.x.0, development builds): the old code may not fit, so the page loads again at once. */
  | { readonly kind: 'reload' };

/** The release line of a version, "1.6" for "1.6.3". */
function releaseLine(version: string): string {
  return version.split('.').slice(0, 2).join('.');
}

/**
 * Watches the `X-Server-Version` header of the API answers. The first one is the version this page
 * was loaded with; a different one later means the host deployed an update while the page was
 * open. A patch (1.6.2 to 1.6.3, which never changes the API: no migration) is only announced
 * (1.6.3, the new-version strip with its reload button). Any other update would run the old code
 * against data it doesn't know (unit, building or report types) and can break it, so the page
 * has to load again at once. A missing header changes nothing.
 */
export class ServerVersionWatch {
  private first: string | null = null;

  /** Judges the version of one answer. */
  check(header: string | null): VersionVerdict {
    const version = header?.trim() ?? '';
    if (version === '') {
      return { kind: 'same' };
    }
    if (this.first === null) {
      this.first = version;
      return { kind: 'same' };
    }
    if (version === this.first) {
      return { kind: 'same' };
    }
    const before = parseVersion(this.first);
    const now = parseVersion(version);
    return before.kind === 'release' &&
      now.kind === 'release' &&
      releaseLine(before.version) === releaseLine(now.version)
      ? { kind: 'patch', version: now.version }
      : { kind: 'reload' };
  }
}
