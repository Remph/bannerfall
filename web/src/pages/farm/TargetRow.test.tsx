// @vitest-environment jsdom
import { cleanup, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { meQueryKey, type Me } from '../../api/queries/auth';
import type { FarmTarget } from '../../api/queries/farm';
import { farmTemplatesQueryKey } from '../../api/queries/farmTemplates';
import { worldQueryKey } from '../../api/queries/world';
import { renderPage } from '../../testing/renderPage';
import { TargetRow } from './TargetRow';

/** 14:00 in Berlin. */
const NOW = '2026-10-18T12:00:00Z';
const VILLAGE_ID = 5;
const NIGHT_TEXT = 'It arrives during the night bonus: the village defends ×2.';
/** A night of 14:00–15:00 for the test. */
const NIGHT = { startHour: 14, endHour: 15, factor: 2, zone: 'Europe/Berlin' };

const me: Me = {
  accountId: 1,
  username: 'ada',
  role: 'PLAYER',
  playerId: 7,
  points: 1200,
  protectedUntil: '2026-10-12T00:00:00Z',
  needsRespawn: false,
  linked: false,
  villages: [],
};

/** A lord's village 10 minutes away. */
const lordTarget: FarmTarget = {
  id: 3,
  target: { id: 90, name: 'Eckhart', x: 63, y: 57, playerId: 30, playerName: 'Lord Eckhart' },
  units: { AXEMAN: 10 },
  template: null,
  distance: 2.2,
  travelSeconds: 600,
  protectedUntil: null,
  lastSentAt: null,
  nextBannerRollAt: null,
  bannerChance: false,
  note: null,
};

function open(target: FarmTarget, nightBonus: object | null) {
  renderPage({
    path: '/',
    url: '/',
    element: <TargetRow villageId={VILLAGE_ID} target={target} home={{ AXEMAN: 20 }} />,
    data: [
      [meQueryKey, me],
      [farmTemplatesQueryKey, []],
      [worldQueryKey, { nightBonus }],
    ],
  });
}

describe('TargetRow night bonus (1.4)', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(NOW));
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it('says so when the farm run would arrive in it at an owned village', () => {
    open(lordTarget, NIGHT);

    expect(screen.getByText(NIGHT_TEXT)).toBeTruthy();
  });

  it('stays quiet by day, at barbarians and without the bonus', () => {
    // arrives at 15:00, the end hour no longer counts
    open({ ...lordTarget, travelSeconds: 3600 }, NIGHT);
    expect(screen.queryByText(NIGHT_TEXT)).toBeNull();
    cleanup();

    const barbarian = { ...lordTarget.target, name: null, playerId: null, playerName: null };
    open({ ...lordTarget, target: barbarian }, NIGHT);
    expect(screen.queryByText(NIGHT_TEXT)).toBeNull();
    cleanup();

    open(lordTarget, null);
    expect(screen.queryByText(NIGHT_TEXT)).toBeNull();
  });
});
