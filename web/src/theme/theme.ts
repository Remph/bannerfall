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

/**
 * Colour themes (1.6): the colours of the bars, main buttons and paper, each in a light and a dark
 * set (styles/palettes.css). 'green' is the game's own look and the default; the others set
 * data-palette on <html>. Stored per device like the light/dark choice, applied before the first
 * paint by public/theme-init.js (same key, same list!).
 */
export const palettes = ['green', 'blue', 'black', 'oled', 'purple', 'leather'] as const;
export type Palette = (typeof palettes)[number];

export const PALETTE_STORAGE_KEY = 'bannerfall.palette';

/** Anything that isn't one of the palettes (nothing stored, an old or removed one) means 'green'. */
export function parsePalette(value: string | null): Palette {
  return palettes.find((palette) => palette === value) ?? 'green';
}

export function loadPalette(): Palette {
  try {
    return parsePalette(localStorage.getItem(PALETTE_STORAGE_KEY));
  } catch {
    return 'green';
  }
}

export function applyPalette(palette: Palette): void {
  if (palette === 'green') {
    document.documentElement.removeAttribute('data-palette');
  } else {
    document.documentElement.setAttribute('data-palette', palette);
  }
  try {
    if (palette === 'green') {
      localStorage.removeItem(PALETTE_STORAGE_KEY);
    } else {
      localStorage.setItem(PALETTE_STORAGE_KEY, palette);
    }
  } catch {
    // ignore: the colours still apply until the page is reloaded
  }
}

/**
 * Keeps the browser's own bar (theme-color, index.html) in the colour of the game's top bar: it
 * follows the light/dark choice, the device's setting and the colour theme. Started once in main.tsx.
 */
export function followThemeColor(): void {
  const root = document.documentElement;
  const update = () => {
    const bar = getComputedStyle(root).getPropertyValue('--color-bar').trim();
    if (bar !== '') {
      document.querySelectorAll('meta[name="theme-color"]').forEach((meta) => meta.setAttribute('content', bar));
    }
  };
  new MutationObserver(update).observe(root, { attributes: true, attributeFilter: ['data-theme', 'data-palette'] });
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', update);
  update();
}
