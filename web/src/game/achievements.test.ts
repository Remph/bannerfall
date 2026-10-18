import { describe, expect, it } from 'vitest';
import { de } from '../i18n/de';
import { en } from '../i18n/en';
import {
  achievementCategories,
  achievementKeys,
  isAchievementCategory,
  isAchievementKey,
  nextThreshold,
  progressPercent,
  sortEarned,
} from './achievements';

describe('achievementKeys', () => {
  it('are the 40 of the rulebook, without doubles', () => {
    expect(achievementKeys).toHaveLength(40);
    expect(new Set(achievementKeys).size).toBe(40);
  });

  it('have a name and a description in both languages', () => {
    for (const dictionary of [en, de]) {
      expect(Object.keys(dictionary.achievements.items).sort()).toEqual([...achievementKeys].sort());
      for (const key of achievementKeys) {
        expect(dictionary.achievements.items[key].name).not.toBe('');
        expect(dictionary.achievements.items[key].description).not.toBe('');
      }
      expect(Object.keys(dictionary.achievements.categories).sort()).toEqual([...achievementCategories].sort());
    }
  });
});

describe('isAchievementKey / isAchievementCategory', () => {
  it('know the server keys only', () => {
    expect(isAchievementKey('VICTOR')).toBe(true);
    expect(isAchievementKey('SOMETHING_NEW')).toBe(false);
    expect(isAchievementCategory('COMBAT')).toBe(true);
    expect(isAchievementCategory('SPACE')).toBe(false);
  });
});

describe('nextThreshold', () => {
  it('is the value of the level after the current one', () => {
    expect(nextThreshold([10, 50, 250], 0)).toBe(10);
    expect(nextThreshold([10, 50, 250], 2)).toBe(250);
  });

  it('is undefined at the highest level and for one-time achievements', () => {
    expect(nextThreshold([10, 50, 250], 3)).toBeUndefined();
    expect(nextThreshold([], 0)).toBeUndefined();
  });
});

describe('progressPercent', () => {
  it('runs from the last threshold to the next', () => {
    expect(progressPercent([10, 50, 250], 0, 0)).toBe(0);
    expect(progressPercent([10, 50, 250], 0, 5)).toBe(50);
    expect(progressPercent([10, 50, 250], 1, 10)).toBe(0);
    expect(progressPercent([10, 50, 250], 1, 30)).toBe(50);
    expect(progressPercent([10, 50, 250], 2, 150)).toBe(50);
  });

  it('is 100 when finished and never leaves 0 … 100', () => {
    expect(progressPercent([10, 50, 250], 3, 9999)).toBe(100);
    expect(progressPercent([10, 50, 250], 1, 5)).toBe(0); // the value dropped below the earned level
    expect(progressPercent([10, 50, 250], 0, 99999)).toBe(100);
  });
});

describe('sortEarned', () => {
  it('puts the highest level first and keeps the server order within a level', () => {
    const sorted = sortEarned([
      { key: 'VICTOR', level: 1 },
      { key: 'PIONEER', level: 1 },
      { key: 'GROWTH', level: 3 },
      { key: 'SOMETHING_NEW', level: 1 },
    ]);
    expect(sorted.map((item) => item.key)).toEqual(['GROWTH', 'PIONEER', 'VICTOR', 'SOMETHING_NEW']);
  });
});
