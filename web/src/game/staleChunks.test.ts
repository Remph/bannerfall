import { describe, expect, it } from 'vitest';
import { lazyPage, shouldReload } from './staleChunks';

function memoryStorage() {
  const items = new Map<string, string>();
  return {
    getItem: (key: string) => items.get(key) ?? null,
    setItem: (key: string, value: string) => void items.set(key, value),
  };
}

describe('shouldReload', () => {
  it('reloads the first time and not again within ten seconds', () => {
    const storage = memoryStorage();
    expect(shouldReload(storage, 1_000_000)).toBe(true);
    expect(shouldReload(storage, 1_005_000)).toBe(false);
    expect(shouldReload(storage, 1_010_000)).toBe(true);
  });

  it('reloads without storage, and when the storage refuses', () => {
    expect(shouldReload(null, 0)).toBe(true);
    const refusing = {
      getItem: () => null,
      setItem: () => {
        throw new Error('QuotaExceededError');
      },
    };
    expect(shouldReload(refusing, 0)).toBe(true);
  });

  it('ignores a time in the future (a changed device clock)', () => {
    const storage = memoryStorage();
    storage.setItem('bannerfall.staleChunkReloadAt', '5000000');
    expect(shouldReload(storage, 1_000_000)).toBe(true);
  });
});

describe('lazyPage', () => {
  function Page() {
    return null;
  }

  it('gives the router the named page', async () => {
    const route = await lazyPage(() => Promise.resolve({ Page }), 'Page')();
    expect(route.Component).toBe(Page);
  });

  it('keeps waiting while a stale chunk reloads the app', async () => {
    // Vite's import resolves to undefined once vite:preloadError was prevented
    const gone = () => Promise.resolve(undefined as unknown as Record<'Page', typeof Page>);
    const result = await Promise.race([
      lazyPage(gone, 'Page')().then(() => 'settled'),
      new Promise((resolve) => setTimeout(() => resolve('waiting'), 20)),
    ]);
    expect(result).toBe('waiting');
  });
});
