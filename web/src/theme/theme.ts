/**
 * Day, night and black (05-frontend §8): "system" follows the device; "light" and "dark" set
 * data-theme on <html>, which tokens.css reads. "black" (2.0) is the night on a pure black ground for
 * OLED screens: data-theme="dark" plus data-black, so everything made for the night applies and
 * palettes.css only turns the ground black. The choice is stored in localStorage. index.html applies
 * it before the first paint (public/theme-init.js, same storage key!), so the page doesn't flash
 * light first.
 */
export const themeModes = ['system', 'light', 'dark', 'black'] as const;
export type ThemeMode = (typeof themeModes)[number];

export const THEME_STORAGE_KEY = 'bannerfall.theme';

/** Anything other than 'light', 'dark' or 'black' (nothing stored, old values) means 'system'. */
export function parseThemeMode(value: string | null): ThemeMode {
  return value === 'light' || value === 'dark' || value === 'black' ? value : 'system';
}

export function loadThemeMode(): ThemeMode {
  try {
    return parseThemeMode(localStorage.getItem(THEME_STORAGE_KEY));
  } catch {
    return 'system'; // storage blocked (private mode)
  }
}

export function applyThemeMode(mode: ThemeMode): void {
  const root = document.documentElement;
  if (mode === 'system') {
    root.removeAttribute('data-theme');
  } else {
    root.setAttribute('data-theme', mode === 'black' ? 'dark' : mode);
  }
  root.toggleAttribute('data-black', mode === 'black');
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
 * Colour themes (1.6; the five of 2.0): the land, its sky and the colour of the main button, each for
 * the day and the night (styles/palettes.css). 'wiese' is the game's own look and the default, 'gestickt' also sets the look switches (styles/looks.css); the
 * others set data-palette on <html>. Stored per device like the day/night choice, applied before the
 * first paint by public/theme-init.js (same key, same list!).
 */
export const palettes = ['wiese', 'kornfeld', 'frost', 'heide', 'gestickt'] as const;
export type Palette = (typeof palettes)[number];

export const PALETTE_STORAGE_KEY = 'bannerfall.palette';

/**
 * The 1.x themes a device may still have stored, and the 2.0 theme each one becomes. theme-init.js
 * rewrites the stored value with the same table (and turns the old OLED theme into the black mode).
 */
export const legacyPalettes: Readonly<Record<string, Palette>> = {
  green: 'wiese',
  blue: 'frost',
  black: 'wiese',
  oled: 'wiese',
  purple: 'heide',
  leather: 'kornfeld',
};

/** Anything that isn't one of the palettes (nothing stored, a removed one without a successor) means 'wiese'. */
export function parsePalette(value: string | null): Palette {
  const known = palettes.find((palette) => palette === value);
  if (known !== undefined) {
    return known;
  }
  // hasOwn: a stored "constructor" or "toString" must not find Object's own members in the table
  return (value !== null && Object.hasOwn(legacyPalettes, value) ? legacyPalettes[value] : undefined) ?? 'wiese';
}

export function loadPalette(): Palette {
  try {
    return parsePalette(localStorage.getItem(PALETTE_STORAGE_KEY));
  } catch {
    return 'wiese';
  }
}

export function applyPalette(palette: Palette): void {
  if (palette === 'wiese') {
    document.documentElement.removeAttribute('data-palette');
  } else {
    document.documentElement.setAttribute('data-palette', palette);
  }
  try {
    if (palette === 'wiese') {
      localStorage.removeItem(PALETTE_STORAGE_KEY);
    } else {
      localStorage.setItem(PALETTE_STORAGE_KEY, palette);
    }
  } catch {
    // ignore: the colours still apply until the page is reloaded
  }
}

/**
 * Keeps the browser's own bar (theme-color, index.html) in the colour of the top of the game's sky: it
 * follows the day, night or black choice, the device's setting and the colour theme. Started once in main.tsx.
 */
export function followThemeColor(): void {
  const root = document.documentElement;
  const update = () => {
    const sky = getComputedStyle(root).getPropertyValue('--color-sky-1').trim();
    if (sky !== '') {
      document.querySelectorAll('meta[name="theme-color"]').forEach((meta) => meta.setAttribute('content', sky));
    }
  };
  new MutationObserver(update).observe(root, {
    attributes: true,
    attributeFilter: ['data-theme', 'data-black', 'data-palette'],
  });
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', update);
  update();
}
