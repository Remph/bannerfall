import { describe, expect, it } from 'vitest';
import themeInit from '../../public/theme-init.js?raw';
import looksCss from '../styles/looks.css?raw';
import palettesCss from '../styles/palettes.css?raw';
import tokensCss from '../styles/tokens.css?raw';
import diplomacyCss from '../pages/diplomacy/DiplomacyPage.module.css?raw';
import { legacyPalettes, palettes } from './theme';

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

describe('colour themes in palettes.css (1.6, the five of 2.0)', () => {
  it.each(palettes)('%s sets the same tokens light and dark, and both dark blocks alike', (palette) => {
    const { light, deviceDark, chosenDark } = blocks(palette);
    expect(light.size).toBeGreaterThan(10);
    expect([...deviceDark.keys()]).toEqual([...light.keys()]);
    expect(deviceDark).toEqual(chosenDark);
  });

  it('every palette sets the same tokens', () => {
    const names = [...blocks('wiese').light.keys()];
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

  it('black is a mode, not a theme: one block after the themes that turns ground, sky and bars black', () => {
    const start = palettesCss.lastIndexOf("\n:root[data-theme='dark'][data-black]");
    expect(start).toBeGreaterThan(palettesCss.lastIndexOf('[data-palette='));
    const black = tokens(palettesCss.slice(start), '[data-black]');
    for (const token of ['--color-bg', '--color-bar', '--color-sky-1', '--map-outside']) {
      expect(black.get(token)).toBe('#000000');
    }
    // what floats and the line under the bars still lift off the black
    expect(black.get('--color-surface')).not.toBe('#000000');
    expect(black.get('--color-bar-edge')).not.toBe('#000000');
    // the picker's tiles carry a data-palette of their own: the block reaches them, so they show black too
    expect(palettesCss).toContain('[data-black] [data-palette] {');
    // the mound's shade is as dark as the ground in black: hilly fields far out take the theme's outline grey
    expect(black.get('--map-hill-far')).toBe('var(--color-border)');
    // the theme keeps its own text, signs and main button
    for (const token of ['--color-text', '--color-accent', '--color-action', '--color-danger']) {
      expect(black.has(token)).toBe(false);
    }
  });

  it("wiese is tokens.css's own look, so the tiles and the plain page agree", () => {
    const { light, chosenDark } = blocks('wiese');
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
    const inInit = [
      ...themeInit.matchAll(/palette === '(kornfeld|frost|heide|wiese)' \|\||palette === '(\w+)'\) \{/g),
    ].map((match) => match[1] ?? match[2]);
    expect(['wiese', ...inInit].sort()).toEqual([...palettes].sort());
  });

  it("theme-init.js knows each theme's sky for the browser's bar (theme-color) by day and at night", () => {
    const table = /var skies = \{([^}]*)\}/.exec(themeInit)?.[1] ?? '';
    const inInit = Object.fromEntries(
      [...table.matchAll(/(\w+): \['(#[0-9a-f]{6})', '(#[0-9a-f]{6})'\]/g)].map((match) => [
        match[1],
        [match[2], match[3]],
      ]),
    );
    const inCss = Object.fromEntries(
      palettes.map((palette) => {
        const { light, chosenDark } = blocks(palette);
        return [palette, [light.get('--color-sky-1'), chosenDark.get('--color-sky-1')]];
      }),
    );
    expect(inInit).toEqual(inCss);
  });

  it('theme-init.js maps the 1.x themes like theme.ts', () => {
    const table = /var legacy = \{([^}]*)\}/.exec(themeInit)?.[1] ?? '';
    const inInit = Object.fromEntries([...table.matchAll(/(\w+): '(\w+)'/g)].map((match) => [match[1], match[2]]));
    expect(inInit).toEqual(legacyPalettes);
  });
});

describe('look switches in looks.css (2.0, the stitched theme)', () => {
  const defaults = tokens(tokensCss.slice(0, tokensCss.indexOf('@media')), ':root');
  const media = looksCss.slice(looksCss.indexOf('@media'));
  const manual = media.slice(media.indexOf("\n:root[data-theme='dark']"));
  const day = tokens(looksCss.slice(0, looksCss.indexOf('@media')), "[data-palette='gestickt']");
  const deviceNight = tokens(media.slice(0, media.length - manual.length), "[data-palette='gestickt']");
  const chosenNight = tokens(manual.slice(0, manual.indexOf('[data-black]')), "[data-palette='gestickt']");

  it('sets only switches from the fixed list, never a colour token', () => {
    expect(day.size).toBeGreaterThan(5);
    const picture = ['--picture-outline', '--picture-outline-width', '--picture-outline-join'];
    for (const name of [...day.keys(), ...deviceNight.keys()]) {
      expect(name.startsWith('--color-')).toBe(false);
      expect(defaults.has(name) || picture.includes(name), name).toBe(true);
    }
  });

  it('keeps the picture outline unset by default, so the plain themes draw the village as before', () => {
    expect([...defaults.keys()].filter((name) => name.startsWith('--picture-outline'))).toEqual([]);
  });

  it('draws nothing by default: no ring in the sun, no seam in the alarm, no box round a label', () => {
    expect(defaults.get('--orb-seam')).toBe('0 solid transparent');
    expect(defaults.get('--seam-on-dark')).toBe('0 solid transparent');
    expect(defaults.get('--plate-fill')).toBe('transparent');
    expect(defaults.get('--plate-stroke')).toBe('none');
    expect(defaults.get('--plate-stroke-width')).toBe('1px');
    expect(defaults.get('--plate-dash')).toBe('none');
  });

  it('draws the label boxes, the ring in the sun and the moon at night, in a lighter thread on the dark land', () => {
    for (const name of ['--orb-seam', '--plate-fill', '--plate-stroke', '--plate-dash']) {
      expect(day.has(name), name).toBe(true);
    }
    expect(deviceNight.get('--plate-stroke')).not.toBe(day.get('--plate-stroke'));
    expect(deviceNight.has('--orb-seam')).toBe(true);
  });

  it('has both night blocks alike, and no weave in black', () => {
    expect(deviceNight.size).toBeGreaterThan(0);
    // the values span lines and the media block is indented: compare them with single spaces
    const flat = (block: Map<string, string>) => [...block].map(([name, value]) => [name, value.replace(/\s+/g, ' ')]);
    expect(flat(deviceNight)).toEqual(flat(chosenNight));
    const black = tokens(
      looksCss.slice(looksCss.lastIndexOf("\n:root[data-theme='dark'][data-black]")),
      '[data-black]',
    );
    expect(black.get('--texture')).toBe('none');
  });
});

type Rgb = [number, number, number];

/** A token's colour: a hex value, an rgba() tint laid over `under`, or the var() and color-mix() the black block uses. */
function colour(value: string, all: Map<string, string>, under?: Rgb): Rgb {
  const token = /^var\((--[\w-]+)\)$/.exec(value);
  if (token) {
    return colour(all.get(token[1] ?? '') ?? '', all, under);
  }
  const hex = /^#([0-9a-f]{6})$/i.exec(value);
  if (hex) {
    const digits = hex[1] ?? '';
    return [0, 2, 4].map((at) => Number.parseInt(digits.slice(at, at + 2), 16)) as Rgb;
  }
  const mix = /^color-mix\(in srgb, (.+) (\d+)%, (#[0-9a-f]{6}|var\(--[\w-]+\))\)$/i.exec(value);
  if (mix) {
    const [first, second, share] = [colour(mix[1] ?? '', all), colour(mix[3] ?? '', all), Number(mix[2]) / 100];
    return first.map((part, at) => part * share + (second[at] ?? 0) * (1 - share)) as Rgb;
  }
  const tint = /^rgba\((\d+), (\d+), (\d+), ([\d.]+)\)$/.exec(value);
  if (tint && under) {
    const alpha = Number(tint[4]);
    return [1, 2, 3].map((at, index) => Number(tint[at]) * alpha + (under[index] ?? 0) * (1 - alpha)) as Rgb;
  }
  throw new Error(`palettes.test.ts cannot read the colour "${value}"`);
}

/** The WCAG contrast ratio of two colours. */
function contrast(a: Rgb, b: Rgb): number {
  const luminance = (rgb: Rgb) => {
    const [red, green, blue] = rgb.map((part) => {
      const share = part / 255;
      return share <= 0.03928 ? share / 12.92 : ((share + 0.055) / 1.055) ** 2.4;
    }) as Rgb;
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
  };
  const [light, dark] = [luminance(a), luminance(b)].sort((x, y) => y - x) as [number, number];
  return (light + 0.05) / (dark + 0.05);
}

describe('every theme stays readable (2.0 review)', () => {
  const baseDay = tokens(tokensCss.slice(0, tokensCss.indexOf('@media')), ':root');
  const baseNight = tokens(tokensCss.slice(tokensCss.lastIndexOf("\n:root[data-theme='dark']")), ':root');
  const black = tokens(
    palettesCss.slice(palettesCss.lastIndexOf("\n:root[data-theme='dark'][data-black]")),
    '[data-black]',
  );
  // text and what it stands on; a third name is the ground under a see-through tint
  const pairs: [string, string, string?][] = [
    ['text', 'bg'],
    ['text', 'surface'],
    ['text-muted', 'bg'],
    ['text-muted', 'well'], // also a disabled button
    ['text-muted', 'surface'],
    ['accent', 'bg'],
    ['accent', 'surface'],
    ['accent', 'well'],
    ['on-accent', 'accent'],
    ['on-action', 'action'],
    ['on-war', 'war'],
    ['on-badge', 'badge'],
    ['on-bar', 'bar'],
    ['on-bar-muted', 'bar'],
    ['danger', 'bg'],
    ['danger', 'surface'],
    ['danger', 'well'],
    ['danger', 'danger-bg', 'bg'],
    ['danger', 'danger-bg', 'surface'],
    ['on-danger', 'danger'],
    ['success', 'bg'],
    // a won battle is a plain green word since the 2.0 UI review, also on an open or hovered report row
    ['success', 'well'],
    ['success', 'success-bg', 'bg'],
    // the sky header (2.0 frame): the village and the stocks stand on the upper two bands on a desktop
    // and on a phone's slim sky; a phone's stocks lie on the land
    ['text', 'sky-1'],
    ['text', 'sky-2'],
    ['sky-muted', 'sky-1'],
    ['sky-muted', 'sky-2'],
    ['sky-muted', 'bg'],
    ['sky-warn', 'sky-1'],
    ['sky-warn', 'sky-2'],
    ['sky-warn', 'bg'],
    ['sky-caution', 'sky-1'],
    ['sky-caution', 'sky-2'],
    ['sky-caution', 'bg'],
    // the login sky (step 8): the game's name stands on the lower two bands
    ['text', 'sky-3'],
  ];
  const cases = palettes.flatMap((palette) => {
    const { light, chosenDark } = blocks(palette);
    const day = new Map([...baseDay, ...light]);
    const night = new Map([...baseDay, ...baseNight, ...chosenDark]);
    return [
      { name: `${palette} by day`, all: day },
      { name: `${palette} at night`, all: night },
      { name: `${palette} in black`, all: new Map([...night, ...black]) },
    ];
  });

  it.each(cases)('$name: text has 4.5:1 on its ground', ({ all }) => {
    const get = (name: string, under?: Rgb) => colour(all.get(`--color-${name}`) ?? '', all, under);
    const low = pairs
      .map(([text, ground, under]) => {
        const ratio = contrast(get(text), get(ground, under ? get(under) : undefined));
        return `${text} on ${ground}${under ? ` over ${under}` : ''}: ${ratio.toFixed(2)}`;
      })
      .filter((line) => Number(line.slice(line.lastIndexOf(' ') + 1)) < 4.5);
    expect(low).toEqual([]);
  });

  // where you are in a list of choices is a shade of its own (2.0 UI review follow-up: the well was 1.03 to
  // 1.13 : 1 against a sheet at night, so the current village could not be told apart)
  it.each(cases)('$name: the current row shows on a sheet, and its text stays readable', ({ all }) => {
    const get = (name: string, under?: Rgb) => colour(all.get(`--color-${name}`) ?? '', all, under);
    const low: string[] = [];
    for (const ground of ['surface']) {
      const shade = get('current', get(ground));
      const lift = contrast(shade, get(ground));
      if (lift < 1.25) {
        low.push(`current on ${ground}: ${lift.toFixed(2)}`);
      }
      // the row's own soft line takes the text colour there (it falls under 4.5 : 1 at night)
      for (const text of ['text', 'danger']) {
        const ratio = contrast(get(text), shade);
        if (ratio < 4.5) {
          low.push(`${text} on current over ${ground}: ${ratio.toFixed(2)}`);
        }
      }
      // a hovered row: half the shade, its soft line keeps the soft colour
      const hover = shade.map((part, at) => (part + (get(ground)[at] ?? 0)) / 2) as Rgb;
      const muted = contrast(get('text-muted'), hover);
      if (muted < 4.5) {
        low.push(`text-muted on hover over ${ground}: ${muted.toFixed(2)}`);
      }
    }
    expect(low).toEqual([]);
  });

  // the open chat in the rail is an outline in the sign colour: it must show on the land (2.0 UI review
  // follow-up: the bar's colour was 1.1 : 1 on the dark land)
  it.each(cases)('$name: the open chat outline shows on the land', ({ all }) => {
    const ratio = contrast(colour(all.get('--color-badge') ?? '', all), colour(all.get('--color-bg') ?? '', all));
    expect(ratio).toBeGreaterThanOrEqual(1.5);
  });

  // a progress bar's fill is the action colour (rule 4): the empty track must show on the land and the
  // fill must stand out from it, by day, at night and in black
  it.each(cases)('$name: the progress track shows on the land and under the action fill', ({ all }) => {
    const get = (name: string) => colour(all.get(`--color-${name}`) ?? '', all);
    const track = get('track');
    // a dark action colour (Kornfeld, Heide by day) leaves a light land no room for more than 1.7:1 on the land
    const ratios: [string, number, number][] = [
      ['track on bg', contrast(track, get('bg')), 1.7],
      ['action on track', contrast(get('action'), track), 2.4],
    ];
    const low = ratios
      .filter(([, ratio, least]) => ratio < least)
      .map(([name, ratio]) => `${name}: ${ratio.toFixed(2)}`);
    expect(low).toEqual([]);
  });

  // the march bars (2.1): attack, support and return fill on their own track (--march-track, see-through: the
  // track or a dark groove over whatever is under it). Each kind must stand out from it, and the track from its
  // ground, on both grounds the bars lie on: the land (rally point, Now column and sheet) and the Now drawer's
  // sheet (the review found the attack bar at 2.4:1 in the drawer at night, where only the land was checked)
  it.each(cases)('$name: each march colour stands out from its track, on the land and in the drawer', ({ all }) => {
    const get = (name: string) => colour(all.get(`--${name}`) ?? '', all);
    const see = /^color-mix\(in srgb, (var\(--color-track\)|#000000) (\d+)%, transparent\)$/.exec(
      all.get('--march-track') ?? '',
    );
    expect(see).not.toBeNull();
    const [ink, share] = [colour(see?.[1] ?? '', all), Number(see?.[2]) / 100];
    const low = ['bg', 'surface'].flatMap((ground) => {
      const under = get(`color-${ground}`);
      const track = ink.map((part, at) => part * share + (under[at] ?? 0) * (1 - share)) as Rgb;
      return [
        ...['attack', 'support', 'return']
          .map((kind) => [`${kind} on ${ground}`, contrast(get(`march-${kind}`), track), 2.6] as const)
          .filter(([, ratio, least]) => ratio < least),
        ...([[`track on ${ground}`, contrast(track, under), 1.1]] as const).filter(([, ratio, least]) => ratio < least),
      ].map(([what, ratio]) => `${what}: ${ratio.toFixed(2)}`);
    });
    expect(low).toEqual([]);
  });

  // the court race on the Diplomacy page (UI review 2.1, finding 08: the court's fill was 1.14 : 1 on its
  // track in Frost by day): both fills stand out from the race's track at least 3 : 1, read from its CSS
  it.each(cases)('$name: both race bars stand out from their track', ({ all }) => {
    const rule = (name: string) =>
      new RegExp(`\\.${name} \\{[^}]*background: var\\(--color-([\\w-]+)\\)`).exec(diplomacyCss)?.[1] ?? '';
    const get = (name: string) => colour(all.get(`--color-${name}`) ?? '', all);
    const track = get(rule('track'));
    const low = ['fill', 'fillMine']
      .map((fill) => [fill, contrast(get(rule(fill)), track)] as const)
      .filter(([, ratio]) => ratio < 3)
      .map(([fill, ratio]) => `${fill}: ${ratio.toFixed(2)}`);
    expect(rule('track')).not.toBe('');
    expect(low).toEqual([]);
  });
});
