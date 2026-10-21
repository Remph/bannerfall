// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest';
import { rememberChoice, rememberedChoice } from './returnChoice';

describe('the remembered "notify me when they’re back" (1.6)', () => {
  afterEach(() => localStorage.clear());

  it('starts off and keeps what the player chose last, per switch', () => {
    expect(rememberedChoice('attack')).toBe(false);
    rememberChoice('attack', true);
    expect(rememberedChoice('attack')).toBe(true);
    expect(rememberedChoice('farmRun')).toBe(false);
    rememberChoice('attack', false);
    expect(rememberedChoice('attack')).toBe(false);
  });
});
