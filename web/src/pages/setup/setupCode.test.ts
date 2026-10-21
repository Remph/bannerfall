import { afterEach, describe, expect, it, vi } from 'vitest';
import { forgetSetupCode, pendingSetupCode, rememberSetupCode } from './setupCode';

function storage(map: Map<string, string>): Pick<Storage, 'getItem' | 'setItem' | 'removeItem'> {
  return {
    getItem: (key) => map.get(key) ?? null,
    setItem: (key, value) => void map.set(key, value),
    removeItem: (key) => void map.delete(key),
  };
}

describe('setup code', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('keeps the code of a setup link until it is claimed', () => {
    vi.stubGlobal('sessionStorage', storage(new Map()));
    rememberSetupCode({ pathname: '/setup', search: '?code=abcdefghjk' });
    expect(pendingSetupCode()).toBe('abcdefghjk');
    forgetSetupCode();
    expect(pendingSetupCode()).toBeNull();
  });

  it('ignores other pages and links without a code', () => {
    vi.stubGlobal('sessionStorage', storage(new Map()));
    rememberSetupCode({ pathname: '/', search: '?code=abcdefghjk' });
    rememberSetupCode({ pathname: '/setup', search: '?step=pace' });
    expect(pendingSetupCode()).toBeNull();
  });

  it('works without storage', () => {
    const refusing = {
      getItem: () => {
        throw new Error('blocked');
      },
      setItem: () => {
        throw new Error('blocked');
      },
      removeItem: () => {
        throw new Error('blocked');
      },
    };
    vi.stubGlobal('sessionStorage', refusing);
    rememberSetupCode({ pathname: '/setup', search: '?code=abcdefghjk' });
    expect(pendingSetupCode()).toBeNull();
    expect(() => forgetSetupCode()).not.toThrow();
  });
});
