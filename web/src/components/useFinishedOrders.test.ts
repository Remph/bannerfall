// @vitest-environment jsdom
import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { FINISH_MS, useFinishedOrders } from './useFinishedOrders';

const now = Date.parse('2026-10-16T12:00:00Z');
const at = (id: number, endInMs: number) => ({ id, endAt: new Date(now + endInMs).toISOString() });

describe('useFinishedOrders', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date', 'setTimeout', 'clearTimeout'] });
    vi.setSystemTime(now);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  function shownQueue(scope: number, queue: Array<{ id: number; endAt: string }>) {
    return renderHook(({ scope, queue }) => useFinishedOrders(scope, queue), { initialProps: { scope, queue } });
  }

  const lines = (shown: Array<{ order: { id: number }; finished: boolean; entering: boolean }>) =>
    shown.map(({ order, finished, entering }) => `${order.id}${finished ? ' done' : ''}${entering ? ' enters' : ''}`);

  it('keeps a finished order for a moment and lets the next one enter', () => {
    const { result, rerender } = shownQueue(1, [at(1, -1000), at(2, 60_000)]);
    rerender({ scope: 1, queue: [at(2, 60_000)] });
    expect(lines(result.current)).toEqual(['1 done', '2 enters']);
    act(() => vi.advanceTimersByTime(FINISH_MS));
    expect(lines(result.current)).toEqual(['2']);
  });

  it('drops a cancelled order at once', () => {
    const { result, rerender } = shownQueue(1, [at(1, 30_000), at(2, 60_000)]);
    rerender({ scope: 1, queue: [at(2, 60_000)] });
    expect(lines(result.current)).toEqual(['2']);
  });

  it('keeps the last finished order before the queue shows empty', () => {
    const { result, rerender } = shownQueue(1, [at(1, -1000)]);
    rerender({ scope: 1, queue: [] });
    expect(lines(result.current)).toEqual(['1 done']);
    act(() => vi.advanceTimersByTime(FINISH_MS));
    expect(result.current).toEqual([]);
  });

  it('starts afresh for another village', () => {
    const { result, rerender } = shownQueue(1, [at(1, -1000)]);
    rerender({ scope: 2, queue: [at(5, 60_000)] });
    expect(lines(result.current)).toEqual(['5']);
  });

  it('shows no finished orders when the device asks for reduced motion', () => {
    vi.stubGlobal('matchMedia', () => ({ matches: true }));
    const { result, rerender } = shownQueue(1, [at(1, -1000), at(2, 60_000)]);
    rerender({ scope: 1, queue: [at(2, 60_000)] });
    expect(lines(result.current)).toEqual(['2']);
  });
});
