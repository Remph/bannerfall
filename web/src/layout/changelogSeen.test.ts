import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { loadChangelogSeen, markVersionSeen, saveChangelogSeen, versionToAnnounce } from './changelogSeen';

describe('versionToAnnounce', () => {
  it('announces a release the player has not dismissed yet', () => {
    expect(versionToAnnounce('1.1.0', null)).toBe('1.1.0');
    expect(versionToAnnounce('1.1.0', '1.0.0')).toBe('1.1.0');
  });

  it('stays quiet for the version already dismissed', () => {
    expect(versionToAnnounce('1.1.0', '1.1.0')).toBeNull();
    expect(versionToAnnounce('v1.1.0', '1.1.0')).toBeNull();
  });

  it('stays quiet for development builds and while the version is not known', () => {
    expect(versionToAnnounce('0.0.1-SNAPSHOT', null)).toBeNull();
    expect(versionToAnnounce('unknown', null)).toBeNull();
    expect(versionToAnnounce(undefined, null)).toBeNull();
  });
});

describe('the stored version', () => {
  // the tests run in node: a tiny stand-in for the browser's storage
  beforeEach(() => {
    const store = new Map<string, string>();
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => store.get(key) ?? null,
      setItem: (key: string, value: string) => store.set(key, value),
    });
  });
  afterEach(() => vi.unstubAllGlobals());

  it('is remembered', () => {
    expect(loadChangelogSeen()).toBeNull();
    saveChangelogSeen('1.1.0');
    expect(loadChangelogSeen()).toBe('1.1.0');
  });

  it('is set for a new account on its release, but not on a development build', () => {
    markVersionSeen('0.0.1-SNAPSHOT');
    expect(loadChangelogSeen()).toBeNull();
    markVersionSeen('v1.1.0');
    expect(loadChangelogSeen()).toBe('1.1.0');
  });
});
