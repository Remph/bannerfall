import { describe, expect, it } from 'vitest';
import type { AdminWorldSettings } from '../../api/queries/admin';
import { barbarianWallsRequest, barbarianWallsToForm } from './barbarianWallsForm';

const settings: AdminWorldSettings = {
  name: 'Bannerfall 1',
  speed: 3,
  unitSpeed: 1,
  mapSize: 100,
  mapSpread: 'NORMAL',
  startResources: 500,
  luckRange: 0.25,
  beginnerProtectionHours: 72,
  nobleMaxDistance: 70,
  loyaltyRegenPerHour: 1,
  buildQueueSize: 5,
  buildQueueMax: 10,
  recruitQueueSize: 5,
  cancelWindowSeconds: 600,
  barbarianInitialCount: 30,
  barbariansPerNewPlayer: 3,
  barbarianGrowthEnabled: true,
  tradeMaxRatio: 3,
  inviteCode: 'friends-only',
  npcLordCount: 4,
  npcDifficulty: 'NORMAL',
  npcThinkMinutes: 20,
  guildsEnabled: false,
  guildMaxMembers: 10,
  guildStartTogether: true,
  npcGuildsEnabled: false,
  npcGuildMaxMembers: 3,
  unitLevelsEnabled: false,
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
  archersEnabled: false,
  moraleEnabled: true,
  moraleFloor: 30,
  automationVillages: 3,
  barbarianWallsEnabled: false,
  barbarianWallStartDays: 3,
  barbarianWallMaxLevel: 5,
  barbarianWallsRebuild: false,
  victoryMode: 'DOMINATION',
  dominationPercent: 60,
  victoryHoldHours: 48,
  keepCount: 5,
  keepPercent: 51,
  phase: 'OPEN',
  setupDone: false,
  opensAt: null,
  endsAt: null,
  endedAt: null,
};

describe('barbarianWallsRequest', () => {
  it('starts from the stored values', () => {
    expect(barbarianWallsToForm(settings)).toEqual({
      barbarianWallsEnabled: false,
      barbarianWallStartDays: '3',
      barbarianWallMaxLevel: '5',
      barbarianWallsRebuild: false,
    });
  });

  it('sends the stored settings with only the wall settings replaced', () => {
    const request = barbarianWallsRequest(settings, {
      barbarianWallsEnabled: true,
      barbarianWallStartDays: '7',
      barbarianWallMaxLevel: '10',
      barbarianWallsRebuild: true,
    });
    expect(request).toMatchObject({
      name: 'Bannerfall 1',
      speed: 3,
      inviteCode: 'friends-only',
      barbarianGrowthEnabled: true,
      guildMaxMembers: 10,
      guildStartTogether: true,
      npcGuildsEnabled: false,
      npcGuildMaxMembers: 3,
      barbarianWallsEnabled: true,
      barbarianWallStartDays: 7,
      barbarianWallMaxLevel: 10,
      barbarianWallsRebuild: true,
      // an open world: map size, barbarians and lords are fixed, the server keeps them
      mapSize: null,
      barbarianInitialCount: null,
      npcLordCount: null,
    });
  });

  it('sends map size, barbarians and lords along in the pre-game', () => {
    const request = barbarianWallsRequest({ ...settings, phase: 'PREGAME' }, barbarianWallsToForm(settings));
    expect(request).toMatchObject({ mapSize: 100, barbarianInitialCount: 30, npcLordCount: 4 });
  });

  it('refuses days outside 0–365, levels outside 0–20, fractions and empty fields', () => {
    const form = barbarianWallsToForm(settings);
    expect(barbarianWallsRequest(settings, { ...form, barbarianWallStartDays: '0' })).not.toBeNull();
    expect(barbarianWallsRequest(settings, { ...form, barbarianWallStartDays: '365' })).not.toBeNull();
    expect(barbarianWallsRequest(settings, { ...form, barbarianWallStartDays: '366' })).toBeNull();
    expect(barbarianWallsRequest(settings, { ...form, barbarianWallStartDays: '-1' })).toBeNull();
    expect(barbarianWallsRequest(settings, { ...form, barbarianWallStartDays: '1.5' })).toBeNull();
    expect(barbarianWallsRequest(settings, { ...form, barbarianWallMaxLevel: '20' })).not.toBeNull();
    expect(barbarianWallsRequest(settings, { ...form, barbarianWallMaxLevel: '21' })).toBeNull();
    expect(barbarianWallsRequest(settings, { ...form, barbarianWallMaxLevel: '' })).toBeNull();
    expect(barbarianWallsRequest(settings, { ...form, barbarianWallMaxLevel: 'abc' })).toBeNull();
  });
});
