import { describe, expect, it } from 'vitest';
import { parsePlayerId, playerPath, rankingPath } from './rankingPaths';

describe('rankingPath', () => {
  it('leaves the first page out of the URL and counts from 1', () => {
    expect(rankingPath(0)).toBe('/ranking');
    expect(rankingPath(1)).toBe('/ranking?page=2');
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
