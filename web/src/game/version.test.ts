import { describe, expect, it } from 'vitest';
import { changelogUrl, parseVersion, ServerVersionWatch, versionLabel } from './version';

describe('parseVersion', () => {
  it('takes a release as it is and drops a leading v', () => {
    expect(parseVersion('1.1.0')).toEqual({ kind: 'release', version: '1.1.0' });
    expect(parseVersion('v1.0.1')).toEqual({ kind: 'release', version: '1.0.1' });
    expect(parseVersion('1.2.0-rc1')).toEqual({ kind: 'release', version: '1.2.0-rc1' });
  });

  it('knows a local Gradle build is no release', () => {
    expect(parseVersion('0.0.1-SNAPSHOT')).toEqual({ kind: 'dev' });
  });

  it('treats a missing version as unknown', () => {
    expect(parseVersion('unknown')).toEqual({ kind: 'unknown' });
    expect(parseVersion('')).toEqual({ kind: 'unknown' });
    expect(parseVersion(undefined)).toEqual({ kind: 'unknown' });
  });
});

describe('versionLabel', () => {
  it('writes releases with a v, development builds as dev and unknown as nothing', () => {
    expect(versionLabel('1.1.0')).toBe('v1.1.0');
    expect(versionLabel('v1.1.0')).toBe('v1.1.0');
    expect(versionLabel('0.0.1-SNAPSHOT')).toBe('dev');
    expect(versionLabel('unknown')).toBeNull();
    expect(versionLabel(undefined)).toBeNull();
  });
});

describe('changelogUrl', () => {
  it('links the entry of the version on the page in the player language', () => {
    expect(changelogUrl('1.1.0', 'de')).toBe('https://bannerfall.de/changelog/#v1.1.0');
    expect(changelogUrl('1.1.0', 'en')).toBe('https://bannerfall.de/en/changelog/#v1.1.0');
  });
});

describe('ServerVersionWatch', () => {
  it('takes the first version as the one the page was loaded with', () => {
    const watch = new ServerVersionWatch();
    expect(watch.changed('1.2.0')).toBe(false);
    expect(watch.changed('1.2.0')).toBe(false);
  });

  it('notices an update deployed while the page is open', () => {
    const watch = new ServerVersionWatch();
    watch.changed('1.1.2');
    expect(watch.changed('1.2.0')).toBe(true);
    // and keeps saying so until the page loads again
    expect(watch.changed('1.2.0')).toBe(true);
  });

  it('ignores answers without the header', () => {
    const watch = new ServerVersionWatch();
    expect(watch.changed(null)).toBe(false);
    expect(watch.changed('')).toBe(false);
    expect(watch.changed('1.2.0')).toBe(false);
    expect(watch.changed(null)).toBe(false);
    expect(watch.changed('1.2.0')).toBe(false);
  });
});
