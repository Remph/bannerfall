// @vitest-environment jsdom
import { cleanup, fireEvent, screen } from '@testing-library/react';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { meQueryKey, type Me } from '../../api/queries/auth';
import { worldQueryKey } from '../../api/queries/world';
import type { Coords } from '../../game/coords';
import { village } from '../../testing/fixtures';
import { renderPage } from '../../testing/renderPage';
import { OwnVillagePicker } from './OwnVillagePicker';

/** The other own villages' fields: Tomburg 9 fields east, Eschenau 5 fields away. */
const tomburg = { x: 70, y: 58 };
const eschenau = { x: 64, y: 62 };

const me: Me = {
  accountId: 1,
  username: 'aldric',
  role: 'PLAYER',
  playerId: 7,
  points: 300,
  protectedUntil: '2026-10-10T00:00:00Z',
  needsRespawn: false,
  linked: false,
  respawnNearGuild: false,
  villages: [
    { id: 59, name: "Aldric's village", x: 61, y: 58, points: 200, incomingAttacks: 0, tag: null },
    { id: 3, name: 'Tomburg', ...tomburg, points: 100, incomingAttacks: 0, tag: null },
    { id: 4, name: 'Eschenau', ...eschenau, points: 90, incomingAttacks: 0, tag: null },
  ],
};

function show(villages: Me['villages'], target?: Coords) {
  const onPick = vi.fn();
  renderPage({
    path: '/v/:villageId/market',
    url: '/v/59/market',
    element: <OwnVillagePicker village={village} target={target} onPick={onPick} />,
    // merchants take six minutes per field at speed 1
    data: [
      [meQueryKey, { ...me, villages }],
      [worldQueryKey, { speed: 1, unitSpeed: 1 }],
    ],
  });
  return onPick;
}

describe('OwnVillagePicker', () => {
  beforeAll(() => {
    // jsdom has no modal dialogs
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.open = true;
    };
  });

  afterEach(cleanup);

  it('lists the other own villages nearest first and fills the target with a tap', () => {
    const onPick = show(me.villages);

    fireEvent.click(screen.getByRole('button', { name: 'Own villages' }));
    const rows = screen.getAllByRole('button', { pressed: false });
    expect(rows.map((row) => row.textContent)).toEqual([
      'Eschenau(64|62) · 5.0 fields · 0:30:00',
      'Tomburg(70|58) · 9.0 fields · 0:54:00',
    ]);

    fireEvent.click(screen.getByRole('button', { name: 'Tomburg(70|58) · 9.0 fields · 0:54:00' }));
    expect(onPick).toHaveBeenCalledWith(tomburg);
  });

  it('marks the village the target field points at', () => {
    show(me.villages, tomburg);

    fireEvent.click(screen.getByRole('button', { name: 'Own villages' }));
    const pressed = screen.getAllByRole('button', { pressed: true });
    expect(pressed.map((row) => row.textContent)).toEqual(['Tomburg(70|58) · 9.0 fields · 0:54:00']);
  });

  it('is not there while the player has only this village', () => {
    show(me.villages.slice(0, 1));

    expect(screen.queryByRole('button', { name: 'Own villages' })).toBeNull();
  });
});
