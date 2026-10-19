// @vitest-environment jsdom
import { cleanup, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { meQueryKey, type Me } from '../../api/queries/auth';
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
