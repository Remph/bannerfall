// @vitest-environment jsdom
import { cleanup, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { meQueryKey } from '../../api/queries/auth';
import { guildBannerQueryKey, myGuildQueryKey, type GuildBanner } from '../../api/queries/guilds';
import { worldQueryKey } from '../../api/queries/world';
import { me, world } from '../../testing/fixtures';
import { renderPage } from '../../testing/renderPage';
import { GuildBannerCard } from './GuildBannerCard';

const banner: GuildBanner = {
  enabled: true,
  level: 1,
  maxLevel: 3,
  fund: 142_000,
  price: 270_000,
  missing: 128_000,
  members: 3,
  memberPrices: [30_000, 90_000, 250_000],
  lockedUntil: null,
  effects: [
    { level: 1, kind: 'PRODUCTION', bonus: 0.05 },
    { level: 2, kind: 'SUPPORT', bonus: 0.1 },
    { level: 3, kind: 'DEFENCE', bonus: 0.05 },
  ],
  donors: [{ playerId: 1, name: 'Ben', amount: 142_000 }],
};

function render(overrides: Partial<GuildBanner> = {}) {
  return renderPage({
    path: '/guild',
    url: '/guild',
    element: <GuildBannerCard />,
    data: [
      [meQueryKey, me],
      [myGuildQueryKey, { guild: { id: 1 }, role: 'MEMBER', invitations: [] }],
      [guildBannerQueryKey, { ...banner, ...overrides }],
      [worldQueryKey, world],
    ],
  });
}

afterEach(cleanup);

describe('GuildBannerCard (1.7)', () => {
  it('shows the level, the effects with the price of the next ones, the fund and the donors', () => {
    render();

    expect(screen.getByText('Level 1 of 3')).toBeTruthy();
    expect(screen.getByText('Wood, clay and iron production +5%')).toBeTruthy();
    expect(screen.getByText('Support to a guild mate arrives 10% sooner')).toBeTruthy();
    expect(screen.getByText(/90,000 per member/)).toBeTruthy();
    expect(screen.getByText('142,000 / 270,000')).toBeTruthy();
    expect(screen.getByText('90,000 × 3 members')).toBeTruthy();
    expect(screen.getByText('Ben')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Donate' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Raise level' })).toBeNull();
  });

  it('offers Raise level instead of Donate once the fund covers the price', () => {
    render({ fund: 270_000, missing: 0 });

    expect(screen.getByRole('button', { name: 'Raise level' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Donate' })).toBeNull();
  });

  it('says why raising is blocked after a member left, and offers no button', () => {
    render({ fund: 270_000, missing: 0, lockedUntil: '2999-01-01T12:00:00Z' });

    expect(screen.getByText(/Raising is blocked until/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Raise level' })).toBeNull();
  });

  it('has no buttons at the highest level', () => {
    render({ level: 3, missing: 0, price: 0, fund: 0 });

    expect(screen.getByText('The guild banner has reached its highest level.')).toBeTruthy();
    expect(screen.queryByRole('button')).toBeNull();
  });
});
