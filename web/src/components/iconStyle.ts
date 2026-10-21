import { useSyncExternalStore } from 'react';

/**
 * Which icons the game shows (1.6): the medieval silhouettes from game-icons.net (default) or the
 * classic hand-drawn line icons, a choice in the settings. Stored per device in localStorage like the
 * theme; every <Icon> follows a change at once.
 */
export const iconStyles = ['medieval', 'classic'] as const;
export type IconStyle = (typeof iconStyles)[number];

export const ICON_STYLE_STORAGE_KEY = 'bannerfall.icons';

/** Anything other than 'classic' (nothing stored, old values) means the medieval icons. */
export function parseIconStyle(value: string | null): IconStyle {
  return value === 'classic' ? 'classic' : 'medieval';
}

function load(): IconStyle {
  try {
    return parseIconStyle(localStorage.getItem(ICON_STYLE_STORAGE_KEY));
  } catch {
    return 'medieval'; // storage blocked (private mode)
  }
}

let current: IconStyle = load();
const listeners = new Set<() => void>();

export function getIconStyle(): IconStyle {
  return current;
}

export function setIconStyle(style: IconStyle): void {
  current = style;
  try {
    if (style === 'medieval') {
      localStorage.removeItem(ICON_STYLE_STORAGE_KEY);
    } else {
      localStorage.setItem(ICON_STYLE_STORAGE_KEY, style);
    }
  } catch {
    // ignore: the choice still applies until the page is reloaded
  }
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function useIconStyle(): IconStyle {
  return useSyncExternalStore(subscribe, getIconStyle, getIconStyle);
}
