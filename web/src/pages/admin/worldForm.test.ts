import { describe, expect, it } from 'vitest';
import type { AdminWorldSettings } from '../../api/queries/admin';
import { worldFromForm, worldToForm } from './worldForm';

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
  unitLevelsEnabled: false,
  paladinVillagesPerSlot: 4,
  paladinMaxSlots: 5,
  bannersEnabled: true,
  bannerDropFactor: 1,
  barbarianWallsEnabled: false,
  barbarianWallStartDays: 3,
  barbarianWallMaxLevel: 5,
  barbarianWallsRebuild: false,
  phase: 'OPEN',
  opensAt: null,
};

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
      unitLevelsEnabled: false,
      paladinVillagesPerSlot: 4,
      paladinMaxSlots: 5,
      bannersEnabled: true,
      bannerDropFactor: 1,
      barbarianWallsEnabled: null,
      barbarianWallStartDays: null,
      barbarianWallMaxLevel: null,
      barbarianWallsRebuild: null,
      mapSize: null,
      barbarianInitialCount: null,
      npcLordCount: null,
    });
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

  it('sends map size, barbarians and lords only in the pre-game, and checks them then', () => {
    const form = worldToForm(settings);
    expect(worldFromForm(form, true)).toMatchObject({ mapSize: 100, barbarianInitialCount: 30, npcLordCount: 4 });
    expect(worldFromForm(form)).toMatchObject({ mapSize: null, barbarianInitialCount: null, npcLordCount: null });
    expect(worldFromForm({ ...form, mapSize: '10' }, true)).toBeNull();
    expect(worldFromForm({ ...form, npcLordCount: '51' }, true)).toBeNull();
    // fixed in an open world: a stale value in the form does not block saving the rest
    expect(worldFromForm({ ...form, mapSize: '10' })).not.toBeNull();
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
