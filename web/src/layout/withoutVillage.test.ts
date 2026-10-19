import { describe, expect, it } from 'vitest';
import { isOpenWithoutVillage } from './withoutVillage';

describe('isOpenWithoutVillage', () => {
  it('opens the respawn screen, the reports and the world-wide pages', () => {
    for (const path of [
      '/',
      '/reports',
      '/reports/12',
      '/ranking',
      '/players/3',
      '/statistics',
      '/about',
      '/privacy',
    ]) {
      expect(isOpenWithoutVillage(path), path).toBe(true);
    }
  });

  it('opens the messages, achievements, guild and account pages too (D10)', () => {
    for (const path of ['/messages', '/messages/7', '/achievements', '/guild', '/guilds/4', '/password', '/admin']) {
      expect(isOpenWithoutVillage(path), path).toBe(true);
    }
  });

  it('keeps every village page closed', () => {
    for (const path of ['/v/1', '/v/1/hq', '/v/1/recruit/barracks', '/v/1/map', '/v/1/rally', '/v/1/farm']) {
      expect(isOpenWithoutVillage(path), path).toBe(false);
    }
  });

  it('accepts a trailing slash but nothing deeper than a route', () => {
    expect(isOpenWithoutVillage('/messages/')).toBe(true);
    expect(isOpenWithoutVillage('/reports/12/extra')).toBe(false);
    expect(isOpenWithoutVillage('/guildhall')).toBe(false);
  });
});
