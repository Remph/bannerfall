// @vitest-environment jsdom
import { cleanup, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { meQueryKey, type Me } from '../api/queries/auth';
import { renderPage } from '../testing/renderPage';
import { DesktopNav, TabBar } from './navigation';

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
  villages: [{ id: 9, name: 'Dorf von Ben', x: 48, y: 52, points: 300, incomingAttacks: 0, tag: null }],
};

function show(url: string) {
  renderPage({
    path: '*',
    url,
    element: (
      <>
        <DesktopNav villageId={9} />
        <TabBar villageId={9} onMore={() => {}} />
      </>
    ),
    data: [[meQueryKey, me]],
  });
}

/** The names of everything lit, the desktop tab row and the phone tab bar together. */
function lit(): string[] {
  return [...document.querySelectorAll('[aria-current="page"]')].map((element) => element.textContent.trim());
}

describe('the bars light the open page (1.5.1)', () => {
  afterEach(cleanup);

  it('lights Village in both bars on the warehouse, and nothing behind More', () => {
    show('/v/9/warehouse');
    expect(lit()).toEqual(['Village', 'Village']);
    expect(screen.getByRole('button', { name: 'More' }).getAttribute('aria-current')).toBeNull();
  });

  it('lights Ranking on somebody else’s profile, and More on the phone', () => {
    show('/players/8');
    expect(lit()).toEqual(['Ranking', 'More']);
  });

  it('lights no section on the own profile, only More on the phone', () => {
    show(`/players/${PLAYER_ID}`);
    expect(lit()).toEqual(['More']);
  });
});
