import { useRef, type PointerEvent } from 'react';
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
}

interface Gesture {
  /** Last position of every finger / the mouse on the canvas, by pointer id. */
  pointers: Map<number, Point>;
  start: Point;
  pointerType: string;
  /** Moved farther than a tap allows (or used two fingers): this is no tap any more. */
  moved: boolean;
}

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

  function onPointerDown(event: PointerEvent<HTMLElement>) {
    if (event.pointerType === 'mouse' && event.button !== 0) {
      return; // only the main mouse button drags
    }
    // Keep receiving this pointer's events even when it leaves the canvas during a drag.
    event.currentTarget.setPointerCapture(event.pointerId);
    const point = position(event);
    const current = gesture.current ?? {
      pointers: new Map(),
      start: point,
      pointerType: event.pointerType,
      moved: false,
    };
    current.pointers.set(event.pointerId, point);
    if (current.pointers.size > 1) {
      current.moved = true; // a second finger: pinch, never a tap
    }
    gesture.current = current;
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
      callbacks.onTap(position(event), current.pointerType);
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
  };
}
