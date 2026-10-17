import type { ComponentType } from 'react';

/**
 * After an update the old chunk files are gone from the server, while a page that was open before
 * still asks for them when the player opens another page: Vite then fires `vite:preloadError`.
 * The fix is to load the new version (1.3, lazy pages). A reload that comes back to the same error
 * within a few seconds would loop, so the second one in that time is left to the router's error.
 */

const KEY = 'bannerfall.staleChunkReloadAt';
const MIN_GAP_MS = 10_000;

/** Whether to reload now, and if so, notes the time. Without storage (private mode) it reloads. */
export function shouldReload(storage: Pick<Storage, 'getItem' | 'setItem'> | null, now: number): boolean {
  try {
    const last = Number(storage?.getItem(KEY) ?? Number.NaN);
    if (Number.isFinite(last) && now - last >= 0 && now - last < MIN_GAP_MS) {
      return false;
    }
    storage?.setItem(KEY, String(now));
  } catch {
    // storage refused (quota, privacy settings): reloading once more is still the best answer
  }
  return true;
}

/** Listens for chunks that failed to load and reloads the app (once per 10 s at most). */
export function reloadOnStaleChunks(target: Window = window) {
  target.addEventListener('vite:preloadError', (event) => {
    let storage: Storage | null = null;
    try {
      storage = target.sessionStorage;
    } catch {
      // no storage
    }
    if (shouldReload(storage, Date.now())) {
      event.preventDefault();
      target.location.reload();
    }
  });
}

/**
 * A route's `lazy` for a page whose code is a chunk of its own (1.3): `lazyPage(() =>
 * import('./pages/map/MapPage'), 'MapPage')`. When the chunk is gone and {@link reloadOnStaleChunks}
 * reloads the app, Vite's import gives `undefined`: the route then waits for the reload instead of
 * flashing the error screen.
 */
export function lazyPage<Name extends string>(load: () => Promise<Record<Name, ComponentType>>, name: Name) {
  return async (): Promise<{ Component: ComponentType }> => {
    const module = (await load()) as Record<Name, ComponentType> | undefined;
    if (module === undefined) {
      return new Promise(() => {});
    }
    return { Component: module[name] };
  };
}
