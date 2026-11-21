// @vitest-environment jsdom
import { cleanup, fireEvent, screen, within } from '@testing-library/react';
import { afterEach, beforeAll, describe, expect, it } from 'vitest';
import { meQueryKey, type Me } from '../../api/queries/auth';
import { nowQueryKey, type Now } from '../../api/queries/now';
import { renderPage } from '../../testing/renderPage';
import { NowList } from './NowList';

const iso = (secondsFromNow: number) => new Date(Date.now() + secondsFromNow * 1000).toISOString();
const ref = (id: number, name: string | null) => ({ id, name, x: 40 + id, y: 50, playerId: 7, playerName: 'ben' });

const me: Me = {
  accountId: 1,
  username: 'ben',
  role: 'PLAYER',
  playerId: 7,
  points: 300,
  protectedUntil: iso(-3600),
  needsRespawn: false,
  linked: false,
  respawnNearGuild: false,
  villages: [
    { id: 9, name: 'Wulfsfeld', x: 49, y: 50, points: 200, incomingAttacks: 0, tag: null },
    { id: 10, name: 'Bertramsfeld', x: 50, y: 50, points: 100, incomingAttacks: 1, tag: null },
  ],
};

function village(id: number, name: string) {
  return {
    village: ref(id, name),
    builds: [],
    training: [],
    dismissals: [],
    research: [],
    outgoing: [],
    incoming: [],
    merchants: [],
    incomingMerchants: [],
  };
}

const now: Now = {
  villages: [
    {
      ...village(9, 'Wulfsfeld'),
      builds: [
        {
          id: 1,
          building: 'HEADQUARTERS',
          targetLevel: 6,
          startAt: iso(-600),
          endAt: iso(1200),
          demolish: false,
          automated: false,
        },
        {
          id: 2,
          building: 'FARM',
          targetLevel: 3,
          startAt: iso(1200),
          endAt: iso(3000),
          demolish: false,
          automated: false,
        },
      ],
      training: [
        {
          id: 3,
          building: 'BARRACKS',
          unit: 'AXEMAN',
          count: 20,
          completedCount: 4,
          secondsPerUnit: 60,
          startAt: iso(-300),
          endAt: iso(900),
        },
      ],
      outgoing: [
        {
          id: 4,
          kind: 'ATTACK',
          sourceVillage: ref(9, 'Wulfsfeld'),
          targetVillage: { id: 90, name: null, x: 60, y: 61, playerId: null, playerName: null },
          homeVillage: ref(9, 'Wulfsfeld'),
          units: { AXEMAN: 10 },
          catapultTarget: null,
          loot: { wood: 0, clay: 0, iron: 0 },
          departAt: iso(-60),
          arriveAt: iso(1500),
          cancellableUntil: iso(500),
          notifyReturn: false,
        },
      ],
    },
    {
      ...village(10, 'Bertramsfeld'),
      builds: [
        {
          id: 5,
          building: 'WAREHOUSE',
          targetLevel: 9,
          startAt: iso(-100),
          endAt: iso(700),
          demolish: false,
          automated: false,
        },
      ],
      incoming: [
        {
          id: 6,
          kind: 'ATTACK',
          sourceVillage: ref(99, 'Feindburg'),
          sourcePlayerName: 'Foe',
          departAt: null,
          arriveAt: iso(2400),
          units: null,
        },
      ],
    },
  ],
};

function show(scope?: 'all') {
  if (scope === 'all') {
    localStorage.setItem('bannerfall.nowScope', 'all');
  }
  return renderPage({
    path: '/v/:villageId',
    url: '/v/9',
    element: <NowList currentVillageId={9} />,
    data: [
      [nowQueryKey, now],
      [meQueryKey, me],
    ],
  });
}

describe('NowList', () => {
  beforeAll(() => {
    // jsdom has no modal dialogs (the cancel sheet)
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.open = true;
    };
  });
  afterEach(() => {
    cleanup();
    localStorage.clear();
  });

  it('shows the current village’s queue, training and commands, and the attacks on every village', () => {
    show();
    expect(screen.getByRole('heading', { name: 'Now' })).toBeTruthy();
    const attacks = screen.getByRole('region', { name: 'Attacks on you' });
    // the attack on the other village is not hidden by the "this village" scope
    expect(within(attacks).getByText(/Attack from Feindburg/)).toBeTruthy();
    // and it says which village it is coming to, though the scope is this village only
    expect(within(attacks).getByText('Bertramsfeld')).toBeTruthy();
    const building = screen.getByRole('region', { name: 'Being built' });
    expect(within(building).getByText(/Town Hall/)).toBeTruthy();
    expect(within(building).getByText(/Farm/)).toBeTruthy();
    // the second order of the queue waits
    expect(within(building).getByText('waits')).toBeTruthy();
    // the other village's warehouse is not in this scope
    expect(within(building).queryByText(/Warehouse/)).toBeNull();
    const training = screen.getByRole('region', { name: 'In training' });
    expect(within(training).getByText(/20 × Axeman/)).toBeTruthy();
    const way = screen.getByRole('region', { name: 'On the way' });
    expect(within(way).getByText(/^Attack on Barbarian village/)).toBeTruthy();
  });

  it('switches to all villages and tags each line with its village', () => {
    show();
    fireEvent.click(screen.getByRole('button', { name: 'All villages' }));
    const building = screen.getByRole('region', { name: 'Being built' });
    expect(within(building).getByText(/Warehouse/)).toBeTruthy();
    expect(within(building).getAllByText('Bertramsfeld')).toHaveLength(1);
    expect(localStorage.getItem('bannerfall.nowScope')).toBe('all');
  });

  it('names the switch’s choices short and tags only the attacks on another village (2.0 UI review)', () => {
    const withHomeAttack: Now = {
      villages: now.villages.map((entry) =>
        entry.village.id === 9
          ? {
              ...entry,
              incoming: [
                {
                  id: 7,
                  kind: 'ATTACK',
                  sourceVillage: ref(98, 'Raubnest'),
                  sourcePlayerName: 'Foe',
                  departAt: null,
                  arriveAt: iso(1800),
                  units: null,
                },
              ],
            }
          : entry,
      ),
    };
    renderPage({
      path: '/v/:villageId',
      url: '/v/9',
      element: <NowList currentVillageId={9} />,
      data: [
        [nowQueryKey, withHomeAttack],
        [meQueryKey, me],
      ],
    });
    // "This village", not the village's name: a long name took two lines
    expect(screen.getByRole('button', { name: 'This village', pressed: true })).toBeTruthy();
    const attacks = screen.getByRole('region', { name: 'Attacks on you' });
    const rows = within(attacks).getAllByRole('listitem');
    const home = rows.find((row) => row.textContent?.includes('Raubnest'));
    const other = rows.find((row) => row.textContent?.includes('Feindburg'));
    // the attack on the village you look at doesn't repeat its name, the one on the other village does
    expect(home?.textContent).not.toContain('Wulfsfeld');
    expect(other?.textContent).toContain('Bertramsfeld');
    // "On the map" ends the second line instead of standing alone under it
    const link = within(home as HTMLElement).getByRole('link', { name: /On the map/ });
    expect(link.parentElement?.textContent).toMatch(/arrives/);
  });

  it('remembers all villages', () => {
    show('all');
    expect(screen.getByRole('button', { name: 'All villages', pressed: true })).toBeTruthy();
  });

  it('offers to cancel a command that is still young, with a countdown', () => {
    show();
    const button = screen.getByRole('button', { name: /Cancel/ });
    expect(button.textContent).toMatch(/\d+:\d\d/);
  });

  it('shows another player’s merchants on their way here, with what they bring (2.1)', () => {
    renderPage({
      path: '/v/:villageId',
      url: '/v/9',
      element: <NowList currentVillageId={9} />,
      data: [
        [
          nowQueryKey,
          {
            villages: [
              {
                ...village(9, 'Wulfsfeld'),
                incomingMerchants: [
                  {
                    id: 30,
                    sourceVillage: { id: 70, name: 'Marastein', x: 30, y: 31, playerId: 8, playerName: 'Mara' },
                    targetVillage: ref(9, 'Wulfsfeld'),
                    resources: { wood: 1200, clay: 0, iron: 300 },
                    merchants: 2,
                    departAt: iso(-600),
                    arriveAt: iso(600),
                    returnAt: iso(1800),
                    arrived: false,
                  },
                ],
              },
            ],
          },
        ],
        [meQueryKey, me],
      ],
    });
    const way = screen.getByRole('region', { name: 'On the way' });
    expect(within(way).getByText(/^Merchants from Marastein/)).toBeTruthy();
    expect(within(way).getByText(/^Mara ·/)).toBeTruthy();
    // what they bring, without the empty clay
    expect(within(way).getByText('1,200')).toBeTruthy();
    expect(within(way).getByText('300')).toBeTruthy();
  });

  it('says so when nothing is running', () => {
    renderPage({
      path: '/v/:villageId',
      url: '/v/9',
      element: <NowList currentVillageId={9} />,
      data: [
        [nowQueryKey, { villages: [village(9, 'Wulfsfeld')] }],
        [meQueryKey, me],
      ],
    });
    expect(screen.getByText('Nothing is running.')).toBeTruthy();
  });

  it('shows the beginner protection with its countdown', () => {
    renderPage({
      path: '/v/:villageId',
      url: '/v/9',
      element: <NowList currentVillageId={9} />,
      data: [
        [nowQueryKey, { villages: [village(9, 'Wulfsfeld')] }],
        [meQueryKey, { ...me, protectedUntil: iso(7200) }],
      ],
    });
    expect(screen.getByText('Beginner protection')).toBeTruthy();
    expect(screen.queryByText('Nothing is running.')).toBeNull();
  });
});
