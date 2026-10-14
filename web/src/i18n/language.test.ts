import { describe, expect, it } from 'vitest';
import { pickLanguage } from './language';

describe('pickLanguage', () => {
  it('prefers the stored choice', () => {
    expect(pickLanguage('de', ['en-US'])).toBe('de');
  });

  it('ignores an invalid stored value', () => {
    expect(pickLanguage('fr', ['de-DE'])).toBe('de');
  });

  it('takes the first supported browser language', () => {
    expect(pickLanguage(null, ['fr-FR', 'de-AT', 'en'])).toBe('de');
    expect(pickLanguage(null, ['EN-gb', 'de'])).toBe('en');
  });

  it('falls back to English', () => {
    expect(pickLanguage(null, ['fr', 'it'])).toBe('en');
    expect(pickLanguage(null, [])).toBe('en');
  });
});
