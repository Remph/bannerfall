import { useEffect, useState } from 'react';

/** Scrolled this far down, the sky gets slim; back above `SLIM_OFF` it is whole again. */
export const SLIM_ON = 48;
export const SLIM_OFF = 8;
/**
 * The slim sky is shorter, so the page loses that much scroll range. Only pages that can scroll this
 * much further get the slim sky: on a shorter one the scroll position would fall back under
 * `SLIM_OFF` at once and the header would flip back and forth.
 */
export const SLIM_ROOM = 140;

/**
 * Whether the phone's sky is slim (2.0): `was` is the state so far, `scrollY` the scroll position and
 * `range` how far the page can scroll in all. The gap between on and off keeps it from flickering.
 */
export function nextSlim(was: boolean, scrollY: number, range: number): boolean {
  if (was) {
    return scrollY > SLIM_OFF;
  }
  return scrollY > SLIM_ON && range > SLIM_ON + SLIM_ROOM;
}

/**
 * The sky header shrinks to one slim band once the player scrolls down, and comes back at the top
 * (2.0, phones; the CSS ignores it on wider screens). `always`: pages that never show the whole sky
 * (the map, which doesn't scroll).
 */
export function useSlimSky(always: boolean): boolean {
  const [scrolled, setScrolled] = useState(false);
  useEffect(() => {
    const update = () => {
      const range = document.documentElement.scrollHeight - window.innerHeight;
      setScrolled((was) => nextSlim(was, window.scrollY, range));
    };
    update();
    window.addEventListener('scroll', update, { passive: true });
    return () => window.removeEventListener('scroll', update);
  }, []);
  return always || scrolled;
}
