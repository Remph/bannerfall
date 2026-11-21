// @vitest-environment jsdom
import { cleanup, fireEvent, screen } from '@testing-library/react';
import { useState } from 'react';
import { afterEach, describe, expect, it } from 'vitest';
import { meQueryKey, type Me } from '../../api/queries/auth';
import { nowQueryKey, type Now } from '../../api/queries/now';
import { renderPage } from '../../testing/renderPage';
import { NowDrawer } from './NowDrawer';

const iso = (secondsFromNow: number) => new Date(Date.now() + secondsFromNow * 1000).toISOString();
const ref = (id: number) => ({ id, name: `Dorf ${id}`, x: 40, y: 50, playerId: 7, playerName: 'ben' });

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
  villages: [{ id: 9, name: 'Dorf 9', x: 49, y: 50, points: 200, incomingAttacks: 0, tag: null }],
};

function nowWith(attack: boolean): Now {
  return {
    villages: [
      {
        village: ref(9),
        builds: [
          {
            id: 1,
            building: 'HEADQUARTERS',
            targetLevel: 6,
            startAt: iso(-60),
            endAt: iso(1200),
            demolish: false,
            automated: false,
          },
        ],
        training: [],
        dismissals: [],
        research: [],
        outgoing: [],
        incoming: attack
          ? [
              {
                id: 6,
                kind: 'ATTACK',
                sourceVillage: ref(99),
                sourcePlayerName: 'Foe',
                departAt: null,
                arriveAt: iso(2400),
                units: null,
              },
            ]
          : [],
        merchants: [],
        incomingMerchants: [],
      },
    ],
  };
}

function Host() {
  const [open, setOpen] = useState(false);
  return <NowDrawer open={open} onOpenChange={setOpen} currentVillageId={9} />;
}

function show(attack = false) {
  renderPage({
    path: '/v/:villageId',
    url: '/v/9',
    element: <Host />,
    data: [
      [nowQueryKey, nowWith(attack)],
      [meQueryKey, me],
    ],
  });
}

describe('NowDrawer', () => {
  afterEach(cleanup);

  it('is a tab that says how much runs', () => {
    show();
    const tab = screen.getByRole('button', { name: 'Now: 1 running' });
    expect(tab.getAttribute('aria-expanded')).toBe('false');
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('says that an attack is coming', () => {
    show(true);
    expect(screen.getByRole('button', { name: 'Now: 2 running, one attack is coming' })).toBeTruthy();
  });

  it('opens on a click and closes with Esc, the focus going back to the tab', () => {
    show();
    fireEvent.click(screen.getByRole('button', { name: 'Now: 1 running' }));
    const drawer = screen.getByRole('dialog', { name: 'Now' });
    expect(drawer).toBeTruthy();
    expect(document.activeElement?.tagName).toBe('H2');

    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Now: 1 running' }));
  });

  it('closes with its close button', () => {
    show();
    fireEvent.click(screen.getByRole('button', { name: 'Now: 1 running' }));
    fireEvent.click(screen.getByRole('button', { name: 'Close the Now list' }));
    expect(screen.queryByRole('dialog')).toBeNull();
  });
});
