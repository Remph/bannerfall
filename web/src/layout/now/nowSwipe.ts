/**
 * The phone's pull-down sheet (2.0): the numbers of the gesture, apart from the DOM so they can be
 * tested. The sheet is the sky's land pulled down; a drag on the sky moves its edge `y` pixels down
 * (0 closed, `height` fully open).
 */

/** A finger has to move this far before the gesture starts, so a tap on the village switcher stays a tap. */
export const DEAD_ZONE = 10;
/** And mostly sideways movement is not a pull. */
const VERTICAL_DOMINANCE = 1.5;
/** px per ms: faster than this is a flick that opens or closes whatever the distance. */
export const FLICK_SPEED = 0.35;

/**
 * Whether a move of (`dx`, `dy`) since the finger went down starts a drag: past the dead zone,
 * mostly vertical, and in the direction that does something (down opens, up closes).
 */
export function startsDrag(dx: number, dy: number, open: boolean): boolean {
  if (Math.abs(dy) < DEAD_ZONE || Math.abs(dy) < Math.abs(dx) * VERTICAL_DOMINANCE) {
    return false;
  }
  return dy > 0 !== open;
}

/** The edge of the sheet while dragging: where it was, plus the finger's way, kept inside 0…height. */
export function edgeAt(base: number, fingerDy: number, height: number): number {
  return Math.max(0, Math.min(height, base + fingerDy));
}

/**
 * Whether the sheet is open after the finger lets go. `edge` is where the sheet's edge is, `velocity`
 * the finger's speed in px per ms (positive = down, 0 when it rested before letting go).
 * Opening needs a bit of a pull (30% of the height, at most 110 px) or a flick down; closing needs
 * a quarter of the height (at most 60 px) up or a flick up.
 */
export function opensOnRelease(wasOpen: boolean, edge: number, height: number, velocity: number): boolean {
  if (wasOpen) {
    return !(velocity < -FLICK_SPEED || height - edge > Math.min(height * 0.25, 60));
  }
  return velocity > FLICK_SPEED || edge > Math.min(height * 0.3, 110);
}

/** How far the content has rolled out of the sky (0 = tucked in, 1 = in place): the parallax of the rows. */
export function reveal(edge: number, height: number): number {
  return height > 0 ? Math.max(0, Math.min(1, edge / height)) : 0;
}
