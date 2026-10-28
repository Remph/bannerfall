import { describe, expect, it } from 'vitest';
import type { TutorialStepKey } from '../api/queries/tutorial';
import { en } from '../i18n/en';
import { de } from '../i18n/de';
import { resourceText, splitLines, tutorialPath } from './tutorial';

const KEYS: TutorialStepKey[] = [
  'WOODCUTTER',
  'HEADQUARTERS',
  'BARRACKS',
  'SPEARMEN',
  'MAP_BARBARIAN',
  'ATTACK',
  'REPORT',
  'SIMULATOR',
  'FARM_LIST',
  'MARKET',
  'GUILD',
  'LESSON_UNIT_LEVELS',
  'LESSON_CONQUEST',
  'LESSON_AUTOMATION',
  'LESSON_BANNER_HALL',
];

describe('tutorialPath', () => {
  it('leads every step and lesson to the page it is about', () => {
    expect(tutorialPath('WOODCUTTER', 7)).toBe('/v/7/hq');
    expect(tutorialPath('BARRACKS', 7)).toBe('/v/7/hq');
    expect(tutorialPath('SPEARMEN', 7)).toBe('/v/7/recruit/barracks');
    expect(tutorialPath('MAP_BARBARIAN', 7)).toBe('/v/7/map');
    expect(tutorialPath('ATTACK', 7)).toBe('/v/7/rally');
    expect(tutorialPath('REPORT', 7)).toBe('/reports');
    expect(tutorialPath('SIMULATOR', 7)).toBe('/v/7/rally/simulator');
    expect(tutorialPath('FARM_LIST', 7)).toBe('/v/7/farm');
    expect(tutorialPath('GUILD', 7)).toBe('/guild');
    expect(tutorialPath('LESSON_UNIT_LEVELS', 7)).toBe('/v/7/smithy');
    expect(tutorialPath('LESSON_CONQUEST', 7)).toBe('/v/7/recruit/academy');
    expect(tutorialPath('LESSON_AUTOMATION', 7)).toBe('/v/7/automation');
    expect(tutorialPath('LESSON_BANNER_HALL', 7)).toBe('/v/7/banners');
  });
});

describe('the tutorial texts', () => {
  it('have a title and a short text in both languages for every key', () => {
    for (const dictionary of [en, de]) {
      for (const key of KEYS) {
        const text = dictionary.tutorial.steps[key];
        expect(text.title.length).toBeGreaterThan(0);
        expect(text.text.length).toBeGreaterThan(0);
        // short: a card, not a page
        expect(text.text.length).toBeLessThan(260);
      }
      expect(Object.keys(dictionary.tutorial.steps).sort()).toEqual([...KEYS].sort());
    }
  });
});

describe('resourceText', () => {
  it('names the amounts above zero in the language', () => {
    expect(resourceText({ wood: 63, clay: 75, iron: 50 }, en, 'en')).toBe('63 Wood, 75 Clay, 50 Iron');
    expect(resourceText({ wood: 1200, clay: 0, iron: 5 }, de, 'de')).toBe('1.200 Holz, 5 Eisen');
    expect(resourceText({ wood: 0, clay: 0, iron: 0 }, en, 'en')).toBe('');
  });
});

describe('splitLines', () => {
  it('keeps the steps and the lessons apart, each in order', () => {
    const lines = splitLines([
      { key: 'WOODCUTTER', kind: 'STEP', status: 'DONE' },
      { key: 'LESSON_CONQUEST', kind: 'LESSON', status: 'OPEN' },
      { key: 'ATTACK', kind: 'STEP', status: 'OPEN' },
    ]);
    expect(lines.steps.map((line) => line.key)).toEqual(['WOODCUTTER', 'ATTACK']);
    expect(lines.lessons.map((line) => line.key)).toEqual(['LESSON_CONQUEST']);
  });
});
