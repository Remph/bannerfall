// @vitest-environment jsdom
import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client';
import { meQueryKey, type Me } from '../../api/queries/auth';
import { farmQueryKey, type FarmTarget } from '../../api/queries/farm';
import { farmTemplatesQueryKey } from '../../api/queries/farmTemplates';
import { worldQueryKey } from '../../api/queries/world';
import type { MapVillage } from '../../api/queries/map';
import type { Village } from '../../api/queries/villages';
import { renderPage } from '../../testing/renderPage';
import { village as fixtureVillage } from '../../testing/fixtures';
import { FarmLine } from './FarmLine';

const NOW = '2026-10-18T12:00:00Z';

const me: Me = {
  accountId: 1,
  username: 'ada',
  role: 'PLAYER',
  playerId: 7,
  points: 1200,
  protectedUntil: '2026-10-12T00:00:00Z',
  needsRespawn: false,
  villages: [],
};

const home: Village = { ...fixtureVillage, unitsHome: { AXEMAN: 20, LIGHT_CAVALRY: 2 } };

const barbarian: MapVillage = {
  id: 90,
  x: 63,
  y: 57,
  name: null,
  playerId: null,
  playerName: null,
  npc: false,
  points: 26,
  loyalty: null,
  loyaltySeenAt: null,
  guildId: null,
  guildTag: null,
  nextBannerRollAt: null,
  keep: false,
  note: null,
};

const templates = [
  { letter: 'A', units: { AXEMAN: 10 } },
  { letter: 'B', units: { LIGHT_CAVALRY: 5 } },
];

function open({
  target = barbarian,
  account = me,
  targets = [] as FarmTarget[],
  list = templates as unknown[],
  world = { nightBonus: null } as object,
} = {}) {
  return renderPage({
    path: '/',
    url: '/',
    element: <FarmLine village={target} currentVillage={home} />,
    data: [
      [meQueryKey, account],
      [farmQueryKey(home.id), targets],
      [farmTemplatesQueryKey, list],
      [worldQueryKey, world],
    ],
  });
}

function mockSend() {
  return vi.spyOn(api, 'POST').mockResolvedValue({
    data: { kind: 'ATTACK', arriveAt: '2026-10-18T12:20:00Z' },
    response: new Response(),
  } as never);
}

describe('FarmLine', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(NOW));
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('greys out a template the units at home do not cover', () => {
    open();

    expect((screen.getByRole('button', { name: 'Send template A now' }) as HTMLButtonElement).disabled).toBe(false);
    expect((screen.getByRole('button', { name: 'Send template B now' }) as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByText('Greyed out: not enough troops at home.')).toBeTruthy();
  });

  it("sends a template's units as an attack with one tap", async () => {
    const post = mockSend();
    open();

    fireEvent.click(screen.getByRole('button', { name: 'Send template A now' }));

    await waitFor(() => expect(post).toHaveBeenCalledTimes(1));
    expect(post.mock.calls[0]?.[1]).toMatchObject({
      params: { path: { villageId: home.id } },
      body: {
        kind: 'ATTACK',
        target: { x: barbarian.x, y: barbarian.y },
        units: templates[0]?.units,
        catapultTarget: null,
      },
    });
    expect(await screen.findByRole('status')).toBeTruthy();
  });

  it('asks for a second tap when the attack would end the beginner protection', async () => {
    const post = mockSend();
    const player = { ...barbarian, playerId: 12, playerName: 'Bo', name: "Bo's village" };
    open({ target: player, account: { ...me, protectedUntil: '2026-10-20T00:00:00Z' } });

    fireEvent.click(screen.getByRole('button', { name: 'Send template A now' }));
    expect(post).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Send anyway?' }));

    await waitFor(() => expect(post).toHaveBeenCalledTimes(1));
  });

  describe('night bonus (1.4)', () => {
    // 12:00 UTC is 14:00 in Berlin: a night of 14:00–15:00 for the test
    const nightBonus = { startHour: 14, endHour: 15, factor: 2, zone: 'Europe/Berlin' };
    const lord = { ...barbarian, playerId: 30, playerName: 'Lord Eckhart', npc: true, name: 'Eckhart' };
    // A arrives at 14:10 (night), C at 15:00 (day: the end hour no longer counts)
    const night = [
      { letter: 'A', units: { AXEMAN: 10 } },
      { letter: 'B', units: { LIGHT_CAVALRY: 5 } },
      { letter: 'C', units: { AXEMAN: 5, LIGHT_CAVALRY: 1 } },
    ];

    function mockPreviews() {
      return vi.spyOn(api, 'POST').mockImplementation(((
        _path: string,
        init: { body: { units: Record<string, number> } },
      ) =>
        Promise.resolve({
          data: { errors: [], travelSeconds: init.body.units.LIGHT_CAVALRY === undefined ? 600 : 3600 },
          response: new Response(),
        })) as never);
    }

    it('names the templates that would arrive in it at an owned village', async () => {
      const post = mockPreviews();
      open({ target: lord, list: night, world: { nightBonus } });

      expect(
        await screen.findByText('With A the attack arrives during the night bonus: the village defends ×2.'),
      ).toBeTruthy();
      // B doesn't fit the units at home, so it isn't asked for
      expect(post).toHaveBeenCalledTimes(2);
    });

    it('asks nothing at a barbarian village', () => {
      const post = mockPreviews();
      open({ list: night, world: { nightBonus } });

      expect(post).not.toHaveBeenCalled();
      expect(screen.queryByText(/night bonus/)).toBeNull();
    });
  });

  it('links to the templates when there are none', () => {
    open({ list: [] });

    expect(screen.getByText('No templates yet.')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Create templates' }).getAttribute('href')).toBe(
      `/v/${home.id}/farm#templates`,
    );
  });

  it('shows a listed village with its template instead of the add button', () => {
    const listed: FarmTarget = {
      id: 3,
      target: { id: barbarian.id, name: null, x: barbarian.x, y: barbarian.y, playerId: null, playerName: null },
      units: { LIGHT_CAVALRY: 5 },
      template: 'B',
      distance: 2.2,
      travelSeconds: 600,
      protectedUntil: null,
      lastSentAt: null,
      nextBannerRollAt: null,
      bannerChance: false,
      note: null,
    };
    open({ targets: [listed] });

    expect(screen.getByText('On the farm list').closest('a')).toBeTruthy();
    expect(screen.getByText('Template B')).toBeTruthy();
  });
});
