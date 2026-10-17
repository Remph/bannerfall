import { describe, expect, it } from 'vitest';
import { isEmailAddress } from './contact';

describe('isEmailAddress', () => {
  it('takes a plain address', () => {
    expect(isEmailAddress('host@example.com')).toBe(true);
    expect(isEmailAddress('privacy.team+world@play.example.org')).toBe(true);
  });

  it('leaves anything else as text', () => {
    expect(isEmailAddress('Discord: host#1234')).toBe(false);
    expect(isEmailAddress('host@example.com or ask in the chat')).toBe(false);
    expect(isEmailAddress('@host')).toBe(false);
    expect(isEmailAddress('host@localhost')).toBe(false);
  });
});
