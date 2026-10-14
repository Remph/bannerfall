import { useCallback, useSyncExternalStore } from 'react';

/** Desktop layout from here on (05-frontend §8): the chat is a floating panel instead of a sheet. */
export const DESKTOP_QUERY = '(min-width: 1024px)';

/**
 * The map's third column (1.1.2): from here on the village panel sits next to the overview column
 * instead of below it. Keep in sync with the media query in MapPage.module.css.
 */
export const THREE_COLUMNS_QUERY = '(min-width: 1280px)';

/**
 * Whether a CSS media query matches right now, updated when it changes (window resized, phone
 * turned). For layouts that differ in what they render, not just in CSS.
 */
export function useMediaQuery(query: string): boolean {
  // the same function on every render, or React would subscribe anew each time
  const subscribe = useCallback(
    (onChange: () => void) => {
      const list = window.matchMedia(query);
      list.addEventListener('change', onChange);
      return () => list.removeEventListener('change', onChange);
    },
    [query],
  );
  return useSyncExternalStore(subscribe, () => window.matchMedia(query).matches);
}
