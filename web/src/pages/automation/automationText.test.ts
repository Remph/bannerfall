import { describe, expect, it } from 'vitest';
import type { VillageAutomation } from '../../api/queries/automation';
import { en } from '../../i18n/en';
import { byHandText, missingText, statusText } from './automationText';

const NOW = Date.parse('2026-10-20T20:00:00Z');
const LEVEL = 4;
const SPEARS = 340;
const SWORDS = 120;
const VILLAGES = 3;
const LINES = 5;

const base: VillageAutomation = {
  state: 'READY',
  villagesNeeded: VILLAGES,
  villages: VILLAGES,
  plan: 'A',
  enabled: true,
  pausedByCancel: false,
  line: 1,
  lineCount: LINES,
  building: 'BARRACKS',
  level: LEVEL,
  readyAt: null,
  recruiting: null,
  missing: {},
  byHand: [],
  blocked: {},
};

describe('statusText', () => {
  it('names what the village queues next', () => {
    expect(statusText(en, 'en', NOW, base)).toBe('Queues Barracks Lv 4 now.');
    expect(statusText(en, 'en', NOW, { ...base, state: 'WAITING_QUEUE' })).toBe(
      'Waits until a build order is done: Barracks Lv 4 next.',
    );
  });

  it('says when the resources are there', () => {
    const text = statusText(en, 'en', NOW, { ...base, state: 'WAITING_RESOURCES', readyAt: '2026-10-20T21:14:00Z' });
    expect(text?.startsWith('Waits for resources: Barracks Lv 4 · enough at ')).toBe(true);
  });

  it('asks for a plan before it is switched off, and says nothing while locked', () => {
    expect(statusText(en, 'en', NOW, { ...base, state: 'OFF', plan: null, building: null })).toBe(
      'Choose a plan: the village starts working through it right away.',
    );
    expect(statusText(en, 'en', NOW, { ...base, state: 'OFF' })).toBe('Switched off.');
    expect(statusText(en, 'en', NOW, { ...base, state: 'LOCKED' })).toBeUndefined();
  });
});

describe('missingText and byHandText', () => {
  it('lists the missing units in unit order', () => {
    expect(missingText(en, 'en', { SWORDSMAN: SWORDS, SPEARMAN: SPEARS, AXEMAN: 0 })).toBe(
      'Spearman 340, Swordsman 120',
    );
    expect(byHandText(en, ['NOBLE'])).toBe(en.units.NOBLE);
  });

  it('says what a unit needs that the village cannot recruit yet', () => {
    expect(
      missingText(en, 'en', { SWORDSMAN: SWORDS, SPEARMAN: SPEARS }, { SWORDSMAN: [{ building: 'SMITHY', level: 1 }] }),
    ).toBe('Spearman 340, Swordsman 120 (needs Smithy 1)');
  });
});
