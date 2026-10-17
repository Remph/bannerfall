/* Sizes of the name plates under the buildings (Plate.tsx), also used by the layout tests. */

/** A plate's height and its padding left and right of the text, in font sizes. */
export const PLATE_HEIGHT = 1.65;
export const PLATE_PAD = 0.6;

/**
 * About how wide a plate's text is before it has been measured (and in tests, which can't measure):
 * a little wider than the body font's semibold letters (measured in Chrome), so a first frame never
 * overflows.
 */
export function estimateTextWidth(text: string, font: number): number {
  let width = 0;
  for (const letter of text) {
    width += letter === letter.toUpperCase() && letter !== letter.toLowerCase() ? 0.56 : 0.5;
  }
  return width * font;
}

/** About how wide the level's text is ("12", "–", "12 → 13") before it has been measured. */
export function estimateLevelWidth(text: string, font: number): number {
  return text.length * font * 0.5;
}

/** The level pill: its text plus room left and right. */
export function pillWidth(levelWidth: number, font: number): number {
  return levelWidth + font * 0.9;
}

/** The plate's width from the measured or estimated widths of the name and the level. */
export function plateWidth(nameWidth: number, levelWidth: number, font: number): number {
  return nameWidth + pillWidth(levelWidth, font) + font * PLATE_PAD * 2;
}

/** The pill's text: the level, or a dash for not built. */
export function levelLabel(level: number): string {
  return level === 0 ? '–' : String(level);
}
