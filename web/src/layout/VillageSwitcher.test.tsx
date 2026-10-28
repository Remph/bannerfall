// @vitest-environment jsdom
import { cleanup, fireEvent, screen } from '@testing-library/react';
import { afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';
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
  respawnNearGuild: false,
  // as the server sends them: the village held longest first
  villages: [
    { id: 9, name: 'Dorf von Ben', x: 48, y: 52, points: 200, incomingAttacks: 0, tag: null },
    { id: 3, name: 'Tomburg', x: 50, y: 50, points: 100, incomingAttacks: 0, tag: null },
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

  beforeEach(() => sessionStorage.clear());

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

  describe('with village tags', () => {
    const tagged: Me['villages'] = [
      { id: 9, name: 'Dorf von Ben', x: 48, y: 52, points: 200, incomingAttacks: 0, tag: 'OFF' },
      { id: 3, name: 'Tomburg', x: 50, y: 50, points: 100, incomingAttacks: 0, tag: 'DEF' },
      { id: 4, name: 'Eschenau', x: 51, y: 50, points: 90, incomingAttacks: 0, tag: null },
    ];

    it('shows no chips while no village has a tag', () => {
      open(me.villages);

      expect(screen.queryByRole('group', { name: 'Show villages with the tag' })).toBeNull();
    });

    it('shows the tag next to the name in the top bar and on the rows', () => {
      open(tagged);

      // the trigger, then the row of the same village
      expect(screen.getAllByText('OFF')).toHaveLength(3); // trigger, chip, row
      expect(screen.getAllByText('DEF')).toHaveLength(2); // chip, row
    });

    it('filters the list by the chip, keeps the main village tag, and remembers the chip', () => {
      open(tagged);
      const chips = screen.getByRole('group', { name: 'Show villages with the tag' });
      expect(chips.textContent).toBe('AllDEFOFF');

      fireEvent.click(screen.getByRole('button', { name: 'DEF', pressed: false }));

      expect(screen.getAllByRole('link').map((link) => link.getAttribute('href'))).toEqual(['/v/3']);
      expect(screen.getByRole('button', { name: 'DEF' }).getAttribute('aria-pressed')).toBe('true');
      expect(sessionStorage.getItem('bannerfall.villageTagFilter')).toBe('DEF');

      fireEvent.click(screen.getByRole('button', { name: 'All' }));

      expect(screen.getAllByRole('link')).toHaveLength(3);
      expect(sessionStorage.getItem('bannerfall.villageTagFilter')).toBeNull();
    });

    it('opens on the chip of this session, and on "All" when nobody uses that tag any more', () => {
      sessionStorage.setItem('bannerfall.villageTagFilter', 'OFF');
      open(tagged);
      expect(screen.getAllByRole('link').map((link) => link.getAttribute('href'))).toEqual(['/v/9']);
      cleanup();

      sessionStorage.setItem('bannerfall.villageTagFilter', 'GONE');
      open(tagged);
      expect(screen.getAllByRole('link')).toHaveLength(3);
    });
  });
});
