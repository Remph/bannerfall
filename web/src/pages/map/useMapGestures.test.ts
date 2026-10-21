// @vitest-environment jsdom
import { renderHook } from '@testing-library/react';
import type { PointerEvent, TouchEvent } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { useMapGestures, type MapGestureCallbacks } from './useMapGestures';

const canvas = { setPointerCapture: () => {}, getBoundingClientRect: () => ({ left: 0, top: 0 }), dataset: {} };

function pointer(x: number, y: number, timeStamp = 0): PointerEvent<HTMLElement> {
  return {
    pointerType: 'touch',
    button: 0,
    pointerId: 1,
    clientX: x,
    clientY: y,
    timeStamp,
    currentTarget: canvas,
  } as unknown as PointerEvent<HTMLElement>;
}

function setup(onDown: () => boolean) {
  const callbacks: MapGestureCallbacks = {
    onPan: vi.fn(),
    onPinch: vi.fn(),
    onTap: vi.fn(),
    onHover: vi.fn(),
    onDown,
    onFling: vi.fn(),
  };
  const { result } = renderHook(() => useMapGestures(callbacks));
  return { callbacks, gestures: result.current };
}

describe('useMapGestures', () => {
  it('taps when the map stood still', () => {
    const { callbacks, gestures } = setup(() => false);
    gestures.onPointerDown(pointer(50, 50));
    gestures.onPointerUp(pointer(51, 50));
    expect(callbacks.onTap).toHaveBeenCalledOnce();
  });

  it('only stops a gliding or drifting map: that touch is no tap on a village', () => {
    const { callbacks, gestures } = setup(() => true);
    gestures.onPointerDown(pointer(50, 50));
    gestures.onPointerUp(pointer(51, 50));
    expect(callbacks.onTap).not.toHaveBeenCalled();
    expect(callbacks.onFling).not.toHaveBeenCalled();
  });

  it('lets a quick drag drift on, not one held still before it was let go', () => {
    const quick = setup(() => false);
    quick.gestures.onPointerDown(pointer(200, 50, 0));
    for (let i = 1; i <= 5; i++) {
      quick.gestures.onPointerMove(pointer(200 - i * 40, 50, i * 10));
    }
    quick.gestures.onPointerUp(pointer(0, 50, 55));
    expect(quick.callbacks.onFling).toHaveBeenCalledOnce();

    const held = setup(() => false);
    held.gestures.onPointerDown(pointer(200, 50, 0));
    for (let i = 1; i <= 5; i++) {
      held.gestures.onPointerMove(pointer(200 - i * 40, 50, i * 10));
    }
    held.gestures.onPointerUp(pointer(0, 50, 200));
    expect(held.callbacks.onFling).not.toHaveBeenCalled();
  });

  it("cancels the finger's lift, so the browser sends no click to the panel that tap opened (1.6)", () => {
    const { callbacks, gestures } = setup(() => false);
    const focus = vi.fn();
    const touchEnd = { cancelable: true, preventDefault: vi.fn(), currentTarget: { focus } };
    gestures.onPointerDown(pointer(50, 50));
    gestures.onPointerUp(pointer(50, 50));
    gestures.onTouchEnd(touchEnd as unknown as TouchEvent<HTMLElement>);

    expect(callbacks.onTap).toHaveBeenCalledOnce();
    expect(touchEnd.preventDefault).toHaveBeenCalledOnce();
    // the focus the cancelled mousedown would have given the map
    expect(focus).toHaveBeenCalledWith({ preventScroll: true, focusVisible: false });
  });

  it('leaves the focus where it is after a drag, e.g. in the go-to field (1.6)', () => {
    const { gestures } = setup(() => false);
    const focus = vi.fn();
    const touchEnd = { cancelable: true, preventDefault: vi.fn(), currentTarget: { focus } };
    gestures.onPointerDown(pointer(50, 50));
    gestures.onPointerMove(pointer(50, 90));
    gestures.onPointerUp(pointer(50, 90));
    gestures.onTouchEnd(touchEnd as unknown as TouchEvent<HTMLElement>);

    expect(touchEnd.preventDefault).toHaveBeenCalledOnce();
    expect(focus).not.toHaveBeenCalled();
  });
});
