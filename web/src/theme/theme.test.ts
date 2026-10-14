import { describe, expect, it } from 'vitest';
import { parseThemeMode } from './theme';

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
