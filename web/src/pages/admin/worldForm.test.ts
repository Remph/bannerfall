import { describe, expect, it } from 'vitest';
import { withSpread, worldFromForm, worldToForm } from './worldForm';
import { adminSettingsFixture } from '../../testing/fixtures';

const settings = adminSettingsFixture({ phase: 'OPEN' });

describe('worldFromForm', () => {
  it('sends the editable settings back after a round trip through the form', () => {
    const request = worldFromForm(worldToForm(settings));
    expect(request).toEqual({
      name: 'Bannerfall 1',
      inviteCode: 'friends-only',
      speed: 3,
      unitSpeed: 1,
      startResources: 500,
      luckRange: 0.25,
      beginnerProtectionHours: 72,
      nobleMaxDistance: 70,
      loyaltyRegenPerHour: 1,
      buildQueueSize: 5,
      buildQueueMax: 10,
      recruitQueueSize: 5,
      cancelWindowSeconds: 600,
      barbariansPerNewPlayer: 3,
      tradeMaxRatio: 3,
      npcThinkMinutes: 20,
      barbarianGrowthEnabled: true,
      guildsEnabled: false,
      guildMaxMembers: 10,
      guildStartTogether: true,
      guildBannersEnabled: false,
      tutorialEnabled: true,
      npcGuildsEnabled: false,
      npcGuildMaxMembers: 3,
      unitLevelsEnabled: false,
      archersEnabled: false,
      paladinVillagesPerSlot: 4,
      paladinMaxSlots: 5,
      noblePriceStep: 0.25,
      bannersEnabled: true,
      bannerDropFactor: 1,
      standardTheftEnabled: true,
      npcBannersEnabled: false,
      npcLearningEnabled: false,
      npcShipmentsEnabled: true,
      marketExchangeRatio: 1.5,
      villageNameLanguage: 'EN',
      nightBonusEnabled: false,
      nightBonusStartHour: 23,
      nightBonusEndHour: 7,
      nightBonusFactor: 2,
      moraleEnabled: true,
      moraleFloor: 30,
      automationVillages: 3,
      barbarianWallsEnabled: null,
      barbarianWallStartDays: null,
      barbarianWallMaxLevel: null,
      barbarianWallsRebuild: null,
      victoryMode: null,
      dominationPercent: null,
      victoryHoldHours: null,
      keepCount: null,
      keepPercent: null,
      mapSize: null,
      mapSpread: null,
      barbarianInitialCount: null,
      npcLordCount: null,
    });
  });

  it('sends the lord shipments switch both ways', () => {
    const form = worldToForm(settings);
    expect(form.npcShipmentsEnabled).toBe(true);
    expect(worldFromForm({ ...form, npcShipmentsEnabled: false })?.npcShipmentsEnabled).toBe(false);
    expect(worldFromForm(form)?.npcShipmentsEnabled).toBe(true);
  });

  it('sends the lord guild switch and the most lords per guild (2–50)', () => {
    const form = worldToForm({ ...settings, npcGuildsEnabled: true, npcGuildMaxMembers: 4 });
    expect(form.npcGuildsEnabled).toBe(true);
    expect(form.npcGuildMaxMembers).toBe('4');
    expect(worldFromForm(form)).toMatchObject({ npcGuildsEnabled: true, npcGuildMaxMembers: 4 });
    expect(worldFromForm({ ...form, npcGuildsEnabled: false })?.npcGuildsEnabled).toBe(false);
    expect(worldFromForm({ ...form, npcGuildMaxMembers: '1' })).toBeNull();
    expect(worldFromForm({ ...form, npcGuildMaxMembers: '51' })).toBeNull();
    expect(worldFromForm({ ...form, npcGuildMaxMembers: '2.5' })).toBeNull();
  });

  it('sends the unit levels switch as it is in the form', () => {
    const form = worldToForm({ ...settings, unitLevelsEnabled: true });
    expect(form.unitLevelsEnabled).toBe(true);
    expect(worldFromForm(form)?.unitLevelsEnabled).toBe(true);
    expect(worldFromForm({ ...form, unitLevelsEnabled: false })?.unitLevelsEnabled).toBe(false);
  });

  it('sends the two paladin settings and checks their ranges (1–100 villages, 0–20 slots)', () => {
    const form = worldToForm({ ...settings, paladinVillagesPerSlot: 6, paladinMaxSlots: 0 });
    expect(form.paladinVillagesPerSlot).toBe('6');
    expect(form.paladinMaxSlots).toBe('0');
    expect(worldFromForm(form)).toMatchObject({ paladinVillagesPerSlot: 6, paladinMaxSlots: 0 });
    expect(worldFromForm({ ...form, paladinVillagesPerSlot: '0' })).toBeNull();
    expect(worldFromForm({ ...form, paladinVillagesPerSlot: '101' })).toBeNull();
    expect(worldFromForm({ ...form, paladinMaxSlots: '21' })).toBeNull();
    expect(worldFromForm({ ...form, paladinMaxSlots: '-1' })).toBeNull();
    expect(worldFromForm({ ...form, paladinMaxSlots: '2.5' })).toBeNull();
    expect(worldFromForm({ ...form, paladinMaxSlots: '' })).toBeNull();
  });

  it('sends the banner switch and the drop factor and checks its range (0–3)', () => {
    const form = worldToForm({ ...settings, bannersEnabled: false, bannerDropFactor: 0.5 });
    expect(form.bannersEnabled).toBe(false);
    expect(form.bannerDropFactor).toBe('0.5');
    expect(worldFromForm(form)).toMatchObject({ bannersEnabled: false, bannerDropFactor: 0.5 });
    expect(worldFromForm({ ...form, bannerDropFactor: '0' })?.bannerDropFactor).toBe(0);
    expect(worldFromForm({ ...form, bannerDropFactor: '3.1' })).toBeNull();
    expect(worldFromForm({ ...form, bannerDropFactor: '-0.1' })).toBeNull();
    expect(worldFromForm({ ...form, bannerDropFactor: '' })).toBeNull();
  });

  it('sends the noble price step and checks its range (0–1)', () => {
    const form = worldToForm({ ...settings, noblePriceStep: 0.5 });
    expect(form.noblePriceStep).toBe('0.5');
    expect(worldFromForm(form)?.noblePriceStep).toBe(0.5);
    expect(worldFromForm({ ...form, noblePriceStep: '0' })?.noblePriceStep).toBe(0);
    expect(worldFromForm({ ...form, noblePriceStep: '1.1' })).toBeNull();
    expect(worldFromForm({ ...form, noblePriceStep: '-0.1' })).toBeNull();
    expect(worldFromForm({ ...form, noblePriceStep: '' })).toBeNull();
  });

  it('sends the market exchange ratio: 0 turns it off, otherwise 1 to 10', () => {
    const form = worldToForm(settings);
    expect(form.marketExchangeRatio).toBe('1.5');
    expect(worldFromForm(form)?.marketExchangeRatio).toBe(1.5);
    expect(worldFromForm({ ...form, marketExchangeRatio: '0' })?.marketExchangeRatio).toBe(0);
    expect(worldFromForm({ ...form, marketExchangeRatio: '1' })?.marketExchangeRatio).toBe(1);
    expect(worldFromForm({ ...form, marketExchangeRatio: '10' })?.marketExchangeRatio).toBe(10);
    expect(worldFromForm({ ...form, marketExchangeRatio: '0.5' })).toBeNull();
    expect(worldFromForm({ ...form, marketExchangeRatio: '10.5' })).toBeNull();
    expect(worldFromForm({ ...form, marketExchangeRatio: '' })).toBeNull();
  });

  it('sends the night bonus and refuses a night without hours or a factor out of range', () => {
    const form = { ...worldToForm(settings), nightBonusEnabled: true };
    expect(worldFromForm({ ...form, nightBonusStartHour: '0', nightBonusEndHour: '8' })).toMatchObject({
      nightBonusEnabled: true,
      nightBonusStartHour: 0,
      nightBonusEndHour: 8,
      nightBonusFactor: 2,
      moraleEnabled: true,
      moraleFloor: 30,
    });
    expect(worldFromForm({ ...form, nightBonusStartHour: '7' })).toBeNull();
    expect(worldFromForm({ ...form, nightBonusEndHour: '24' })).toBeNull();
    expect(worldFromForm({ ...form, nightBonusStartHour: '22.5' })).toBeNull();
    expect(worldFromForm({ ...form, nightBonusFactor: '1' })).toBeNull();
    expect(worldFromForm({ ...form, nightBonusFactor: '3' })?.nightBonusFactor).toBe(3);
  });

  it('sends morale and refuses a floor out of range', () => {
    const form = worldToForm(settings);
    expect(worldFromForm({ ...form, moraleEnabled: false, moraleFloor: '50' })).toMatchObject({
      moraleEnabled: false,
      moraleFloor: 50,
    });
    expect(worldFromForm({ ...form, moraleFloor: '29' })).toBeNull();
    expect(worldFromForm({ ...form, moraleFloor: '101' })).toBeNull();
    expect(worldFromForm({ ...form, moraleFloor: '40.5' })).toBeNull();
    expect(worldFromForm({ ...form, moraleFloor: '100' })?.moraleFloor).toBe(100);
  });

  it('sends from which village on the automation unlocks, 0 for off', () => {
    const form = worldToForm(settings);
    expect(worldFromForm({ ...form, automationVillages: '0' })?.automationVillages).toBe(0);
    expect(worldFromForm({ ...form, automationVillages: '101' })).toBeNull();
    expect(worldFromForm({ ...form, automationVillages: '2.5' })).toBeNull();
  });

  it('sends the language of default village names', () => {
    const form = worldToForm(settings);
    expect(form.villageNameLanguage).toBe('EN');
    expect(worldFromForm({ ...form, villageNameLanguage: 'DE' })?.villageNameLanguage).toBe('DE');
  });

  it('sends map size, barbarians and lords only in the pre-game, and checks them then', () => {
    const form = worldToForm(settings);
    expect(worldFromForm(form, true)).toMatchObject({ mapSize: 100, barbarianInitialCount: 30, npcLordCount: 4 });
    expect(worldFromForm(form)).toMatchObject({ mapSize: null, barbarianInitialCount: null, npcLordCount: null });
    expect(worldFromForm({ ...form, mapSize: '10' }, true)).toBeNull();
    expect(worldFromForm({ ...form, npcLordCount: '51' }, true)).toBeNull();
    // fixed in an open world: a stale value in the form does not block saving the rest
    expect(worldFromForm({ ...form, mapSize: '10' })).not.toBeNull();
  });

  it('sends the map spread only in the pre-game', () => {
    const form = worldToForm(settings);
    expect(form.mapSpread).toBe('NORMAL');
    expect(worldFromForm(form, true)).toMatchObject({ mapSpread: 'NORMAL' });
    expect(worldFromForm(form)).toMatchObject({ mapSpread: null });
  });

  it('gives a new spread its map size and noble range, which the host can still change', () => {
    const form = worldToForm(settings);
    expect(withSpread(form, 'VAST')).toMatchObject({ mapSpread: 'VAST', mapSize: '160', nobleMaxDistance: '112' });
    expect(withSpread(form, 'WIDE')).toMatchObject({ mapSize: '130', nobleMaxDistance: '91' });
    expect(withSpread(form, 'CLOSE')).toMatchObject({ mapSize: '80', nobleMaxDistance: '56' });
    expect(withSpread(withSpread(form, 'VAST'), 'NORMAL')).toMatchObject({ mapSize: '100', nobleMaxDistance: '70' });
    const typed = { ...withSpread(form, 'WIDE'), mapSize: '150' };
    expect(worldFromForm(typed, true)).toMatchObject({ mapSpread: 'WIDE', mapSize: 150, nobleMaxDistance: 91 });
  });

  it('trims the name and the invite code and refuses blank or too long ones', () => {
    expect(worldFromForm({ ...worldToForm(settings), inviteCode: '  abc  ' })?.inviteCode).toBe('abc');
    expect(worldFromForm({ ...worldToForm(settings), inviteCode: '   ' })).toBeNull();
    expect(worldFromForm({ ...worldToForm(settings), name: 'x'.repeat(65) })).toBeNull();
  });

  it('refuses values outside the server ranges, empty fields and text', () => {
    const form = worldToForm(settings);
    expect(worldFromForm({ ...form, luckRange: '1.5' })).toBeNull(); // would make every battle fail
    expect(worldFromForm({ ...form, speed: '0' })).toBeNull();
    expect(worldFromForm({ ...form, tradeMaxRatio: '0.5' })).toBeNull();
    expect(worldFromForm({ ...form, startResources: '' })).toBeNull();
    expect(worldFromForm({ ...form, nobleMaxDistance: 'far' })).toBeNull();
    expect(worldFromForm({ ...form, buildQueueSize: '0' })).toBeNull();
  });

  it('accepts the edges of the ranges', () => {
    const form = worldToForm(settings);
    expect(worldFromForm({ ...form, luckRange: '0' })?.luckRange).toBe(0);
    expect(worldFromForm({ ...form, luckRange: '1' })?.luckRange).toBe(1);
    expect(worldFromForm({ ...form, barbariansPerNewPlayer: '0' })?.barbariansPerNewPlayer).toBe(0);
  });

  it('wants whole numbers where the server does', () => {
    const form = worldToForm(settings);
    expect(worldFromForm({ ...form, recruitQueueSize: '2.5' })).toBeNull();
    expect(worldFromForm({ ...form, speed: '2.5' })?.speed).toBe(2.5);
  });

  it('refuses fewer paid slots than normal ones', () => {
    const form = worldToForm(settings);
    expect(worldFromForm({ ...form, buildQueueSize: '6', buildQueueMax: '5' })).toBeNull();
    expect(worldFromForm({ ...form, buildQueueSize: '6', buildQueueMax: '6' })).not.toBeNull();
  });
});
