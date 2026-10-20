// @vitest-environment jsdom
import { cleanup, fireEvent, screen } from '@testing-library/react';
import { afterEach, beforeAll, describe, expect, it } from 'vitest';
import { meQueryKey, type Me } from '../../api/queries/auth';
import { mapChunkQueryKey, type MapVillage } from '../../api/queries/map';
import type { Market } from '../../api/queries/market';
import { worldQueryKey } from '../../api/queries/world';
import { village } from '../../testing/fixtures';
import { renderPage } from '../../testing/renderPage';
import { SendResourcesCard } from './SendResourcesCard';

/** Tomburg, the player's second village: 9 fields east of Aldric's village, in the same map chunk. */
const tomburg: MapVillage = {
  id: 3,
  x: 70,
  y: 58,
  name: 'Tomburg',
  playerId: 7,
  playerName: 'aldric',
  npc: false,
  points: 100,
  loyalty: 100,
  loyaltySeenAt: null,
  guildId: null,
  guildTag: null,
  nextBannerRollAt: null,
  keep: false,
  note: null,
};

const me: Me = {
  accountId: 1,
  username: 'aldric',
  role: 'PLAYER',
  playerId: 7,
  points: 300,
  protectedUntil: '2026-10-10T00:00:00Z',
  needsRespawn: false,
  linked: false,
  villages: [
    { id: 59, name: "Aldric's village", x: 61, y: 58, points: 200, incomingAttacks: 0 },
    { id: 3, name: 'Tomburg', x: 70, y: 58, points: 100, incomingAttacks: 0 },
  ],
};

const market: Market = {
  merchants: { total: 5, free: 5 },
  outgoing: [],
  incoming: [],
  offers: [],
  exchange: { ratio: 1, dailyCap: 0, received: { wood: 0, clay: 0, iron: 0 }, resetsAt: '2026-10-21T00:00:00Z' },
};

describe('SendResourcesCard', () => {
  beforeAll(() => {
    // jsdom has no modal dialogs; a tap on a village closes the sheet again
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
      this.open = false;
    };
  });

  afterEach(cleanup);

  it('fills the target with an own village picked from the sheet, with its travel time', () => {
    renderPage({
      path: '/v/:villageId/market',
      url: '/v/59/market',
      element: <SendResourcesCard village={village} market={market} />,
      // merchants take six minutes per field at speed 1
      data: [
        [meQueryKey, me],
        [worldQueryKey, { speed: 1, unitSpeed: 1, mapSize: 100 }],
        [mapChunkQueryKey({ cx: 3, cy: 2 }), [tomburg]],
      ],
    });

    fireEvent.click(screen.getByRole('button', { name: 'Own villages' }));
    fireEvent.click(screen.getByRole('button', { name: 'Tomburg(70|58) · 9.0 fields · 0:54:00' }));

    expect((screen.getByRole('textbox', { name: 'Target' }) as HTMLInputElement).value).toBe('70|58');
    expect(screen.getByText('Tomburg · aldric · 9.0 fields')).toBeTruthy();
    expect(screen.getByText('Travel time').nextElementSibling?.textContent).toBe('0:54:00');
  });
});
