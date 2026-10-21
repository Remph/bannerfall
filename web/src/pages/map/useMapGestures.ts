import { useRef, type PointerEvent, type TouchEvent } from 'react';
import { releaseVelocity, type PointerSample } from '../../game/mapMotion';
import type { Point } from '../../game/mapView';

/** What the map does with the pointer input. Points are CSS pixels from the canvas' top left corner. */
export interface MapGestureCallbacks {
  /** One finger or the mouse dragged by (dx, dy) pixels. */
  onPan: (dx: number, dy: number) => void;
  /** Two fingers moved from `from` to `to`. */
  onPinch: (from: readonly [Point, Point], to: readonly [Point, Point]) => void;
  /** A short touch or click without moving. */
  onTap: (point: Point, pointerType: string) => void;
  /** The mouse moved over the map without a button pressed; null = it left or a drag started. */
  onHover: (point: Point | null) => void;
  /**
   * A finger or the mouse went down: a running glide or drift stops (1.3). True when one was
   * running: that touch only stops the map, it is no tap on a village.
   */
  onDown?: () => boolean;
  /** A one-finger drag was let go while still moving, at this speed in pixels per millisecond (1.3). */
  onFling?: (velocity: Point) => void;
}

interface Gesture {
  /** Last position of every finger / the mouse on the canvas, by pointer id. */
  pointers: Map<number, Point>;
  start: Point;
  pointerType: string;
  /** Moved farther than a tap allows (or used two fingers): this is no tap any more. */
  moved: boolean;
  /** Two fingers were down at some point: the map never drifts on after a pinch. */
  pinched: boolean;
  /** The last positions of a one-finger drag, for its speed when it is let go. */
  samples: PointerSample[];
}

/** Enough samples for the last 100 ms of a drag (mapMotion.releaseVelocity). */
const MAX_SAMPLES = 12;

/** How far a tap may wander before it becomes a drag: a finger is less precise than a mouse. */
function tapSlop(pointerType: string): number {
  return pointerType === 'mouse' ? 4 : 10;
}

/** The first two fingers (a Map keeps the order in which they touched down). */
function firstTwo(pointers: Map<number, Point>): [Point, Point] | undefined {
  const [first, second] = [...pointers.values()];
  return first !== undefined && second !== undefined ? [first, second] : undefined;
}

function position(event: PointerEvent<HTMLElement>): Point {
  const rect = event.currentTarget.getBoundingClientRect();
  return { x: event.clientX - rect.left, y: event.clientY - rect.top };
}

/**
 * Touch-first map input with Pointer Events (05-frontend §6): drag pans, two fingers pinch-zoom,
 * a tap selects. The same code serves mouse, pen and touch. The canvas needs `touch-action: none`,
 * otherwise the browser scrolls or zooms the page instead.
 *
 * Spread the returned handlers onto the canvas: `<canvas {...gestures} />`.
 */
export function useMapGestures(callbacks: MapGestureCallbacks) {
  // A ref, not state: the gesture changes on every pointer event and nothing renders from it.
  const gesture = useRef<Gesture | null>(null);
  // The last gesture ended as a tap: only then did the browser focus the canvas (see onTouchEnd).
  const tapped = useRef(false);

  function onPointerDown(event: PointerEvent<HTMLElement>) {
    if (event.pointerType === 'mouse' && event.button !== 0) {
      return; // only the main mouse button drags
    }
    tapped.current = false;
    // Keep receiving this pointer's events even when it leaves the canvas during a drag.
    event.currentTarget.setPointerCapture(event.pointerId);
    const point = position(event);
    const current = gesture.current ?? {
      pointers: new Map(),
      start: point,
      pointerType: event.pointerType,
      moved: false,
      pinched: false,
      samples: [],
    };
    current.pointers.set(event.pointerId, point);
    if (current.pointers.size > 1) {
      current.moved = true; // a second finger: pinch, never a tap
      current.pinched = true;
    }
    gesture.current = current;
    if (callbacks.onDown?.() === true) {
      current.moved = true;
    }
    callbacks.onHover(null);
  }

  function onPointerMove(event: PointerEvent<HTMLElement>) {
    const point = position(event);
    const current = gesture.current;
    const last = current?.pointers.get(event.pointerId);
    if (current === null || last === undefined) {
      if (event.pointerType === 'mouse') {
        callbacks.onHover(point);
      }
      return;
    }
    if (current.pointers.size >= 2) {
      // Pinch with the first two fingers (a third one changes nothing).
      const before = firstTwo(current.pointers);
      current.pointers.set(event.pointerId, point);
      const after = firstTwo(current.pointers);
      if (before !== undefined && after !== undefined) {
        callbacks.onPinch(before, after);
      }
      return;
    }
    current.pointers.set(event.pointerId, point);
    current.samples.push({ t: event.timeStamp, x: point.x, y: point.y });
    if (current.samples.length > MAX_SAMPLES) {
      current.samples.shift();
    }
    if (!current.moved) {
      if (Math.hypot(point.x - current.start.x, point.y - current.start.y) < tapSlop(current.pointerType)) {
        return; // still a tap
      }
      current.moved = true;
      event.currentTarget.dataset.dragging = 'true'; // CSS: grabbing cursor
      callbacks.onPan(point.x - current.start.x, point.y - current.start.y); // catch up the slop at once
      return;
    }
    callbacks.onPan(point.x - last.x, point.y - last.y);
  }

  function end(event: PointerEvent<HTMLElement>, completed: boolean) {
    const current = gesture.current;
    if (current === null || !current.pointers.delete(event.pointerId)) {
      return;
    }
    if (current.pointers.size > 0) {
      return; // one of two fingers lifted: the other one goes on panning
    }
    gesture.current = null;
    delete event.currentTarget.dataset.dragging;
    if (completed && !current.moved) {
      tapped.current = true;
      callbacks.onTap(position(event), current.pointerType);
    }
    if (completed && current.moved && !current.pinched) {
      const velocity = releaseVelocity(current.samples, event.timeStamp);
      if (velocity !== null) {
        callbacks.onFling?.(velocity);
      }
    }
  }

  return {
    onPointerDown,
    onPointerMove,
    onPointerUp: (event: PointerEvent<HTMLElement>) => end(event, true),
    // The browser took the pointer away (e.g. a system gesture): never a tap.
    onPointerCancel: (event: PointerEvent<HTMLElement>) => end(event, false),
    onPointerLeave: (event: PointerEvent<HTMLElement>) => {
      if (event.pointerType === 'mouse' && gesture.current === null) {
        callbacks.onHover(null);
      }
    },
    // After a finger lifts, the browser sends mousedown, mouseup and click to whatever is under it by
    // then: a tap opens the village panel on pointerup, so that click hit the panel's button under the
    // finger and sent a farm attack (1.6). Cancelling touchend drops them; the map only needs the
    // pointer events. The focus the mousedown gave the canvas after a tap (not after a drag: the go-to
    // field keeps it while the map pans) it gets here instead.
    onTouchEnd: (event: TouchEvent<HTMLElement>) => {
      if (event.cancelable) {
        event.preventDefault();
        if (tapped.current) {
          event.currentTarget.focus({ preventScroll: true, focusVisible: false });
        }
      }
    },
  };
}
