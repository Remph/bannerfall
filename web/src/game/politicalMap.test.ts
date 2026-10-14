import { describe, expect, it } from 'vitest';
import { parseMapLayer, guildColor, guildsInView } from './politicalMap';

describe('parseMapLayer', () => {
  it('knows the guild layer and falls back to the owner colours', () => {
    expect(parseMapLayer('guild')).toBe('guild');
    expect(parseMapLayer('owner')).toBe('owner');
    expect(parseMapLayer(null)).toBe('owner');
    expect(parseMapLayer('whatever')).toBe('owner');
  });
});

describe('guildColor', () => {
  it('is the same for the same guild and differs between neighbouring ids', () => {
    expect(guildColor(7, 'light')).toBe(guildColor(7, 'light'));
    const colours = new Set([1, 2, 3, 4, 5, 6, 7, 8].map((id) => guildColor(id, 'light')));
    expect(colours.size).toBe(8);
  });

  const hueOf = (colour: string) => Number(/^hsl\((\d+) /.exec(colour)?.[1]);

  it('stays off the greens of the grass and the purple of the NPC lords', () => {
    for (let id = 1; id <= 500; id++) {
      const hue = hueOf(guildColor(id, 'light'));
      expect(hue <= 60 || hue >= 170).toBe(true);
      expect(hue <= 262 || hue >= 300).toBe(true);
    }
  });

  it('keeps the first few guilds at least 15 degrees apart on the colour wheel', () => {
    const hues = [1, 2, 3, 4, 5, 6, 7, 8].map((id) => hueOf(guildColor(id, 'light')));
    for (const [i, a] of hues.entries()) {
      for (const b of hues.slice(i + 1)) {
        const distance = Math.abs(a - b);
        expect(Math.min(distance, 360 - distance)).toBeGreaterThanOrEqual(15);
      }
    }
  });

  it('is lighter in the dark theme', () => {
    expect(guildColor(3, 'light')).toContain('40%');
    expect(guildColor(3, 'dark')).toContain('62%');
  });
});

describe('guildsInView', () => {
  it('counts villages per guild, most first, ignoring barbarians and players without a guild', () => {
    const entries = guildsInView([
      { guildId: 2, guildTag: 'WOLF' },
      { guildId: 1, guildTag: 'ALPHA' },
      { guildId: 2, guildTag: 'WOLF' },
      { guildId: null, guildTag: null },
      {},
      { guildId: 3, guildTag: 'BETA' },
    ]);
    // WOLF has two villages; ALPHA and BETA one each, so they go by tag
    expect(entries).toEqual([
      { guildId: 2, tag: 'WOLF', villages: 2 },
      { guildId: 1, tag: 'ALPHA', villages: 1 },
      { guildId: 3, tag: 'BETA', villages: 1 },
    ]);
  });
});
