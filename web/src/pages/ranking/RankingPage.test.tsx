// @vitest-environment jsdom
import { cleanup, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { meQueryKey, type Me } from '../../api/queries/auth';
import { enemiesQueryKey, myGuildQueryKey, type Enemies } from '../../api/queries/guilds';
import { rankingQueryKey, type RankingPage as Page } from '../../api/queries/ranking';
import { renderPage } from '../../testing/renderPage';
import { RankingPage } from './RankingPage';

const me: Me = {
  accountId: 1,
  username: 'ada',
  role: 'PLAYER',
  playerId: 7,
  points: 1200,
  protectedUntil: '2026-10-12T00:00:00Z',
  needsRespawn: false,
  linked: false,
  respawnNearGuild: false,
  villages: [],
};

const attackers: Page = {
  page: 0,
  size: 50,
  total: 2,
  entries: [
    {
      rank: 1,
      playerId: 9,
      name: 'Sir Godwin',
      npc: true,
      points: 900,
      villageCount: 2,
      value: 4200,
      guildId: null,
      guildTag: null,
    },
    {
      rank: 2,
      playerId: 7,
      name: 'ada',
      npc: false,
      points: 1200,
      villageCount: 1,
      value: 1500,
      guildId: null,
      guildTag: null,
    },
  ],
};

afterEach(cleanup);

describe('RankingPage tabs (1.5)', () => {
  it('opens the tab of the URL and shows its totals instead of the points', () => {
    renderPage({
      path: '/ranking',
      url: '/ranking?by=attacker',
      element: <RankingPage />,
      data: [
        [meQueryKey, me],
        [[...rankingQueryKey, 'ATTACKER', 0], attackers],
      ],
    });

    expect(screen.getByRole('button', { name: 'Attacker' }).getAttribute('aria-pressed')).toBe('true');
    expect(screen.getByText('Defenders killed in their attacks, counted by population.')).toBeTruthy();
    expect(screen.getByText('4,200')).toBeTruthy();
    expect(screen.queryByText('1,200')).toBeNull();
    expect(screen.getByRole('link', { name: 'World statistics' }).getAttribute('href')).toBe('/statistics');
  });
});

describe('RankingPage enemy marks (1.7)', () => {
  it('marks the players of an enemy guild, and the marked player himself, with a pill', () => {
    const line = (playerId: number, rank: number, name: string, guildId: number | null, guildTag: string | null) => ({
      rank,
      playerId,
      name,
      npc: false,
      points: 500,
      villageCount: 1,
      value: 500,
      guildId,
      guildTag,
    });
    const points: Page = {
      page: 0,
      size: 50,
      total: 3,
      entries: [line(30, 1, 'Brita', 4, 'IW'), line(31, 2, 'Cord', null, null), line(32, 3, 'Dag', null, null)],
    };
    const enemies: Enemies = {
      guilds: [{ id: 4, name: 'Iron Wolves', tag: 'IW' }],
      players: [{ id: 31, name: 'Cord', npc: false, guildId: null, guildTag: null }],
      villages: [],
      max: 30,
    };
    renderPage({
      path: '/ranking',
      url: '/ranking',
      element: <RankingPage />,
      data: [
        [meQueryKey, me],
        [myGuildQueryKey, { guild: { id: 1 }, role: 'MEMBER', invitations: [] }],
        [enemiesQueryKey, enemies],
        [[...rankingQueryKey, 'POINTS', 0], points],
      ],
    });

    expect(screen.getAllByText('Enemy')).toHaveLength(2);
    expect(screen.getByRole('link', { name: /Brita/ }).textContent).toContain('Enemy');
    expect(screen.getByRole('link', { name: /Cord/ }).textContent).toContain('Enemy');
    expect(screen.getByRole('link', { name: /Dag/ }).textContent).not.toContain('Enemy');
  });
  it('shows no pills to a player who has left the guild, whatever is still cached', () => {
    const points: Page = {
      page: 0,
      size: 50,
      total: 1,
      entries: [
        {
          rank: 1,
          playerId: 31,
          name: 'Cord',
          npc: false,
          points: 500,
          villageCount: 1,
          value: 500,
          guildId: null,
          guildTag: null,
        },
      ],
    };
    const enemies: Enemies = {
      guilds: [],
      players: [{ id: 31, name: 'Cord', npc: false, guildId: null, guildTag: null }],
      villages: [],
      max: 30,
    };
    renderPage({
      path: '/ranking',
      url: '/ranking',
      element: <RankingPage />,
      data: [
        [meQueryKey, me],
        [myGuildQueryKey, { guild: null, role: null, invitations: [] }],
        [enemiesQueryKey, enemies],
        [[...rankingQueryKey, 'POINTS', 0], points],
      ],
    });

    expect(screen.getByRole('link', { name: /Cord/ })).toBeTruthy();
    expect(screen.queryByText('Enemy')).toBeNull();
  });
});
