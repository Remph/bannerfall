import type { Camera, Point } from './mapView';
import { easeInOut } from './motion';

/**
 * Map motion (UI review 1.3): the zoom buttons zoom smoothly, "go to" and "centre on my village"
 * glide to the target (zooming out a little on long jumps), and a quick drag lets the map drift on
 * and slow down. The mouse wheel, pinching and the keys stay direct. Pure helpers; the page runs
 * them frame by frame (useCameraMotion).
 */

/** How long the + and − buttons take to zoom. */
export const ZOOM_GLIDE_MS = 260;
/** Longer jumps take longer, up to this. */
const GLIDE_MAX_MS = 1100;
const GLIDE_BASE_MS = 380;
const GLIDE_MS_PER_FIELD = 12;
/** From this many fields on, a glide zooms out on the way, by up to LIFT of the tile size. */
const LIFT_FROM_FIELDS = 15;
const LIFT = 0.35;

/** How long a glide from `from` to `to` takes, and how far it zooms out on the way (0 = not). */
export function glidePlan(from: Camera, to: Camera): { ms: number; lift: number } {
  const fields = Math.hypot(to.x - from.x, to.y - from.y);
  return {
    ms: Math.min(GLIDE_MAX_MS, GLIDE_BASE_MS + fields * GLIDE_MS_PER_FIELD),
    lift: fields > LIFT_FROM_FIELDS ? LIFT : 0,
  };
}

/** The camera `progress` (0 to 1) of the way through a glide; not clamped to the map. */
export function glideCamera(from: Camera, to: Camera, progress: number, lift: number): Camera {
  if (progress >= 1) {
    return to;
  }
  const k = easeInOut(progress);
  return {
    x: from.x + (to.x - from.x) * k,
    y: from.y + (to.y - from.y) * k,
    tile: (from.tile + (to.tile - from.tile) * k) * (1 - lift * Math.sin(Math.PI * k)),
  };
}

/** A pointer position during a drag, with its event time in milliseconds. */
export interface PointerSample {
  t: number;
  x: number;
  y: number;
}

/** The speed of a drag is measured over its last this many milliseconds… */
const RELEASE_WINDOW_MS = 100;
/** …and is none when the pointer stood still this long before it was lifted. */
const RELEASE_STILL_MS = 60;
/** Slower than this (pixels per millisecond) at the release, the map stops at once. */
const FLING_MIN_SPEED = 0.15;
/** The drift ends below this speed. */
const FLING_STOP_SPEED = 0.02;
/** Per millisecond the drift keeps this share of its speed. */
const FLING_FRICTION = 0.9955;
/** A frame step counts as this long at most (a frame that came late doesn't jump). */
const MAX_STEP_MS = 32;

/**
 * The speed (pixels per millisecond) a drag had when the pointer was lifted at `releasedAt`, or
 * null when the map should not drift on: it stood still before, or moved too slowly.
 */
export function releaseVelocity(samples: readonly PointerSample[], releasedAt: number): Point | null {
  const last = samples[samples.length - 1];
  if (last === undefined || releasedAt - last.t >= RELEASE_STILL_MS) {
    return null;
  }
  const first = samples.find((sample) => last.t - sample.t <= RELEASE_WINDOW_MS) ?? last;
  const ms = last.t - first.t;
  if (ms <= 0) {
    return null;
  }
  const velocity = { x: (last.x - first.x) / ms, y: (last.y - first.y) / ms };
  return Math.hypot(velocity.x, velocity.y) >= FLING_MIN_SPEED ? velocity : null;
}

/**
 * One frame of the drift: how far to pan in `elapsedMs`, and the speed after it; null once the
 * drift has come to a stop.
 */
export function driftStep(velocity: Point, elapsedMs: number): { dx: number; dy: number; velocity: Point } | null {
  if (Math.hypot(velocity.x, velocity.y) <= FLING_STOP_SPEED) {
    return null;
  }
  const ms = Math.min(MAX_STEP_MS, Math.max(0, elapsedMs));
  const keep = FLING_FRICTION ** ms;
  return { dx: velocity.x * ms, dy: velocity.y * ms, velocity: { x: velocity.x * keep, y: velocity.y * keep } };
}
