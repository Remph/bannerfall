import { describe, expect, it } from 'vitest';
import { isOpenWhenEnded } from './worldEnd';

describe('isOpenWhenEnded', () => {
  it('keeps reports, messages, the password, the admin page and the simulator open', () => {
    ['/reports', '/reports/12', '/messages', '/messages/4', '/password', '/admin', '/v/3/rally/simulator'].forEach(
      (path) => expect(isOpenWhenEnded(path)).toBe(true),
    );
  });

  it('freezes the village pages, the map, the rally point and the guild', () => {
    ['/', '/v/3', '/v/3/hq', '/v/3/map', '/v/3/rally', '/v/3/farm', '/v/3/market', '/guild', '/ranking'].forEach(
      (path) => expect(isOpenWhenEnded(path)).toBe(false),
    );
  });
});
