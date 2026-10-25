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
    expect(watch.check('1.2.0')).toEqual({ kind: 'same' });
    expect(watch.check('1.2.0')).toEqual({ kind: 'same' });
  });

  it('announces a patch deployed while the page is open', () => {
    const watch = new ServerVersionWatch();
    watch.check('1.6.2');
    expect(watch.check('1.6.3')).toEqual({ kind: 'patch', version: '1.6.3' });
    // and keeps saying so until the page loads again
    expect(watch.check('1.6.3')).toEqual({ kind: 'patch', version: '1.6.3' });
  });

  it('wants a reload for a new 1.x.0, a new major version and anything that is no release', () => {
    const minor = new ServerVersionWatch();
    minor.check('1.6.2');
    expect(minor.check('1.7.0')).toEqual({ kind: 'reload' });

    const major = new ServerVersionWatch();
    major.check('1.6.2');
    expect(major.check('2.0.0')).toEqual({ kind: 'reload' });

    const dev = new ServerVersionWatch();
    dev.check('0.0.1-SNAPSHOT');
    expect(dev.check('0.0.2-SNAPSHOT')).toEqual({ kind: 'reload' });
  });

  it('ignores answers without the header', () => {
    const watch = new ServerVersionWatch();
    expect(watch.check(null)).toEqual({ kind: 'same' });
    expect(watch.check('')).toEqual({ kind: 'same' });
    expect(watch.check('1.2.0')).toEqual({ kind: 'same' });
    expect(watch.check(null)).toEqual({ kind: 'same' });
    expect(watch.check('1.2.0')).toEqual({ kind: 'same' });
  });
});
