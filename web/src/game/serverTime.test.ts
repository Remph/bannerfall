import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  gameTime,
  setFrozenAt,
  createServerClock,
  currentTick,
  median,
  msUntilNextSecond,
  secondsUntil,
  subscribeToTicks,
} from './serverTime';

describe('median', () => {
  it('handles empty, odd and even lists', () => {
    expect(median([])).toBe(0);
    expect(median([5, 1, 3])).toBe(3);
    expect(median([4, 1, 3, 2])).toBe(2.5);
  });
});

describe('serverClock', () => {
  const local = Date.parse('2026-10-02T20:00:00Z');

  it('is 0 before the first response', () => {
    expect(createServerClock().offsetMs()).toBe(0);
  });

  it('measures how far the server is ahead', () => {
    const clock = createServerClock();
    clock.record('2026-10-02T20:00:02Z', local);
    expect(clock.offsetMs()).toBe(2000);
  });

  it('ignores missing or broken headers', () => {
    const clock = createServerClock();
    clock.record(null, local);
    clock.record('not a date', local);
    expect(clock.offsetMs()).toBe(0);
  });

  it('uses the median of the last five samples', () => {
    const clock = createServerClock();
    const header = '2026-10-02T20:00:00Z';
    const serverMs = Date.parse(header);
    clock.record(header, serverMs - 60_000); // the oldest sample, pushed out by the next five
    for (const offset of [1000, 2000, 3000, 30_000, 2000]) {
      clock.record(header, serverMs - offset);
    }
    expect(clock.offsetMs()).toBe(2000);
  });
});

describe('gameTime', () => {
  afterEach(() => setFrozenAt(null));

  it('is the time itself while the world runs', () => {
    expect(gameTime(5_000)).toBe(5_000);
  });

  it('stands still at the end once the world has ended', () => {
    setFrozenAt(3_000);
    expect(gameTime(5_000)).toBe(3_000);
    expect(gameTime(2_000)).toBe(2_000);
  });
});

describe('secondsUntil', () => {
  it('rounds up, so the last second still shows 1', () => {
    expect(secondsUntil(10_000, 0)).toBe(10);
    expect(secondsUntil(10_000, 9_001)).toBe(1);
    expect(secondsUntil(10_000, 9_999)).toBe(1);
  });

  it('is 0 from the moment the time is reached', () => {
    expect(secondsUntil(10_000, 10_000)).toBe(0);
    expect(secondsUntil(10_000, 12_500)).toBe(0);
  });
});

describe('msUntilNextSecond', () => {
  it('waits until just after the next full second', () => {
    expect(msUntilNextSecond(Date.parse('2026-10-03T12:00:00.250Z'))).toBe(755);
    expect(msUntilNextSecond(Date.parse('2026-10-03T12:00:00.000Z'))).toBe(1005);
  });
});

describe('subscribeToTicks', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('ticks all listeners together on the full second and stops when nobody listens', () => {
    vi.useFakeTimers();
    vi.setSystemTime(Date.parse('2026-10-03T12:00:00.250Z'));
    const first = vi.fn();
    const second = vi.fn();
    const stopFirst = subscribeToTicks(first);
    const stopSecond = subscribeToTicks(second);
    expect(vi.getTimerCount()).toBe(1); // one shared timer, not one per listener

    vi.advanceTimersByTime(755);
    expect(first).toHaveBeenCalledTimes(1);
    expect(second).toHaveBeenCalledTimes(1);
    expect(currentTick()).toBe(Date.parse('2026-10-03T12:00:01.005Z'));

    stopFirst();
    vi.advanceTimersByTime(1000);
    expect(first).toHaveBeenCalledTimes(1);
    expect(second).toHaveBeenCalledTimes(2);

    stopSecond();
    expect(vi.getTimerCount()).toBe(0);
  });
});
