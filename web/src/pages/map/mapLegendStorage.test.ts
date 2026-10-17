import { afterEach, describe, expect, it, vi } from 'vitest';
import { loadLegendOpen, saveLegendOpen } from './mapLegendStorage';

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

describe('map legend storage', () => {
  it('remembers an open legend and forgets a folded one', () => {
    const items = stubStorage();
    expect(loadLegendOpen()).toBe(false);
    saveLegendOpen(true);
    expect(loadLegendOpen()).toBe(true);
    saveLegendOpen(false);
    expect(items.size).toBe(0);
    expect(loadLegendOpen()).toBe(false);
  });

  it('stays folded when the storage is blocked', () => {
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
    expect(loadLegendOpen()).toBe(false);
    expect(() => saveLegendOpen(true)).not.toThrow();
  });
});
