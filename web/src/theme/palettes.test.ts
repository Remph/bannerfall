import { describe, expect, it } from 'vitest';
import themeInit from '../../public/theme-init.js?raw';
import palettesCss from '../styles/palettes.css?raw';
import tokensCss from '../styles/tokens.css?raw';
import { palettes } from './theme';

/** The custom properties of every rule whose selector contains `selector`, merged ("--color-bar" → "#2f4a3c"). */
function tokens(css: string, selector: string): Map<string, string> {
  const result = new Map<string, string>();
  for (const [, selectors = '', body = ''] of css.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
    if (!selectors.includes(selector)) {
      continue;
    }
    for (const [, name = '', value = ''] of body.matchAll(/(--[\w-]+):\s*([^;]+);/g)) {
      result.set(name, value.trim());
    }
  }
  return result;
}

/** The three blocks of a palette in palettes.css: light, dark by the device's setting, dark by choice. */
function blocks(palette: string) {
  const media = palettesCss.slice(palettesCss.indexOf('@media'));
  const manual = media.slice(media.indexOf("\n:root[data-theme='dark']"));
  const inMedia = media.slice(0, media.length - manual.length);
  return {
    light: tokens(palettesCss.slice(0, palettesCss.indexOf('@media')), `[data-palette='${palette}']`),
    deviceDark: tokens(inMedia, `[data-palette='${palette}']`),
    chosenDark: tokens(manual, `[data-palette='${palette}']`),
  };
}

describe('colour themes in palettes.css (1.6)', () => {
  it.each(palettes)('%s sets the same tokens light and dark, and both dark blocks alike', (palette) => {
    const { light, deviceDark, chosenDark } = blocks(palette);
    expect(light.size).toBeGreaterThan(10);
    expect([...deviceDark.keys()]).toEqual([...light.keys()]);
    expect(deviceDark).toEqual(chosenDark);
  });

  it('every palette sets the same tokens', () => {
    const names = [...blocks('green').light.keys()];
    for (const palette of palettes) {
      expect([...blocks(palette).light.keys()]).toEqual(names);
    }
  });

  it('no palette touches the war, map marker, village, banner or chart colours', () => {
    // A theme is the frame only; of the map it may change just the edge, the axis and the outside:
    // "--color-bar" and "--map-edge" pass, "--color-danger" and "--map-npc" don't
    const frame = /^--(color-(?!danger|on-danger|success)[\w-]+|map-(edge|axis|outside))$/;
    const outside = palettes.flatMap((palette) => {
      const { light, chosenDark } = blocks(palette);
      return [...light.keys(), ...chosenDark.keys()]
        .filter((name) => !frame.test(name))
        .map((name) => `${palette} ${name}`);
    });
    expect(outside).toEqual([]);
  });

  it("green is tokens.css's own look, so the tiles and the plain page agree", () => {
    const { light, chosenDark } = blocks('green');
    const base = tokens(tokensCss.slice(0, tokensCss.indexOf('@media')), ':root');
    const baseDark = tokens(tokensCss.slice(tokensCss.lastIndexOf("\n:root[data-theme='dark']")), ':root');
    for (const [name, value] of light) {
      expect([name, value]).toEqual([name, base.get(name)]);
    }
    for (const [name, value] of chosenDark) {
      expect([name, value]).toEqual([name, baseDark.get(name)]);
    }
  });

  it('palettes.css and theme-init.js know exactly the palettes of theme.ts', () => {
    const inCss = new Set([...palettesCss.matchAll(/\[data-palette='(\w+)'\]/g)].map((match) => match[1]));
    expect([...inCss].sort()).toEqual([...palettes].sort());
    const inInit = [...themeInit.matchAll(/palette === '(\w+)'/g)].map((match) => match[1]);
    expect(['green', ...inInit].sort()).toEqual([...palettes].sort());
  });
});
