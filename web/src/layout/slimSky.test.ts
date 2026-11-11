import { describe, expect, it } from 'vitest';
import { nextSlim, SLIM_OFF, SLIM_ON, SLIM_ROOM } from './slimSky';

describe('the slim sky (2.0)', () => {
  const long = 2000;

  it('stays whole at the top and gets slim once scrolled down', () => {
    expect(nextSlim(false, 0, long)).toBe(false);
    expect(nextSlim(false, SLIM_ON, long)).toBe(false);
    expect(nextSlim(false, SLIM_ON + 1, long)).toBe(true);
  });

  it('stays slim until the player is back at the top', () => {
    expect(nextSlim(true, SLIM_ON - 10, long)).toBe(true);
    expect(nextSlim(true, SLIM_OFF + 1, long)).toBe(true);
    expect(nextSlim(true, SLIM_OFF, long)).toBe(false);
  });

  it('never gets slim on a page too short to stay scrolled without the whole sky', () => {
    expect(nextSlim(false, SLIM_ON + 20, SLIM_ON + SLIM_ROOM)).toBe(false);
    expect(nextSlim(false, SLIM_ON + 20, SLIM_ON + SLIM_ROOM + 1)).toBe(true);
  });
});
