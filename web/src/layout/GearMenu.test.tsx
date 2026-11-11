// @vitest-environment jsdom
import { cleanup, fireEvent, screen } from '@testing-library/react';
import { useRef, useState } from 'react';
import { afterEach, describe, expect, it } from 'vitest';
import { meQueryKey, type Me } from '../api/queries/auth';
import { renderPage } from '../testing/renderPage';
import { GearMenu } from './GearMenu';

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
  villages: [{ id: 9, name: 'Dorf von Ben', x: 48, y: 52, points: 300, incomingAttacks: 0, tag: null }],
};

/** A gear button and its menu, wired like AppLayout does. */
function Harness() {
  const gear = useRef<HTMLButtonElement>(null);
  const [place, setPlace] = useState<{ top: number; right: number } | null>(null);
  return (
    <>
      <button ref={gear} type="button" onClick={() => setPlace(place === null ? { top: 60, right: 10 } : null)}>
        Gear
      </button>
      <p>The page</p>
      <GearMenu place={place} anchor={gear} onClose={() => setPlace(null)} />
    </>
  );
}

function open() {
  renderPage({ path: '*', url: '/v/9', element: <Harness />, data: [[meQueryKey, me]] });
  fireEvent.click(screen.getByRole('button', { name: 'Gear' }));
}

describe('GearMenu: the PC’s small menu under the gear (2.0)', () => {
  afterEach(cleanup);

  it('shows the settings and the own profile, and nothing before the gear is pressed', () => {
    renderPage({ path: '*', url: '/v/9', element: <Harness />, data: [[meQueryKey, me]] });
    expect(screen.queryByRole('dialog', { name: 'Settings' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Gear' }));
    const menu = screen.getByRole('dialog', { name: 'Settings' });
    expect(menu.contains(screen.getByRole('group', { name: 'Theme' }))).toBe(true);
    expect(screen.getByRole('link', { name: 'My profile' }).getAttribute('href')).toBe('/players/7');
  });

  it('closes on Escape and gives the focus back to the gear', () => {
    open();
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(screen.queryByRole('dialog', { name: 'Settings' })).toBeNull();
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Gear' }));
  });

  it('closes on a tap elsewhere, stays open on a tap inside, and the gear toggles it', () => {
    open();
    fireEvent.pointerDown(screen.getByRole('dialog', { name: 'Settings' }));
    expect(screen.getByRole('dialog', { name: 'Settings' })).toBeTruthy();
    fireEvent.pointerDown(screen.getByText('The page'));
    expect(screen.queryByRole('dialog', { name: 'Settings' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Gear' }));
    fireEvent.click(screen.getByRole('button', { name: 'Gear' }));
    expect(screen.queryByRole('dialog', { name: 'Settings' })).toBeNull();
  });

  it('closes when a page of the menu is chosen', () => {
    open();
    fireEvent.click(screen.getByRole('link', { name: 'Change password' }));
    expect(screen.queryByRole('dialog', { name: 'Settings' })).toBeNull();
  });
});
