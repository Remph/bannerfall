import { afterEach, describe, expect, it, vi } from 'vitest';
import { loadShowMovements, saveShowMovements } from './mapMovementsStorage';

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
