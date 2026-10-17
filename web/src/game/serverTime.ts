import { useSyncExternalStore } from 'react';

/**
 * Difference between the server clock and this device's clock. Every API response carries the
 * header X-Server-Time; the client records it here. Countdowns and ticking resources use
 * serverNow() / useServerNow() as "now", so a wrong phone clock doesn't matter.
 */
const MAX_SAMPLES = 5;

export interface ServerClock {
  record(serverTimeHeader: string | null, localNowMs: number): void;
  /** Median of the last samples (one slow response doesn't throw it off); 0 before the first sample. */
  offsetMs(): number;
}

export function createServerClock(): ServerClock {
  const samples: number[] = [];
  return {
    record(serverTimeHeader, localNowMs) {
      if (serverTimeHeader === null) {
        return;
      }
      const serverNowMs = Date.parse(serverTimeHeader);
      if (Number.isNaN(serverNowMs)) {
        return;
      }
      samples.push(serverNowMs - localNowMs);
      if (samples.length > MAX_SAMPLES) {
        samples.shift();
      }
    },
    offsetMs() {
      return median(samples);
    },
  };
}

/** The one instance the app uses. */
export const serverClock = createServerClock();

/** "Now" by the server's clock, in milliseconds since 1970. */
export function serverNow(): number {
  return Date.now() + serverClock.offsetMs();
}

/**
 * Whole seconds left until `untilMs`, rounded up: a countdown shows 0:00:01 during the last second
 * and 0:00:00 from the moment the time is reached.
 */
export function secondsUntil(untilMs: number, nowMs: number): number {
  return Math.max(0, Math.ceil((untilMs - nowMs) / 1000));
}

/** Milliseconds until the next full second (plus a few ms, so the timer surely lands after it). */
export function msUntilNextSecond(nowMs: number): number {
  return 1000 - (nowMs % 1000) + 5;
}

/*
 * One shared ticker for the whole app: a single timer fires on every full server second and tells
 * all subscribed components at once, so every counter on screen changes in the same render. The
 * timer only runs while at least one component listens.
 */
type Listener = () => void;

const listeners = new Set<Listener>();
let tickNow = serverNow();
let timer: ReturnType<typeof setTimeout> | undefined;

function tick(): void {
  tickNow = serverNow();
  timer = setTimeout(tick, msUntilNextSecond(tickNow));
  // a copy, so a listener that unsubscribes during the loop doesn't disturb it
  [...listeners].forEach((listener) => listener());
}

/** Exported for tests; components use useServerNow(). Returns the unsubscribe function. */
export function subscribeToTicks(listener: Listener): () => void {
  listeners.add(listener);
  if (timer === undefined) {
    tickNow = serverNow();
    timer = setTimeout(tick, msUntilNextSecond(tickNow));
  }
  return () => {
    listeners.delete(listener);
    if (listeners.size === 0 && timer !== undefined) {
      clearTimeout(timer);
      timer = undefined;
    }
  };
}

/**
 * The time of the last tick. React may call this several times per render and expects the same
 * value each time, so it only changes on a tick – or, while nobody listens, once it is a second old.
 */
export function currentTick(): number {
  if (timer === undefined) {
    const now = serverNow();
    if (now - tickNow >= 1000) {
      tickNow = now;
    }
  }
  return tickNow;
}

/**
 * The server time, re-rendering the component once per second – on the full second, so all
 * countdowns and resource counters on screen change together.
 */
export function useServerNow(): number {
  // useSyncExternalStore connects React to a value that lives outside React (here: the ticker).
  // React subscribes on mount, unsubscribes on unmount and re-renders when currentTick() changes.
  return useSyncExternalStore(subscribeToTicks, currentTick);
}

/*
 * A world that has ended is frozen (v1.3, 02-game-rules §22): its game time stands still at the end,
 * so resources stop counting and countdowns stop. The app layout sets the end from GET /api/world.
 * The server clock itself (ServerClock) keeps running.
 */
let frozenAtMs: number | null = null;

/** When the world ended, in milliseconds; null while it runs. */
export function setFrozenAt(endedAtMs: number | null): void {
  frozenAtMs = endedAtMs;
}

/** `nowMs` in game time: the end of the world once it has ended, otherwise `nowMs` itself. */
export function gameTime(nowMs: number): number {
  return frozenAtMs !== null && frozenAtMs < nowMs ? frozenAtMs : nowMs;
}

/** Like useServerNow(), in game time: what resources, countdowns and movements are shown at. */
export function useGameNow(): number {
  return gameTime(useServerNow());
}

export function median(values: readonly number[]): number {
  if (values.length === 0) {
    return 0;
  }
  const sorted = [...values].sort((a, b) => a - b);
  const middle = Math.floor(sorted.length / 2);
  // With noUncheckedIndexedAccess, sorted[i] is `number | undefined`; `?? 0` handles the impossible case.
  if (sorted.length % 2 === 1) {
    return sorted[middle] ?? 0;
  }
  return ((sorted[middle - 1] ?? 0) + (sorted[middle] ?? 0)) / 2;
}
