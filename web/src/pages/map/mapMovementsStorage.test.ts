import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  loadShowLines,
  loadShowMovements,
  loadVillageOnly,
  saveShowLines,
  saveShowMovements,
  saveVillageOnly,
} from './mapMovementsStorage';

function stubStorage() {
  const items = new Map<string, string>();
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => items.get(key) ?? null,
    setItem: (key: string, value: string) => void items.set(key, value),
    removeItem: (key: string) => void items.delete(key),
  });
  return items;
}

afterEach(() => vi.unstubAllGlobals());

describe('movement markers storage', () => {
  it('shows the markers until they are switched off, and forgets the default', () => {
    const items = stubStorage();
    expect(loadShowMovements()).toBe(true);
    saveShowMovements(false);
    expect(loadShowMovements()).toBe(false);
    saveShowMovements(true);
    expect(items.size).toBe(0);
    expect(loadShowMovements()).toBe(true);
  });

  it('shows the markers when the storage is blocked', () => {
    vi.stubGlobal('localStorage', {
      getItem: () => {
        throw new Error('blocked');
      },
      setItem: () => {
        throw new Error('blocked');
      },
      removeItem: () => {
        throw new Error('blocked');
      },
    });
    expect(loadShowMovements()).toBe(true);
    expect(() => saveShowMovements(false)).not.toThrow();
  });
});

describe('movement lines storage', () => {
  it('hides the lines until they are switched on, and forgets the default', () => {
    const items = stubStorage();
    expect(loadShowLines()).toBe(false);
    saveShowLines(true);
    expect(loadShowLines()).toBe(true);
    saveShowLines(false);
    expect(items.size).toBe(0);
    expect(loadShowLines()).toBe(false);
  });

  it('hides the lines when the storage is blocked', () => {
    vi.stubGlobal('localStorage', {
      getItem: () => {
        throw new Error('blocked');
      },
      setItem: () => {
        throw new Error('blocked');
      },
      removeItem: () => {
        throw new Error('blocked');
      },
    });
    expect(loadShowLines()).toBe(false);
    expect(() => saveShowLines(true)).not.toThrow();
  });
});

describe('movements of one village storage', () => {
  it('shows only the village’s movements until all are asked for, and forgets the default', () => {
    const items = stubStorage();
    expect(loadVillageOnly()).toBe(true);
    saveVillageOnly(false);
    expect(loadVillageOnly()).toBe(false);
    saveVillageOnly(true);
    expect(items.size).toBe(0);
    expect(loadVillageOnly()).toBe(true);
  });
});
