// @vitest-environment jsdom
import { cleanup, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { meQueryKey } from '../../api/queries/auth';
import { enemiesQueryKey, myGuildQueryKey, type Enemies, type MyGuild } from '../../api/queries/guilds';
import { renderPage } from '../../testing/renderPage';
import { EnemiesCard } from './EnemiesCard';
import { me, world } from '../../testing/fixtures';
import { worldQueryKey } from '../../api/queries/world';

const enemies: Enemies = {
  guilds: [{ id: 4, name: 'Iron Wolves', tag: 'IW' }],
  players: [{ id: 20, name: 'Sir Godwin', npc: true, guildId: null, guildTag: null }],
  villages: [{ id: 300, name: 'Tomburg', x: 12, y: 34, ownerId: 21, ownerName: 'Brita', npc: false }],
  max: 30,
};

const mine = (role: 'LEADER' | 'MEMBER'): MyGuild => ({
  guild: {
    id: 1,
    name: 'Friends',
    tag: 'FR',
    description: '',
    createdAt: '2026-10-20T10:00:00Z',
    points: 0,
    bannerLevel: 0,
    members: [],
    invitations: [],
  },
  role,
  invitations: [],
});

function render(role: 'LEADER' | 'MEMBER', list: Enemies = enemies) {
  return renderPage({
    path: '/guild',
    url: '/guild',
    element: <EnemiesCard isLeader={role === 'LEADER'} />,
    data: [
      [meQueryKey, me],
      [myGuildQueryKey, mine(role)],
      [enemiesQueryKey, list],
      [worldQueryKey, world],
    ],
  });
}

afterEach(cleanup);

describe('EnemiesCard (1.7)', () => {
  it('lists the marked guilds, players and villages and the leader can remove each', () => {
    render('LEADER');

    expect(screen.getByRole('link', { name: '[IW] Iron Wolves' }).getAttribute('href')).toBe('/guilds/4');
    expect(screen.getByRole('link', { name: 'Sir Godwin' }).getAttribute('href')).toBe('/players/20');
    expect(screen.getByText('NPC lord')).toBeTruthy();
    expect(screen.getByText('of Brita')).toBeTruthy();
    expect(screen.getByText('3 of 30 marks used')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Remove the enemy mark from Iron Wolves' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Remove the enemy mark from Sir Godwin' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Remove the enemy mark from Tomburg' })).toBeTruthy();
  });

  it('shows a plain member the list without buttons', () => {
    render('MEMBER');

    expect(screen.getByText('Iron Wolves', { exact: false })).toBeTruthy();
    expect(screen.queryByRole('button')).toBeNull();
  });

  it('says so when nothing is marked, and tells the leader how to mark', () => {
    const empty: Enemies = { guilds: [], players: [], villages: [], max: 30 };
    render('LEADER', empty);
    expect(screen.getByText(/No enemies marked\. Mark a guild/)).toBeTruthy();
    cleanup();

    render('MEMBER', empty);
    expect(screen.getByText('No enemies marked.')).toBeTruthy();
  });
});
