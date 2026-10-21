import { describe, expect, it } from 'vitest';
import type { AdminWorldSettings } from '../../api/queries/admin';
import { pregameWorldSettings } from '../../testing/fixtures';
import {
  changedKeys,
  changedRows,
  changedSections,
  endsAtRequest,
  invalidRows,
  numberSwitchOn,
  parseSection,
  switchedOnValue,
  worldRows,
  worldTabRequest,
  worldTabToForm,
  type WorldTabKey,
} from './worldTabForm';

const running: AdminWorldSettings = { ...pregameWorldSettings, phase: 'OPEN', setupDone: true };

describe('worldTab: changes', () => {
  it('names the changed rows once per row and in the order of the tab', () => {
    const stored = worldTabToForm(running);
    const form = { ...stored, moraleFloor: '40', moraleEnabled: false, standardTheftEnabled: false, name: 'Neu' };
    const keys = changedKeys(stored, form);
    expect(keys.sort()).toEqual(['moraleEnabled', 'moraleFloor', 'name', 'standardTheftEnabled']);
    const rows = changedRows(keys);
    expect(rows).toEqual(['name', 'morale', 'standardTheft']);
    expect(changedSections(rows)).toEqual(['basics', 'rules']);
  });

  it('puts every field of the form in exactly one row', () => {
    const keys = Object.keys(worldTabToForm(running)) as WorldTabKey[];
    const inRows = Object.values(worldRows).flatMap((row) => [...row.keys]);
    expect([...inRows].sort()).toEqual([...keys].sort());
  });

  it('opens Basics for an unknown section', () => {
    expect(parseSection('rules')).toBe('rules');
    expect(parseSection('nonsense')).toBe('basics');
    expect(parseSection(null)).toBe('basics');
  });
});

describe('worldTab: the request', () => {
  it('sends the world, victory and wall settings in one request', () => {
    const form = {
      ...worldTabToForm(running),
      speed: '4',
      victoryMode: 'KEEPS' as const,
      keepCount: '7',
      barbarianWallsEnabled: true,
      barbarianWallMaxLevel: '8',
    };
    const request = worldTabRequest(running, form);
    expect(request).toMatchObject({
      speed: 4,
      victoryMode: 'KEEPS',
      keepCount: 7,
      keepPercent: 51,
      barbarianWallsEnabled: true,
      barbarianWallMaxLevel: 8,
      // fixed once the world is open
      mapSize: null,
      npcLordCount: null,
    });
  });

  it('sends the map in the pre-game', () => {
    const request = worldTabRequest(pregameWorldSettings, { ...worldTabToForm(pregameWorldSettings), mapSize: '130' });
    expect(request).toMatchObject({ mapSize: 130, mapSpread: 'NORMAL', npcLordCount: 4 });
  });

  it('is null while any field is out of range', () => {
    expect(worldTabRequest(running, { ...worldTabToForm(running), moraleFloor: '10' })).toBeNull();
    expect(worldTabRequest(running, { ...worldTabToForm(running), keepCount: '13' })).toBeNull();
    expect(worldTabRequest(running, { ...worldTabToForm(running), barbarianWallMaxLevel: '21' })).toBeNull();
  });

  it('reads the end date: empty = none, an unfinished one = not valid', () => {
    const form = worldTabToForm(running);
    expect(endsAtRequest({ ...form, endsAt: '' })).toBeNull();
    expect(endsAtRequest({ ...form, endsAt: '2026-11-07T18:00' })).toBe(new Date('2026-11-07T18:00').toISOString());
    expect(endsAtRequest({ ...form, endsAt: '2026-11-07' })).toBeUndefined();
  });
});

describe('worldTab: rows that keep the draft from being saved', () => {
  it('names only the wrong row', () => {
    const form = { ...worldTabToForm(running), moraleFloor: '10', name: 'Neu' };
    expect(invalidRows(running, form)).toEqual(['morale']);
  });

  it('checks the fields of a row together', () => {
    // the paid build slots may not be fewer than the normal ones
    const form = { ...worldTabToForm(running), buildQueueSize: '12', buildQueueMax: '11' };
    expect(invalidRows(running, form)).toEqual(['buildQueue']);
  });

  it('is empty for a valid draft', () => {
    expect(invalidRows(running, { ...worldTabToForm(running), speed: '5' })).toEqual([]);
  });

  it('names the end date while it is unfinished', () => {
    expect(invalidRows(running, { ...worldTabToForm(running), endsAt: '2026-11' })).toEqual(['endsAt']);
  });
});

describe('worldTab: numbers that are off at 0', () => {
  it('turn back on to the stored number, or the default', () => {
    expect(switchedOnValue(running, 'paladinMaxSlots')).toBe('5');
    const off = { ...running, paladinMaxSlots: 0, marketExchangeRatio: 0, noblePriceStep: 0, automationVillages: 0 };
    expect(switchedOnValue(off, 'paladinMaxSlots')).toBe('5');
    expect(switchedOnValue(off, 'marketExchangeRatio')).toBe('1.5');
    expect(switchedOnValue(off, 'noblePriceStep')).toBe('0.25');
    expect(switchedOnValue(off, 'automationVillages')).toBe('3');
    expect(switchedOnValue({ ...running, paladinMaxSlots: 3 }, 'paladinMaxSlots')).toBe('3');
  });

  it('are on above 0 and while being typed', () => {
    expect(numberSwitchOn('0')).toBe(false);
    expect(numberSwitchOn('0.0')).toBe(false);
    expect(numberSwitchOn('2')).toBe(true);
    expect(numberSwitchOn('')).toBe(true);
  });
});
