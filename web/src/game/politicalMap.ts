/*
 * The political layer of the overview map (M15c, 02-game-rules §16.2): villages coloured by guild.
 * Pure helpers; the drawing is in pages/map/overviewDraw.ts.
 */

/** What colours the overview: the owner kinds as before, or the owner's guild. */
export type MapLayer = 'owner' | 'guild';

export function parseMapLayer(value: string | null): MapLayer {
  return value === 'guild' ? 'guild' : 'owner';
}

/**
 * Hues a guild may get, in degrees, as [from, length] arcs of the colour wheel. Left out on purpose:
 * the greens (60–170°), which vanish on the grass, and the purples (262–300°), which are the NPC
 * lords' colour on this map.
 */
const GUILD_HUE_ARCS: readonly (readonly [number, number])[] = [
  [170, 92], // teal to blue
  [300, 120], // magenta, red, orange, amber (runs past 360°)
];
const GUILD_HUE_SPAN = GUILD_HUE_ARCS.reduce((sum, [, length]) => sum + length, 0);
const GOLDEN_RATIO_STEP = 0.6180339887;

/**
 * The colour of a guild: a golden-ratio step per guild id along the allowed hues, so guilds with
 * neighbouring ids still look different, and the same guild keeps its colour for everybody and on
 * every visit. Lightness depends on the theme (readable on the grass in both).
 */
export function guildColor(guildId: number, theme: 'light' | 'dark'): string {
  let offset = ((((guildId * GOLDEN_RATIO_STEP) % 1) + 1) % 1) * GUILD_HUE_SPAN;
  let hue = 0;
  for (const [from, length] of GUILD_HUE_ARCS) {
    if (offset < length) {
      hue = Math.round(from + offset) % 360;
      break;
    }
    offset -= length;
  }
  return theme === 'dark' ? `hsl(${hue} 68% 62%)` : `hsl(${hue} 62% 40%)`;
}

export interface GuildInView {
  guildId: number;
  tag: string;
  villages: number;
}

/** The guilds with villages among these, the ones with the most villages first (ties by tag). */
export function guildsInView(
  villages: readonly { guildId?: number | null; guildTag?: string | null }[],
): GuildInView[] {
  const byId = new Map<number, GuildInView>();
  for (const village of villages) {
    if (village.guildId == null || village.guildTag == null) {
      continue;
    }
    const entry = byId.get(village.guildId);
    if (entry === undefined) {
      byId.set(village.guildId, { guildId: village.guildId, tag: village.guildTag, villages: 1 });
    } else {
      entry.villages += 1;
    }
  }
  return [...byId.values()].sort((a, b) => b.villages - a.villages || a.tag.localeCompare(b.tag));
}
