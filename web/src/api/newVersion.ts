import { useSyncExternalStore } from 'react';

/**
 * The patch the server runs since this page was loaded (1.6.3), told by the API client when an
 * answer carries a newer one; null until then. Plain module state with listeners, because the
 * client is no React code.
 */
let announced: string | null = null;
const listeners = new Set<() => void>();

export function announceNewVersion(version: string): void {
  if (announced !== version) {
    announced = version;
    listeners.forEach((listener) => listener());
  }
}

/** For tests: the page "loads again". */
export function forgetNewVersion(): void {
  announced = null;
  listeners.forEach((listener) => listener());
}

/** The announced version, or null. */
export function useAnnouncedVersion(): string | null {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    () => announced,
  );
}
