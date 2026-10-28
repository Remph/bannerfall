import { describe, expect, it } from 'vitest';
import { barbarianWallsRequest, barbarianWallsToForm } from './barbarianWallsForm';
import { adminSettingsFixture } from '../../testing/fixtures';

const settings = adminSettingsFixture({ phase: 'OPEN' });

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
      guildBannersEnabled: false,
      tutorialEnabled: true,
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
