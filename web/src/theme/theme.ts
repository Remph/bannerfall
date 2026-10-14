/**
 * Light/dark theme (05-frontend §8): "system" follows the device; "light" and "dark" set
 * data-theme on <html>, which tokens.css reads. The choice is stored in localStorage. index.html
 * applies it before the first paint (public/theme-init.js, same storage key!), so the page doesn't
 * flash light first.
 */
export const themeModes = ['system', 'light', 'dark'] as const;
export type ThemeMode = (typeof themeModes)[number];

export const THEME_STORAGE_KEY = 'bannerfall.theme';

/** Anything other than 'light' or 'dark' (nothing stored, old values) means 'system'. */
export function parseThemeMode(value: string | null): ThemeMode {
  return value === 'light' || value === 'dark' ? value : 'system';
}

export function loadThemeMode(): ThemeMode {
  try {
    return parseThemeMode(localStorage.getItem(THEME_STORAGE_KEY));
  } catch {
    return 'system'; // storage blocked (private mode)
  }
}

export function applyThemeMode(mode: ThemeMode): void {
  if (mode === 'system') {
    document.documentElement.removeAttribute('data-theme');
  } else {
    document.documentElement.setAttribute('data-theme', mode);
  }
  try {
    if (mode === 'system') {
      localStorage.removeItem(THEME_STORAGE_KEY);
    } else {
      localStorage.setItem(THEME_STORAGE_KEY, mode);
    }
  } catch {
    // ignore: the theme still applies until the page is reloaded
  }
}
