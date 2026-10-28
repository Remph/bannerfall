import { describe, expect, it } from 'vitest';
import { keepsNeeded } from '../../game/victory';
import { victoryRequest, victoryToForm } from './victoryForm';
import { adminSettingsFixture } from '../../testing/fixtures';

const settings = adminSettingsFixture({ phase: 'OPEN' });

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
