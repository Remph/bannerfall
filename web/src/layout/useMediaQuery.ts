import { useCallback, useSyncExternalStore } from 'react';

/** Desktop layout from here on (05-frontend §8): the chat is a floating panel instead of a sheet. */
export const DESKTOP_QUERY = '(min-width: 1024px)';

/** From here on the open chat is a column at the right edge instead of a floating panel (1.3). */
export const CHAT_COLUMN_QUERY = '(min-width: 1600px)';

/**
 * From here on the map's village panel sits at the top of the side column instead of over the map
 * (1.3; a third column since 1.1.2): wide enough for the column, and high enough that panel,
 * overview and "go to" fit in it without scrolling (a player's panel needs about 960 px of window,
 * one with an extra line a bit more; at 920 px the column still scrolled). Keep the width in sync
 * with MapPage.module.css.
 */
export const PANEL_COLUMN_QUERY = '(min-width: 1280px) and (min-height: 1000px)';

/**
 * Whether a CSS media query matches right now, updated when it changes (window resized, phone
 * turned). For layouts that differ in what they render, not just in CSS.
 */
export function useMediaQuery(query: string): boolean {
  // the same function on every render, or React would subscribe anew each time
  const subscribe = useCallback(
    (onChange: () => void) => {
      if (typeof window.matchMedia !== 'function') {
        return () => {};
      }
      const list = window.matchMedia(query);
      list.addEventListener('change', onChange);
      return () => list.removeEventListener('change', onChange);
    },
    [query],
  );
  // no matchMedia (the tests' jsdom): the phone layout
  return useSyncExternalStore(
    subscribe,
    () => typeof window.matchMedia === 'function' && window.matchMedia(query).matches,
  );
}
