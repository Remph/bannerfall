// @vitest-environment jsdom
import { cleanup, fireEvent, screen } from '@testing-library/react';
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
  respawnNearGuild: false,
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
  morale: null,
  haulCapacity: 150,
  lastRaid: null,
};

/** Renders the row and opens it (1.6): the full texts are in the opened part. */
function open(target: FarmTarget, nightBonus: object | null) {
  render(target, nightBonus);
  fireEvent.click(screen.getByRole('button', { expanded: false }));
}

function render(target: FarmTarget, nightBonus: object | null = null) {
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

describe('TargetRow morale (1.6)', () => {
  afterEach(cleanup);

  it('says so below full strength and stays quiet otherwise', () => {
    open({ ...lordTarget, morale: 0.48 }, null);
    expect(
      screen.getByText(
        'Morale 48%: the owner has far fewer points than you, so your attack hits with 48% of its strength.',
      ),
    ).toBeTruthy();
    cleanup();

    open(lordTarget, null);
    expect(screen.queryByText(/^Morale/)).toBeNull();
  });
});

describe('TargetRow compact row (1.6)', () => {
  afterEach(cleanup);

  it('opens on a tap of the row and closes again; Edit and Remove are inside', () => {
    render(lordTarget);
    const row = screen.getByRole('button', { expanded: false });
    expect(screen.queryByRole('button', { name: /Edit/ })).toBeNull();

    fireEvent.click(row);
    expect(row.getAttribute('aria-expanded')).toBe('true');
    expect(screen.getByRole('button', { name: /Edit/ })).toBeTruthy();
    expect(screen.getByRole('button', { name: /Remove/ })).toBeTruthy();
    expect(screen.getByText('The units carry up to 150 resources home.')).toBeTruthy();

    fireEvent.click(row);
    expect(screen.queryByRole('button', { name: /Edit/ })).toBeNull();
  });

  it('shows what the units can carry until a raid landed, then the haul over it', () => {
    render(lordTarget);
    expect(screen.getByText('up to 150')).toBeTruthy();
    cleanup();

    render({ ...lordTarget, lastRaid: { at: '2026-10-21T07:00:00Z', won: true, loot: 150, full: true } });
    expect(screen.getByText('/150')).toBeTruthy();
    // all they could carry: in the success colour
    expect(screen.getByText('150').className).toMatch(/haulFull/);
    cleanup();

    render({ ...lordTarget, lastRaid: { at: '2026-10-21T07:00:00Z', won: true, loot: 40, full: false } });
    expect(screen.getByText('40').className).not.toMatch(/haulFull/);
    cleanup();

    render({ ...lordTarget, lastRaid: { at: '2026-10-21T07:00:00Z', won: false, loot: 0, full: false } });
    expect(screen.getByText('lost')).toBeTruthy();
  });

  it('says why Send asks "Send anyway?" without opening, while the own protection lasts', () => {
    renderPage({
      path: '/',
      url: '/',
      element: <TargetRow villageId={VILLAGE_ID} target={lordTarget} home={{ AXEMAN: 20 }} />,
      data: [
        [meQueryKey, { ...me, protectedUntil: '2099-01-01T00:00:00Z' }],
        [farmTemplatesQueryKey, []],
        [worldQueryKey, { nightBonus: null }],
      ],
    });
    expect(screen.queryByText('Attacking ends your own beginner protection.')).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Send the usual troops to Eckhart' }));
    expect(screen.getByRole('button', { name: 'Send anyway?' })).toBeTruthy();
    expect(screen.getByText('Attacking ends your own beginner protection.')).toBeTruthy();
  });

  it('shows the morale as a small mark without opening', () => {
    render({ ...lordTarget, morale: 0.48 });

    expect(screen.getByText('48%')).toBeTruthy();
  });
});
