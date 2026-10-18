import { describe, expect, it } from 'vitest';
import type { AdminWorldSettings } from '../../api/queries/admin';
import { keepsNeeded } from '../../game/victory';
import { victoryRequest, victoryToForm } from './victoryForm';

const settings: AdminWorldSettings = {
  name: 'Bannerfall 1',
  speed: 3,
  unitSpeed: 1,
  mapSize: 100,
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
  npcGuildsEnabled: false,
  npcGuildMaxMembers: 3,
  unitLevelsEnabled: false,
  paladinVillagesPerSlot: 4,
  paladinMaxSlots: 5,
  bannersEnabled: true,
  bannerDropFactor: 1,
  standardTheftEnabled: true,
  npcBannersEnabled: false,
  marketExchangeRatio: 1.5,
  villageNameLanguage: 'EN',
  nightBonusEnabled: false,
  nightBonusStartHour: 23,
  nightBonusEndHour: 7,
  nightBonusFactor: 2,
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
  opensAt: null,
  endsAt: null,
  endedAt: null,
};

describe('victoryRequest', () => {
  it('sends the stored settings with the victory settings of the card', () => {
    const form = { ...victoryToForm(settings), victoryMode: 'KEEPS' as const, keepCount: '4', keepPercent: '99' };
    expect(victoryRequest(settings, form)).toMatchObject({
      name: 'Bannerfall 1',
      speed: 3,
      victoryMode: 'KEEPS',
      dominationPercent: 60,
      victoryHoldHours: 48,
      keepCount: 4,
      keepPercent: 99,
    });
  });

  it('checks the ranges of the server', () => {
    const form = victoryToForm(settings);
    expect(victoryRequest(settings, { ...form, dominationPercent: '9' })).toBeNull();
    expect(victoryRequest(settings, { ...form, dominationPercent: '101' })).toBeNull();
    expect(victoryRequest(settings, { ...form, victoryHoldHours: '0' })).toBeNull();
    expect(victoryRequest(settings, { ...form, victoryHoldHours: '1.5' })).toBeNull();
    expect(victoryRequest(settings, { ...form, keepCount: '13' })).toBeNull();
    expect(victoryRequest(settings, { ...form, keepPercent: '' })).toBeNull();
    expect(victoryRequest(settings, { ...form, keepPercent: '100' })?.keepPercent).toBe(100);
  });
});

describe('keepsNeeded', () => {
  it('is the share of the keeps rounded up', () => {
    expect(keepsNeeded(5, 51)).toBe(3);
    expect(keepsNeeded(4, 51)).toBe(3);
    expect(keepsNeeded(4, 99)).toBe(4);
    expect(keepsNeeded(4, 50)).toBe(2);
    expect(keepsNeeded(5, 1)).toBe(1);
    expect(keepsNeeded(12, 51)).toBe(7);
  });
});
