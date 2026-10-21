// @vitest-environment jsdom
import { act, cleanup, fireEvent, screen, within } from '@testing-library/react';
import { afterEach, beforeAll, describe, expect, it } from 'vitest';
import { meQueryKey, type Me } from '../api/queries/auth';
import { getIconStyle, setIconStyle } from '../components/iconStyle';
import { renderPage } from '../testing/renderPage';
import { applyPalette } from '../theme/theme';
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
  respawnNearGuild: false,
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

describe('MenuSheet: the source code link (1.6)', () => {
  beforeAll(() => {
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.open = true;
    };
  });
  afterEach(cleanup);

  it.each<[MenuKind, boolean]>([
    ['more', true],
    ['settings', true],
    ['settings', false],
  ])('has a GitHub row to the public repository in %s (profile: %s), opening a new tab', (kind, profile) => {
    open(kind, { profile });
    const link = screen.getByRole('link', { name: 'GitHub' });
    expect(link.getAttribute('href')).toBe('https://github.com/Remph/bannerfall');
    expect(link.getAttribute('target')).toBe('_blank');
  });
});

describe('MenuSheet: medieval or classic icons (1.6)', () => {
  beforeAll(() => {
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.open = true;
    };
  });
  afterEach(() => {
    cleanup();
    act(() => setIconStyle('medieval'));
  });

  it('switches to the classic icons and back in the settings', () => {
    open('settings', {});
    const group = screen.getByRole('group', { name: 'Icons' });
    const classic = within(group).getByRole('button', { name: 'Classic' });
    expect(within(group).getByRole('button', { name: 'Medieval' }).getAttribute('aria-pressed')).toBe('true');
    fireEvent.click(classic);
    expect(getIconStyle()).toBe('classic');
    expect(classic.getAttribute('aria-pressed')).toBe('true');
    fireEvent.click(within(group).getByRole('button', { name: 'Medieval' }));
    expect(getIconStyle()).toBe('medieval');
  });
});

describe('MenuSheet: colour themes (1.6)', () => {
  beforeAll(() => {
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.open = true;
    };
  });
  afterEach(() => {
    cleanup();
    applyPalette('green');
  });

  it('offers the five themes, green chosen by default, and switches the page on a tap', () => {
    open('settings', {});
    const group = screen.getByRole('radiogroup', { name: 'Colours' });
    const choices = within(group).getAllByRole('radio');
    expect(choices.map((choice) => choice.getAttribute('aria-label'))).toEqual([
      'Green banner',
      'Royal blue',
      'Black and gold',
      'Purple',
      'Leather and forest',
    ]);
    expect(screen.getByRole('radio', { name: 'Green banner' })).toHaveProperty('checked', true);
    expect(screen.getByText('Green banner')).toBeDefined();

    fireEvent.click(screen.getByRole('radio', { name: 'Royal blue' }));
    expect(document.documentElement.getAttribute('data-palette')).toBe('blue');
    expect(screen.getByRole('radio', { name: 'Royal blue' })).toHaveProperty('checked', true);
    expect(screen.getByText('Royal blue')).toBeDefined();
  });
});
