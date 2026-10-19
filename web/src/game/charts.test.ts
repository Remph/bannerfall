import { describe, expect, it } from 'vitest';
import { chartDays, nearestIndex, niceMax, valueOn } from './charts';

describe('niceMax', () => {
  it('rounds the top up to round grid lines', () => {
    expect(niceMax(1234, 4)).toBe(2000);
    expect(niceMax(900, 3)).toBe(1500);
    expect(niceMax(7, 4)).toBe(8);
    expect(niceMax(100, 4)).toBe(100);
    expect(niceMax(2, 4)).toBe(4);
  });

  it('keeps an axis for an empty chart and whole numbers for small values', () => {
    expect(niceMax(0, 4)).toBe(4);
    expect(niceMax(1, 3)).toBe(3);
  });
});

describe('chartDays and valueOn', () => {
  const ada = [
    { day: '2026-10-18', value: 10 },
    { day: '2026-10-19', value: 12 },
  ];
  const lord = [
    { day: '2026-10-17', value: 5 },
    { day: '2026-10-19', value: 9 },
  ];

  it('collects every day once, in order', () => {
    expect(chartDays([ada, lord])).toEqual(['2026-10-17', '2026-10-18', '2026-10-19']);
  });

  it('finds a line value by day', () => {
    expect(valueOn(lord, '2026-10-19')).toBe(9);
    expect(valueOn(ada, '2026-10-17')).toBeUndefined();
  });
});

describe('nearestIndex', () => {
  it('picks the closest day and stays inside', () => {
    expect(nearestIndex(0.49, 3)).toBe(1);
    expect(nearestIndex(-1, 3)).toBe(0);
    expect(nearestIndex(2, 3)).toBe(2);
    expect(nearestIndex(0.7, 1)).toBe(0);
  });
});
