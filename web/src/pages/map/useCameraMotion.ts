import { useCallback, useEffect, useLayoutEffect, useRef, type Dispatch, type SetStateAction } from 'react';
import { driftStep, glideCamera, glidePlan, ZOOM_GLIDE_MS } from '../../game/mapMotion';
import { clampCamera, panBy, type Camera, type Point } from '../../game/mapView';
import { prefersReducedMotion } from '../../game/motion';

interface Glide {
  from: Camera;
  to: Camera;
  startedAt: number;
  ms: number;
  lift: number;
}

/**
 * Moves the map's camera frame by frame (UI review 1.3, game/mapMotion.ts): `glideTo` for "go to"
 * and centring, `zoomTo` for the + and − buttons, `drift` after a quick drag. Any direct move
 * (drag, wheel, pinch, keys) calls `stop` first. With reduced motion the camera jumps, as before.
 */
export function useCameraMotion(camera: Camera, setCamera: Dispatch<SetStateAction<Camera>>, mapSize: number) {
  // the camera as last drawn, for a glide to start from (read in event handlers, not while rendering)
  const shown = useRef(camera);
  useLayoutEffect(() => {
    shown.current = camera;
  }, [camera]);
  const frame = useRef<number | null>(null);
  const glide = useRef<Glide | null>(null);

  /** Stops a running glide or drift; true if one was running. */
  const stop = useCallback(() => {
    const running = frame.current;
    if (running !== null) {
      cancelAnimationFrame(running);
      frame.current = null;
    }
    glide.current = null;
    return running !== null;
  }, []);
  useEffect(
    () => () => {
      stop();
    },
    [stop],
  );

  const run = useCallback(
    (next: Glide) => {
      stop();
      if (prefersReducedMotion()) {
        setCamera(next.to);
        return;
      }
      glide.current = next;
      const step = (now: number) => {
        const current = glide.current;
        if (current === null) {
          return;
        }
        const progress = (now - current.startedAt) / current.ms;
        setCamera(clampCamera(glideCamera(current.from, current.to, progress, current.lift), mapSize));
        if (progress < 1) {
          frame.current = requestAnimationFrame(step);
        } else {
          frame.current = null;
          glide.current = null;
        }
      };
      frame.current = requestAnimationFrame(step);
    },
    [mapSize, setCamera, stop],
  );

  return {
    stop,
    /** Where the camera is going: the end of a running glide, otherwise where it is. */
    target: useCallback(() => glide.current?.to ?? shown.current, []),
    /** Glide to `to` ("go to", centring); long jumps zoom out on the way. */
    glideTo: useCallback(
      (to: Camera) => run({ from: shown.current, to, startedAt: performance.now(), ...glidePlan(shown.current, to) }),
      [run],
    ),
    /** Zoom smoothly to `to` (the + and − buttons). */
    zoomTo: useCallback(
      (to: Camera) => run({ from: shown.current, to, startedAt: performance.now(), ms: ZOOM_GLIDE_MS, lift: 0 }),
      [run],
    ),
    /**
     * Changes where a running glide ends (the village panel moves the map so it doesn't cover the
     * village), or the camera itself when none runs.
     */
    adjust: useCallback(
      (change: (camera: Camera) => Camera) => {
        const current = glide.current;
        if (current !== null) {
          glide.current = { ...current, to: change(current.to) };
        } else {
          setCamera(change);
        }
      },
      [setCamera],
    ),
    /** Let the map drift on after a drag released at `velocity` (pixels per millisecond). */
    drift: useCallback(
      (velocity: Point) => {
        stop();
        if (prefersReducedMotion()) {
          return;
        }
        let speed = velocity;
        let last = performance.now();
        const step = (now: number) => {
          const next = driftStep(speed, now - last);
          last = now;
          if (next === null) {
            frame.current = null;
            return;
          }
          speed = next.velocity;
          setCamera((current) => panBy(current, next.dx, next.dy, mapSize));
          frame.current = requestAnimationFrame(step);
        };
        frame.current = requestAnimationFrame(step);
      },
      [mapSize, setCamera, stop],
    ),
  };
}
