import { describe, expect, it } from 'vitest';
import { distance, isOnMap, parseCoords } from './coords';

describe('parseCoords', () => {
  it.each([
    ['48|52', { x: 48, y: 52 }],
    ['(48|52)', { x: 48, y: 52 }],
    ['  ( 48 | 52 )  ', { x: 48, y: 52 }],
    ['48 52', { x: 48, y: 52 }],
    ['48,52', { x: 48, y: 52 }],
    ['48.52', { x: 48, y: 52 }],
    ['48/52', { x: 48, y: 52 }],
    ['0|0', { x: 0, y: 0 }],
    ['007|9', { x: 7, y: 9 }],
  ])('reads %j', (text, expected) => {
    expect(parseCoords(text)).toEqual(expected);
  });

  it.each(['', '48', '48|', '|52', '48|52|3', 'a|b', '-1|5', '4.5|6', '12345|1', '48||52'])('rejects %j', (text) => {
    expect(parseCoords(text)).toBeUndefined();
  });
});

describe('isOnMap', () => {
  it('accepts 0 … mapSize − 1', () => {
    expect(isOnMap({ x: 0, y: 0 }, 100)).toBe(true);
    expect(isOnMap({ x: 99, y: 99 }, 100)).toBe(true);
    expect(isOnMap({ x: 100, y: 5 }, 100)).toBe(false);
    expect(isOnMap({ x: 5, y: 100 }, 100)).toBe(false);
  });
});

describe('distance', () => {
  it('is the straight line in fields', () => {
    expect(distance({ x: 50, y: 50 }, { x: 53, y: 54 })).toBe(5);
    expect(distance({ x: 10, y: 10 }, { x: 10, y: 10 })).toBe(0);
    expect(distance({ x: 0, y: 0 }, { x: 1, y: 1 })).toBeCloseTo(Math.SQRT2);
  });
});
