// @vitest-environment jsdom
import { cleanup, fireEvent, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { meQueryKey, type Me } from '../../api/queries/auth';
import type { components } from '../../api/schema';
import { renderPage } from '../../testing/renderPage';
import { PasswordPage } from './PasswordPage';

const ACCOUNT_ID = 7;

const me: Me = {
  accountId: ACCOUNT_ID,
  username: 'Alrik',
  role: 'PLAYER',
  playerId: ACCOUNT_ID,
  points: 0,
  protectedUntil: '2026-10-20T12:00:00Z',
  needsRespawn: false,
  villages: [],
  linked: false,
  respawnNearGuild: false,
};

function open(mode: components['schemas']['AuthOptionsDto']['mode']) {
  const options: components['schemas']['AuthOptionsDto'] = {
    mode,
    accountServerName: 'example.com',
    careerReports: false,
  };
  const external: components['schemas']['ExternalLoginDto'] = {
    name: null,
    problem: null,
    bannedUntil: null,
    banReason: null,
  };
  return renderPage({
    path: '/password',
    url: '/password',
    element: <PasswordPage />,
    data: [
      [meQueryKey, me],
      [['authOptions'], options],
      [['externalLogin'], external],
    ],
  });
}

/** The card that links the account, found by its heading. */
function linkCard() {
  return screen.getByRole('heading', { name: 'Log in with example.com' }).closest('section') ?? document.body;
}

describe('PasswordPage', () => {
  afterEach(cleanup);

  it('asks for the current password before linking the account (security audit of 1.5)', () => {
    open('LINKING');

    const card = within(linkCard());
    const field = card.getByLabelText('Current password');
    expect(field).toHaveProperty('type', 'password');
    expect(field.getAttribute('autocomplete')).toBe('current-password');

    // an empty field is refused on the page, without a request
    fireEvent.click(card.getByRole('button', { name: 'Link with example.com' }));
    expect(card.getByText('Please fill in this field.')).toBeTruthy();
    expect(card.getByRole('button', { name: 'Link with example.com' })).toHaveProperty('disabled', false);
  });

  it('offers no linking on a world without it', () => {
    open('OFF');

    expect(screen.queryByRole('heading', { name: 'Log in with example.com' })).toBeNull();
  });
});
