import { useSyncExternalStore } from 'react';

/**
 * Calls onChange whenever devicePixelRatio changes: the window moved to a screen with another pixel
 * density, or the browser zoom changed. A media query for exactly the current ratio stops matching
 * then; afterwards we listen for the new one.
 */
function subscribe(onChange: () => void): () => void {
  let media: MediaQueryList | undefined;
  const listen = () => {
    media = window.matchMedia(`(resolution: ${window.devicePixelRatio}dppx)`);
    media.addEventListener('change', changed, { once: true });
  };
  const changed = () => {
    listen();
    onChange();
  };
  listen();
  return () => media?.removeEventListener('change', changed);
}

function pixelRatio(): number {
  return window.devicePixelRatio || 1;
}

/** Device pixels per CSS pixel, and a re-render when it changes, so the map canvas stays sharp. */
export function useDevicePixelRatio(): number {
  return useSyncExternalStore(subscribe, pixelRatio);
}
