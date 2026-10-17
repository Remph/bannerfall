// @vitest-environment jsdom
import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { COUNT_MS } from '../game/motion';
import { AFTERGLOW_MS, useCountedValue } from './useCountedValue';

describe('useCountedValue', () => {
  beforeEach(() => {
    vi.useFakeTimers({
      toFake: ['requestAnimationFrame', 'cancelAnimationFrame', 'setTimeout', 'clearTimeout', 'performance'],
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  function counted(target: number) {
    return renderHook(({ value }) => useCountedValue(value, 3600), { initialProps: { value: target } });
  }

  it('follows the growth with the production at once', () => {
    const { result, rerender } = counted(500);
    rerender({ value: 501 });
    expect(result.current).toEqual({ value: 501, direction: null });
  });

  it('counts up to a gain and glows a moment after', () => {
    const { result, rerender } = counted(500);
    rerender({ value: 1500 });
    expect(result.current.direction).toBe('up');
    act(() => vi.advanceTimersByTime(COUNT_MS / 2));
    expect(result.current.value).toBeGreaterThan(500);
    expect(result.current.value).toBeLessThan(1500);
    act(() => vi.advanceTimersByTime(COUNT_MS));
    expect(result.current).toEqual({ value: 1500, direction: 'up' });
    act(() => vi.advanceTimersByTime(AFTERGLOW_MS));
    expect(result.current.direction).toBeNull();
  });

  it('counts down to a payment', () => {
    const { result, rerender } = counted(1500);
    rerender({ value: 300 });
    expect(result.current.direction).toBe('down');
    act(() => vi.advanceTimersByTime(COUNT_MS * 2));
    expect(result.current.value).toBe(300);
  });

  it('ends at a value that changed while counting', () => {
    const { result, rerender } = counted(500);
    rerender({ value: 1500 });
    act(() => vi.advanceTimersByTime(COUNT_MS / 2));
    rerender({ value: 1502 });
    act(() => vi.advanceTimersByTime(COUNT_MS));
    expect(result.current.value).toBe(1502);
  });

  it('switches at once when the device asks for reduced motion', () => {
    vi.stubGlobal('matchMedia', () => ({ matches: true }));
    const { result, rerender } = counted(500);
    rerender({ value: 1500 });
    expect(result.current).toEqual({ value: 1500, direction: null });
  });

  it('stops counting when it unmounts', () => {
    const { rerender, unmount } = counted(500);
    rerender({ value: 1500 });
    unmount();
    expect(vi.getTimerCount()).toBe(0);
  });
});
