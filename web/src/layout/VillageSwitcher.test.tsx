// @vitest-environment jsdom
import { cleanup, fireEvent, screen } from '@testing-library/react';
import { afterEach, beforeAll, describe, expect, it } from 'vitest';
import { meQueryKey, type Me } from '../api/queries/auth';
import { renderPage } from '../testing/renderPage';
import { VillageSwitcher } from './VillageSwitcher';

const me: Me = {
  accountId: 1,
  username: 'ben',
  role: 'PLAYER',
  playerId: 7,
  points: 300,
  protectedUntil: '2026-10-10T00:00:00Z',
  needsRespawn: false,
  linked: false,
  // as the server sends them: the village held longest first
  villages: [
    { id: 9, name: 'Dorf von Ben', x: 48, y: 52, points: 200, incomingAttacks: 0 },
    { id: 3, name: 'Tomburg', x: 50, y: 50, points: 100, incomingAttacks: 0 },
  ],
};

function open(villages: Me['villages']) {
  renderPage({
    path: '/v/:villageId',
    url: '/v/9',
    element: <VillageSwitcher current={villages[0]} />,
    data: [[meQueryKey, { ...me, villages }]],
  });
  fireEvent.click(screen.getByRole('button', { name: /Dorf von Ben/ }));
}

describe('VillageSwitcher', () => {
  beforeAll(() => {
    // jsdom has no modal dialogs
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.open = true;
    };
  });

  afterEach(cleanup);

  it('lists the villages in the server’s order and tags the first as the main village', () => {
    open(me.villages);

    const links = screen.getAllByRole('link');
    expect(links.map((link) => link.getAttribute('href'))).toEqual(['/v/9', '/v/3']);
    expect(links[0]?.textContent).toContain('Main village');
    expect(links[1]?.textContent).not.toContain('Main village');
  });

  it('has no tag while there is only one village', () => {
    open(me.villages.slice(0, 1));

    expect(screen.queryByText('Main village')).toBeNull();
  });
});
