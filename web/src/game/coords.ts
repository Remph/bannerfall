/**
 * Map coordinates (02-game-rules §2): every field has whole-number coordinates (x|y) with
 * 0 ≤ x, y < mapSize. Shown as "(48|52)" (formatCoords in format.ts).
 */
export interface Coords {
  x: number;
  y: number;
}

// "48|52", "(48|52)", "48 52", "48,52", "48.52", "48/52" … – up to 4 digits each.
const COORDS_PATTERN = /^\(?\s*(\d{1,4})(?:\s*[|,.;:/]\s*|\s+)(\d{1,4})\s*\)?$/;

/**
 * Coordinates the way players type them: "48|52", also with brackets, a space, a comma or a dot
 * between the numbers (phone number keyboards have no "|"). Anything else → undefined. Doesn't
 * check the map size, see `isOnMap`.
 */
export function parseCoords(text: string): Coords | undefined {
  const match = COORDS_PATTERN.exec(text.trim());
  // With noUncheckedIndexedAccess, match[1] is `string | undefined`, although the pattern guarantees it.
  const x = match?.[1];
  const y = match?.[2];
  if (x === undefined || y === undefined) {
    return undefined;
  }
  return { x: Number(x), y: Number(y) };
}

export function isOnMap(coords: Coords, mapSize: number): boolean {
  return coords.x >= 0 && coords.y >= 0 && coords.x < mapSize && coords.y < mapSize;
}

/** Distance in fields, as the server computes it (travel times use it): √(Δx² + Δy²). */
export function distance(from: Coords, to: Coords): number {
  return Math.hypot(from.x - to.x, from.y - to.y);
}
