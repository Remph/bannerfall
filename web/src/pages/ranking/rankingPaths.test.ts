import { describe, expect, it } from 'vitest';
import { parsePlayerId, playerPath, rankingKindFromSearch, rankingPath } from './rankingPaths';

describe('rankingPath', () => {
  it('leaves the first page out of the URL and counts from 1', () => {
    expect(rankingPath(0)).toBe('/ranking');
    expect(rankingPath(1)).toBe('/ranking?page=2');
  });

  it('names a statistics tab in the URL and reads it back (1.5)', () => {
    expect(rankingPath(0, 'ATTACKER')).toBe('/ranking?by=attacker');
    expect(rankingPath(2, 'PLUNDERER')).toBe('/ranking?by=plunderer&page=3');
    expect(rankingKindFromSearch(new URLSearchParams('by=defender'))).toBe('DEFENDER');
    expect(rankingKindFromSearch(new URLSearchParams('by=nope'))).toBe('POINTS');
    expect(rankingKindFromSearch(new URLSearchParams(''))).toBe('POINTS');
  });
});

describe('player paths', () => {
  it('builds and parses the profile path', () => {
    expect(playerPath(12)).toBe('/players/12');
    expect(parsePlayerId('12')).toBe(12);
  });

  it('refuses anything that is not a plain positive number', () => {
    for (const bad of [undefined, '', '0', '-3', '1.5', 'abc', '12abc']) {
      expect(parsePlayerId(bad)).toBeUndefined();
    }
  });
});
