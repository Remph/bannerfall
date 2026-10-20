// @vitest-environment jsdom
import { cleanup, screen } from '@testing-library/react';
import { afterEach, beforeAll, describe, expect, it } from 'vitest';
import { meQueryKey, type Me } from '../api/queries/auth';
import { renderPage } from '../testing/renderPage';
import { MenuSheet, type MenuKind } from './MenuSheet';

const PLAYER_ID = 7;

const me: Me = {
  accountId: 1,
  username: 'ben',
  role: 'PLAYER',
  playerId: PLAYER_ID,
  points: 300,
  protectedUntil: '2026-10-10T00:00:00Z',
  needsRespawn: false,
  linked: false,
  villages: [{ id: 9, name: 'Dorf von Ben', x: 48, y: 52, points: 300, incomingAttacks: 0 }],
};

function open(kind: MenuKind, { profile, url = '/v/9' }: { profile?: boolean; url?: string }) {
  renderPage({
    path: '*',
    url,
    element: <MenuSheet kind={kind} villageId={9} onClose={() => {}} onOpenChat={() => {}} profile={profile} />,
    data: [[meQueryKey, me]],
  });
}

describe('MenuSheet: the own profile (1.5.1)', () => {
  beforeAll(() => {
    // jsdom has no modal dialogs
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.open = true;
    };
  });
  afterEach(cleanup);

  it('opens the gear menu with a row to the own profile on desktop', () => {
    open('settings', { profile: true });
    expect(screen.getByRole('link', { name: 'My profile' }).getAttribute('href')).toBe(`/players/${PLAYER_ID}`);
  });

  it('puts it first among the player pages of More on phones, once', () => {
    open('more', { profile: true });
    const links = screen.getAllByRole('link', { name: 'My profile' });
    expect(links).toHaveLength(1);
    expect(links[0]?.getAttribute('href')).toBe(`/players/${PLAYER_ID}`);
    const playerGroup = screen.getByRole('list', { name: 'Player' });
    expect(playerGroup.querySelector('a')?.textContent).toBe('My profile');
  });

  it('has no profile row in the pre-game, which has no profile pages', () => {
    open('settings', {});
    expect(screen.queryByRole('link', { name: 'My profile' })).toBeNull();
  });
});

describe('MenuSheet: the open page is marked (1.5.1)', () => {
  beforeAll(() => {
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.open = true;
    };
  });
  afterEach(cleanup);

  const current = (name: string) => screen.getByRole('link', { name }).getAttribute('aria-current');

  it('marks the profile row of the gear menu on the own profile', () => {
    open('settings', { profile: true, url: `/players/${PLAYER_ID}` });
    expect(current('My profile')).toBe('page');
    expect(current('Notifications')).toBeNull();
  });

  it('marks the profile tile in More on the own profile, and the ranking on somebody else’s', () => {
    open('more', { profile: true, url: `/players/${PLAYER_ID}` });
    expect(current('My profile')).toBe('page');
    expect(current('Ranking')).toBeNull();
    cleanup();
    open('more', { profile: true, url: '/players/8' });
    expect(current('My profile')).toBeNull();
    expect(current('Ranking')).toBe('page');
  });

  it('marks the Town Hall tile on its page', () => {
    open('more', { profile: true, url: '/v/9/hq' });
    expect(current('Town Hall')).toBe('page');
  });
});
