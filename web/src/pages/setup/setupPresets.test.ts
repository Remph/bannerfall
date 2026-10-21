import { afterEach, describe, expect, it, vi } from 'vitest';
import { pregameWorldSettings as settings } from '../../testing/fixtures';
import {
  applyPreset,
  instantToLocal,
  localToInstant,
  newInviteCode,
  noblesFromDays,
  presetFacts,
  setupFromSettings,
  setupPresets,
  setupToRequest,
} from './setupPresets';

describe('setup presets', () => {
  const base = setupFromSettings(settings);

  it('every preset gives a request the server takes', () => {
    for (const preset of setupPresets) {
      const request = setupToRequest(applyPreset(base, preset));
      expect(request, preset).not.toBeNull();
      // the pre-game values go along: they are fixed once the world opens
      expect(request?.mapSize, preset).not.toBeNull();
      expect(request?.npcLordCount, preset).not.toBeNull();
      expect(request?.victoryMode, preset).not.toBeNull();
    }
  });

  it('custom keeps what is stored', () => {
    expect(applyPreset(base, 'custom')).toEqual(base);
  });

  it('the co-op preset brings many lords in lord guilds', () => {
    const form = applyPreset(base, 'coop');
    expect(Number(form.npcLordCount)).toBeGreaterThanOrEqual(20);
    expect(form.npcGuildsEnabled).toBe(true);
    expect(form.npcDifficulty).toBe('HARD');
    expect(presetFacts(base, 'coop').lords).toBe('20');
  });

  it('a preset’s map spread brings its map size and noble range', () => {
    expect(applyPreset(base, 'friends')).toMatchObject({ mapSpread: 'NORMAL', mapSize: '100', nobleMaxDistance: '70' });
    expect(applyPreset(base, 'long')).toMatchObject({ mapSpread: 'WIDE', mapSize: '130', nobleMaxDistance: '91' });
    expect(setupToRequest(applyPreset(base, 'long'))).toMatchObject({
      mapSpread: 'WIDE',
      mapSize: 130,
      nobleMaxDistance: 91,
    });
  });

  it('the rounds with friends let guild mates start together', () => {
    const stored = { ...base, guildStartTogether: false };
    expect(applyPreset(stored, 'friends').guildStartTogether).toBe(true);
    expect(applyPreset(stored, 'coop').guildStartTogether).toBe(true);
    expect(applyPreset(stored, 'custom').guildStartTogether).toBe(false);
  });

  it('the 1.6 rules are saved with the rest', () => {
    const request = setupToRequest({
      ...base,
      archersEnabled: true,
      moraleEnabled: true,
      moraleFloor: '50',
      noblePriceStep: '0',
      guildStartTogether: false,
    });
    expect(request).toMatchObject({
      archersEnabled: true,
      moraleEnabled: true,
      moraleFloor: 50,
      noblePriceStep: 0,
      guildStartTogether: false,
    });
    expect(setupToRequest({ ...base, moraleFloor: '20' })).toBeNull();
  });

  it('a value out of range stops the save', () => {
    expect(setupToRequest({ ...base, mapSize: '10' })).toBeNull();
    expect(setupToRequest({ ...base, dominationPercent: '5' })).toBeNull();
    expect(setupToRequest({ ...base, keepCount: '1.5' })).toBeNull();
  });

  it('the walls and victory settings are saved with the rest', () => {
    const request = setupToRequest({ ...base, barbarianWallsEnabled: true, victoryMode: 'KEEPS', keepCount: '6' });
    expect(request).toMatchObject({ barbarianWallsEnabled: true, victoryMode: 'KEEPS', keepCount: 6 });
  });
});

describe('pace hint', () => {
  it('scales the simulation: day 12 to 15 at speed 3, day 6 to 8 at speed 6', () => {
    expect(noblesFromDays('3')).toEqual([12, 15]);
    expect(noblesFromDays('6')).toEqual([6, 8]);
  });

  it('says nothing for an empty or bad speed', () => {
    expect(noblesFromDays('')).toBeNull();
    expect(noblesFromDays('0')).toBeNull();
  });
});

describe('times and codes', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('a datetime-local value goes there and back', () => {
    const instant = localToInstant('2026-11-07T18:00');
    expect(instant).not.toBeNull();
    expect(instantToLocal(instant)).toBe('2026-11-07T18:00');
    expect(localToInstant('')).toBeNull();
    expect(instantToLocal(null)).toBe('');
  });

  it('an invite code has ten letters and digits without look-alikes', () => {
    const code = newInviteCode();
    expect(code).toMatch(/^[a-hj-km-np-z2-9]{10}$/);
  });
});
