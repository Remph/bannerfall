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
 * Watches the `X-Server-Version` header of the API answers. The first one is the version this page
 * was loaded with; a different one later means the host deployed an update while the page was
 * open. The old code would then run against the new server's data (unit, building or report types
 * it doesn't know) and can break, so the page has to load again. A missing header changes nothing.
 */
export class ServerVersionWatch {
  private first: string | null = null;

  /** Records the version of one answer; true if it differs from the first one. */
  changed(header: string | null): boolean {
    const version = header?.trim() ?? '';
    if (version === '') {
      return false;
    }
    if (this.first === null) {
      this.first = version;
      return false;
    }
    return version !== this.first;
  }
}
