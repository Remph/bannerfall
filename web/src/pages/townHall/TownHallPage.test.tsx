// @vitest-environment jsdom
import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client';
import { buildingsQueryKey, type BuildingInfo, type NextLevel } from '../../api/queries/buildings';
import { meQueryKey } from '../../api/queries/auth';
import { tutorialQueryKey } from '../../api/queries/tutorial';
import { villageQueryKey, type Village } from '../../api/queries/villages';
import { worldQueryKey } from '../../api/queries/world';
import { FIXTURE_NOW, me, tutorial, village, world } from '../../testing/fixtures';
import { renderPage } from '../../testing/renderPage';
import { TownHallPage } from './TownHallPage';

function next(level: number, overrides: Partial<NextLevel> = {}): NextLevel {
  return {
    level,
    cost: { wood: 300, clay: 250, iron: 200 },
    surchargePercent: 0,
    pop: 5,
    durationSeconds: 600,
    effects: [],
    unlocksBuildings: [],
    unlocksUnits: [],
    ...overrides,
  };
}

function info(building: BuildingInfo['building'], level: number, overrides: Partial<BuildingInfo> = {}): BuildingInfo {
  return {
    building,
    level,
    queuedLevel: level,
    maxLevel: 20,
    next: next(level + 1),
    requirementsMet: true,
    missing: [],
    demolish: null,
    ...overrides,
  };
}

/** The server lists them in its enum order; the page sorts them. */
const buildings: BuildingInfo[] = [
  info('HEADQUARTERS', 10),
  info('BARRACKS', 5),
  info('WOODCUTTER', 1),
  info('WAREHOUSE', 12, { next: next(13, { cost: { wood: 99999, clay: 99999, iron: 99999 } }) }),
  info('FARM', 30, { maxLevel: 30, next: null }),
  info('WALL', 0, { next: next(1), requirementsMet: false, missing: [{ building: 'BARRACKS', level: 10 }] }),
];

function open(shown: Village = village, language: 'en' | 'de' = 'en', url = `/v/${shown.id}/hq`) {
  return renderPage({
    path: '/v/:villageId/hq',
    url,
    element: <TownHallPage />,
    data: [
      [meQueryKey, me],
      [villageQueryKey(shown.id), shown],
      [buildingsQueryKey(shown.id), buildings],
      [worldQueryKey, world],
      [tutorialQueryKey, { ...tutorial, active: false, current: null }],
    ],
    language,
  });
}

/** The Town Hall (05-frontend §3): the queue next to the buildings, the upgrade buttons, the locked ones. */
describe('TownHallPage', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(FIXTURE_NOW));
    Element.prototype.scrollIntoView = vi.fn();
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('shows the village, its Town Hall level and the cards of the page', () => {
    open();

    expect(screen.getByRole('heading', { level: 1, name: 'Town Hall' })).toBeTruthy();
    expect(screen.getByText(/Aldric's village · Lv 10/)).toBeTruthy();
    expect(screen.getByRole('link', { name: /Village overview/ }).getAttribute('href')).toBe(`/v/${village.id}`);
    for (const title of ['Buildings', 'Not available yet']) {
      expect(screen.getByRole('heading', { name: title })).toBeTruthy();
    }
    expect(screen.getByRole('group', { name: 'Upgrade or demolish' })).toBeTruthy();
  });

  it('offers an upgrade for what can be built and says why the rest cannot', () => {
    open();

    expect(screen.getByRole('button', { name: 'Upgrade to Lv 11' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Upgrade to Lv 6' })).toBeTruthy();
    // the warehouse costs more than the stock holds: the button waits, with a reason next to it
    expect(screen.getByRole('button', { name: 'Upgrade to Lv 13' }).hasAttribute('disabled')).toBe(true);
    expect(screen.getByText('Maximum level reached')).toBeTruthy();
    // the locked list names the missing building
    const locked = screen.getByRole('heading', { name: 'Not available yet' }).closest('section, div') as HTMLElement;
    expect(within(locked).getByText(/Needs Barracks 10/)).toBeTruthy();
    expect(within(locked).queryByRole('button', { name: /Upgrade|Build/ })).toBeNull();
  });

  it('queues the upgrade of a building', async () => {
    const post = vi.spyOn(api, 'POST').mockResolvedValue({
      data: undefined,
      response: new Response(null, { status: 500 }),
    } as never);
    open();

    fireEvent.click(screen.getByRole('button', { name: 'Upgrade to Lv 6' }));

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('/api/villages/{villageId}/build-orders', {
        params: { path: { villageId: village.id } },
        body: { building: 'BARRACKS' },
      }),
    );
  });

  it('says once that the queue is full instead of under every button', () => {
    const order = {
      id: 1,
      building: 'WALL' as const,
      targetLevel: 1,
      startAt: '2026-10-15T20:00:00Z',
      endAt: '2026-10-15T21:00:00Z',
      demolish: false,
      automated: false,
    };
    open({ ...village, buildQueue: Array.from({ length: world.buildQueueMax }, (_, i) => ({ ...order, id: i })) });

    expect(screen.getByText(/queue is full/i)).toBeTruthy();
    for (const button of screen.getAllByRole('button', { name: /^Upgrade to/ })) {
      expect(button.hasAttribute('disabled')).toBe(true);
    }
  });

  it('switches to the demolish list', () => {
    open();

    fireEvent.click(screen.getByRole('button', { name: 'Demolish' }));

    expect(screen.queryByRole('button', { name: 'Upgrade to Lv 6' })).toBeNull();
    expect(screen.queryByRole('heading', { name: 'Not available yet' })).toBeNull();
  });

  it('scrolls to the building the village overview linked to', () => {
    open(village, 'en', `/v/${village.id}/hq#BARRACKS`);

    expect(Element.prototype.scrollIntoView).toHaveBeenCalled();
  });

  it('is shown in German too', () => {
    open(village, 'de');

    expect(screen.getByRole('heading', { level: 1, name: 'Rathaus' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Ausbau auf Stufe 6' })).toBeTruthy();
  });

  it('sends a village id that is not a number home', () => {
    renderPage({ path: '/v/:villageId/hq', url: '/v/abc/hq', element: <TownHallPage />, data: [] });

    expect(screen.queryByRole('heading', { level: 1, name: 'Town Hall' })).toBeNull();
  });
});
