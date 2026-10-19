import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  loadRememberedVillage,
  parseVillageId,
  rememberVillage,
  resolveVillageId,
  villagePath,
} from './currentVillage';

describe('parseVillageId', () => {
  it('accepts plain numbers only', () => {
    expect(parseVillageId('12')).toBe(12);
    expect(parseVillageId(undefined)).toBeUndefined();
    expect(parseVillageId('abc')).toBeUndefined();
    expect(parseVillageId('1e3')).toBeUndefined();
    expect(parseVillageId('-4')).toBeUndefined();
  });
});

describe('resolveVillageId', () => {
  const own = [7, 9];

  it('prefers the URL, even for a foreign village (the server decides)', () => {
    expect(resolveVillageId('9', 7, own)).toBe(9);
    expect(resolveVillageId('42', 7, own)).toBe(42);
  });

  it('then the remembered village, if it is still ours', () => {
    expect(resolveVillageId(undefined, 9, own)).toBe(9);
    expect(resolveVillageId(undefined, 42, own)).toBe(7);
  });

  it('then the first village, or none', () => {
    expect(resolveVillageId(undefined, null, own)).toBe(7);
    expect(resolveVillageId('abc', null, own)).toBe(7);
    expect(resolveVillageId(undefined, null, [])).toBeUndefined();
  });
});

describe('villagePath', () => {
  it('keeps the screen when switching villages', () => {
    expect(villagePath('/v/1', 2)).toBe('/v/2');
    expect(villagePath('/v/1/hq', 2)).toBe('/v/2/hq');
    expect(villagePath('/v/1/recruit/BARRACKS', 23)).toBe('/v/23/recruit/BARRACKS');
  });

  it('opens the overview from other screens', () => {
    expect(villagePath('/reports', 2)).toBe('/v/2');
    expect(villagePath('/', 2)).toBe('/v/2');
  });
});

describe('the remembered village', () => {
  // the tests run in node: tiny stand-ins for the browser's storages
  let session: Map<string, string>;
  let local: Map<string, string>;
  const storage = (store: Map<string, string>) => ({
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => store.set(key, value),
    removeItem: (key: string) => store.delete(key),
  });
  beforeEach(() => {
    session = new Map();
    local = new Map([['bannerfall.village', '9']]); // left behind by 1.4
    vi.stubGlobal('sessionStorage', storage(session));
    vi.stubGlobal('localStorage', storage(local));
  });
  afterEach(() => vi.unstubAllGlobals());

  it('lasts only for the browser session, so a new start opens the main village', () => {
    expect(loadRememberedVillage()).toBeNull();
    rememberVillage(9);
    expect(loadRememberedVillage()).toBe(9);
    session.clear(); // the game was closed
    expect(loadRememberedVillage()).toBeNull();
  });

  it('clears the one 1.4 kept for good', () => {
    rememberVillage(7);
    expect(local.has('bannerfall.village')).toBe(false);
  });

  it('is skipped where the browser refuses storage', () => {
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
    vi.stubGlobal('localStorage', refusing);
    expect(() => rememberVillage(7)).not.toThrow();
    expect(loadRememberedVillage()).toBeNull();
  });
});
