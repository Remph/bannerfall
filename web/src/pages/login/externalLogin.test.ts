import { describe, expect, it } from 'vitest';
import { de } from '../../i18n/de';
import { en } from '../../i18n/en';
import { externalProblemText } from './externalLogin';

const nothing = { name: null, problem: null, bannedUntil: null, banReason: null };

describe('externalProblemText', () => {
  it('is null when the last login through the account server went fine', () => {
    expect(externalProblemText(en, 'en', undefined, 'accounts.example.com')).toBeNull();
    expect(externalProblemText(en, 'en', nothing, 'accounts.example.com')).toBeNull();
  });

  it('names the account server', () => {
    expect(externalProblemText(en, 'en', { ...nothing, problem: 'UNAVAILABLE' }, 'accounts.example.com')).toBe(
      'accounts.example.com cannot be reached right now. Please try again later.',
    );
    expect(externalProblemText(de, 'de', { ...nothing, problem: 'FAILED' }, 'accounts.example.com')).toContain(
      'accounts.example.com',
    );
  });

  it('says until when and why a player is banned on this world', () => {
    const text = externalProblemText(
      en,
      'en',
      { ...nothing, problem: 'BANNED', bannedUntil: '9999-12-31T23:59:59Z', banReason: 'spam' },
      'accounts.example.com',
    );
    expect(text).toBe('This account is banned for good. Reason: spam');
  });
});
