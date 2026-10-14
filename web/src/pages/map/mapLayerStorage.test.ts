import { afterEach, describe, expect, it, vi } from 'vitest';
import { loadMapLayer, saveMapLayer } from './mapLayerStorage';

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

describe('map layer storage', () => {
  it('remembers the guild layer and forgets the owner layer', () => {
    const items = stubStorage();
    expect(loadMapLayer()).toBe('owner');
    saveMapLayer('guild');
    expect(loadMapLayer()).toBe('guild');
    saveMapLayer('owner');
    expect(items.size).toBe(0);
    expect(loadMapLayer()).toBe('owner');
  });

  it('falls back to the owner colours when the storage is blocked', () => {
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
    expect(loadMapLayer()).toBe('owner');
    expect(() => saveMapLayer('guild')).not.toThrow();
  });
});
