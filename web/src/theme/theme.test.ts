// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest';
import {
  applyPalette,
  applyThemeMode,
  loadPalette,
  loadThemeMode,
  PALETTE_STORAGE_KEY,
  parsePalette,
  parseThemeMode,
  THEME_STORAGE_KEY,
} from './theme';

describe('parseThemeMode', () => {
  it('accepts light, dark and black', () => {
    expect(parseThemeMode('light')).toBe('light');
    expect(parseThemeMode('dark')).toBe('dark');
    expect(parseThemeMode('black')).toBe('black');
  });

  it('falls back to system', () => {
    expect(parseThemeMode(null)).toBe('system');
    expect(parseThemeMode('sepia')).toBe('system');
  });
});

describe('the black mode (2.0)', () => {
  afterEach(() => applyThemeMode('system'));

  it('is the night plus data-black, and is remembered as black', () => {
    applyThemeMode('black');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(document.documentElement.hasAttribute('data-black')).toBe(true);
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('black');
    expect(loadThemeMode()).toBe('black');
  });

  it('leaves no data-black behind when another mode is chosen', () => {
    applyThemeMode('black');
    applyThemeMode('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(document.documentElement.hasAttribute('data-black')).toBe(false);
  });
});

describe('colour themes (1.6, the four of 2.0)', () => {
  afterEach(() => applyPalette('wiese'));

  it('parses the known palettes and falls back to wiese', () => {
    expect(parsePalette('frost')).toBe('frost');
    expect(parsePalette('heide')).toBe('heide');
    expect(parsePalette(null)).toBe('wiese');
    expect(parsePalette('sepia')).toBe('wiese');
    // names every object has are not themes either
    expect(parsePalette('constructor')).toBe('wiese');
    expect(parsePalette('toString')).toBe('wiese');
  });

  it('turns a theme stored by 1.x into its successor', () => {
    expect(parsePalette('green')).toBe('wiese');
    expect(parsePalette('blue')).toBe('frost');
    expect(parsePalette('black')).toBe('wiese');
    expect(parsePalette('oled')).toBe('wiese');
    expect(parsePalette('purple')).toBe('heide');
    expect(parsePalette('leather')).toBe('kornfeld');
  });

  it('sets data-palette and remembers the choice', () => {
    applyPalette('heide');
    expect(document.documentElement.getAttribute('data-palette')).toBe('heide');
    expect(localStorage.getItem(PALETTE_STORAGE_KEY)).toBe('heide');
    expect(loadPalette()).toBe('heide');
  });

  it('wiese is the plain page: no attribute, nothing stored', () => {
    applyPalette('frost');
    applyPalette('wiese');
    expect(document.documentElement.hasAttribute('data-palette')).toBe(false);
    expect(localStorage.getItem(PALETTE_STORAGE_KEY)).toBeNull();
  });
});
