// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest';
import { applyPalette, loadPalette, PALETTE_STORAGE_KEY, parsePalette, parseThemeMode } from './theme';

describe('parseThemeMode', () => {
  it('accepts light and dark', () => {
    expect(parseThemeMode('light')).toBe('light');
    expect(parseThemeMode('dark')).toBe('dark');
  });

  it('falls back to system', () => {
    expect(parseThemeMode(null)).toBe('system');
    expect(parseThemeMode('sepia')).toBe('system');
  });
});

describe('colour themes (1.6)', () => {
  afterEach(() => applyPalette('green'));

  it('parses the known palettes and falls back to green', () => {
    expect(parsePalette('blue')).toBe('blue');
    expect(parsePalette('leather')).toBe('leather');
    expect(parsePalette(null)).toBe('green');
    expect(parsePalette('sepia')).toBe('green');
  });

  it('sets data-palette and remembers the choice', () => {
    applyPalette('purple');
    expect(document.documentElement.getAttribute('data-palette')).toBe('purple');
    expect(localStorage.getItem(PALETTE_STORAGE_KEY)).toBe('purple');
    expect(loadPalette()).toBe('purple');
  });

  it('green is the plain page: no attribute, nothing stored', () => {
    applyPalette('black');
    applyPalette('green');
    expect(document.documentElement.hasAttribute('data-palette')).toBe(false);
    expect(localStorage.getItem(PALETTE_STORAGE_KEY)).toBeNull();
  });
});
