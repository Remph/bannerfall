import { useSyncExternalStore } from 'react';
import { parsePalette, type Palette } from './theme';

const DARK_QUERY = '(prefers-color-scheme: dark)';

/** The theme on screen right now: the manual choice (data-theme on <html>) or else the device's setting. */
function effectiveTheme(): 'light' | 'dark' {
  const chosen = document.documentElement.getAttribute('data-theme');
  if (chosen === 'light' || chosen === 'dark') {
    return chosen;
  }
  return window.matchMedia(DARK_QUERY).matches ? 'dark' : 'light';
}

function subscribe(onChange: () => void): () => void {
  const observer = new MutationObserver(onChange);
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme', 'data-palette'] });
  const media = window.matchMedia(DARK_QUERY);
  media.addEventListener('change', onChange);
  return () => {
    observer.disconnect();
    media.removeEventListener('change', onChange);
  };
}

/**
 * 'light' or 'dark', and a re-render whenever that changes – for things CSS can't restyle by itself,
 * like the map canvas, which reads the colour tokens when it draws.
 */
export function useEffectiveTheme(): 'light' | 'dark' {
  // useSyncExternalStore: React's way to read a value that lives outside React (here: the DOM).
  return useSyncExternalStore(subscribe, effectiveTheme);
}

/** The colour theme on screen (data-palette on <html>, 1.6), for the same canvas redraws. */
export function usePalette(): Palette {
  return useSyncExternalStore(subscribe, () => parsePalette(document.documentElement.getAttribute('data-palette')));
}
