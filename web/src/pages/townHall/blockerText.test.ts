import { describe, expect, it } from 'vitest';
import { de } from '../../i18n/de';
import { en } from '../../i18n/en';
import { blockerText } from './blockerText';

describe('blockerText', () => {
  const now = Date.parse('2026-10-06T12:00:00Z'); // a Tuesday

  it('names the weekday when resources are enough within the week', () => {
    const text = blockerText(en, 'en', now, { kind: 'later', atMs: Date.parse('2026-10-08T12:00:00Z') });
    expect(text).toContain('Thu');
  });

  it('names the date when the wait is longer (a weekday alone would be ambiguous)', () => {
    const text = blockerText(en, 'en', now, { kind: 'later', atMs: Date.parse('2026-10-14T12:00:00Z') });
    expect(text).toContain('14 Oct');
  });

  it('says why a Banner Hall cannot be built without banners, in the words of the server refusal', () => {
    expect(blockerText(en, 'en', now, { kind: 'bannersDisabled' })).toBe('Banners are switched off in this world.');
    expect(blockerText(de, 'de', now, { kind: 'bannersDisabled' })).toBe('Banner sind in dieser Welt ausgeschaltet.');
  });
});
