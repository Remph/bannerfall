// @vitest-environment jsdom
import { cleanup, fireEvent, screen, within } from '@testing-library/react';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client';
import { ApiError } from '../../api/errors';
import { meQueryKey, type Me } from '../../api/queries/auth';
import { enemiesQueryKey, myGuildQueryKey } from '../../api/queries/guilds';
import { playerQueryKey, type PlayerHistory, type PlayerProfile } from '../../api/queries/ranking';
import { worldQueryKey } from '../../api/queries/world';
import { me, world } from '../../testing/fixtures';
import { renderPage } from '../../testing/renderPage';
import { PlayerPage } from './PlayerPage';

const profile: PlayerProfile = {
  id: 8,
  name: 'Brita',
  npc: false,
  points: 2480,
  rank: 3,
  villages: [
    { id: 70, name: 'Tomburg', x: 12, y: 34, points: 1500 },
    { id: 71, name: 'Nordwacht', x: 20, y: 40, points: 980 },
  ],
  guild: { id: 4, name: 'Iron Wolves', tag: 'IW' },
  achievements: [{ key: 'FOREMAN', category: 'BUILDING', leveled: true, level: 2, earnedAt: '2026-10-20T10:00:00Z' }],
  stats: {
    attacksWon: 12,
    attacksLost: 3,
    defencesWon: 4,
    defencesLost: 1,
    killedAttacking: 1520,
    killedDefending: 300,
    unitsLost: 410,
    lootTaken: 25_400,
    lootLost: 900,
    villagesConquered: 2,
    villagesLost: 0,
    unitsRecruited: 800,
    buildingLevels: 95,
    since: '2026-10-20T08:00:00Z',
  },
  careerUrl: null,
  lordVariant: null,
};
const history: PlayerHistory = {
  since: '2026-10-20T08:00:00Z',
  days: [
    { day: '2026-10-20', points: 1000, villages: 1, rank: 5 },
    { day: '2026-10-21', points: 1200, villages: 2, rank: 4 },
  ],
};

function open(shown: PlayerProfile = profile, viewer: Me = me, language: 'en' | 'de' = 'en') {
  return renderPage({
    path: '/players/:playerId',
    url: `/players/${shown.id}`,
    element: <PlayerPage />,
    data: [
      [meQueryKey, viewer],
      [worldQueryKey, world],
      [myGuildQueryKey, { guild: null, role: null, invitations: [] }],
      [enemiesQueryKey, { guilds: [], players: [], villages: [], max: 30 }],
      [[...playerQueryKey, shown.id], shown],
      [[...playerQueryKey, shown.id, 'history'], history],
    ],
    language,
  });
}

/** A player's profile (04-api *World & player*): rank, guild, villages, statistics, achievements. */
describe('PlayerPage', () => {
  beforeAll(() => {
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
      this.open = false;
    };
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it('shows the player with rank, points, guild and a way back to the ranking', () => {
    open();

    expect(screen.getByRole('heading', { level: 1, name: 'Brita' })).toBeTruthy();
    expect(screen.getByText('Rank 3 · 2,480 points')).toBeTruthy();
    expect(screen.getByRole('link', { name: '[IW] Iron Wolves' }).getAttribute('href')).toBe('/guilds/4');
    expect(screen.getByRole('link', { name: /Ranking/ }).getAttribute('href')).toBe('/ranking');
  });

  it('lists the villages, the statistics and the earned achievements', () => {
    open();

    const villages = screen.getByRole('heading', { name: 'Villages' }).closest('section') as HTMLElement;
    expect(within(villages).getAllByRole('listitem')).toHaveLength(2);
    expect(within(villages).getByText('Tomburg')).toBeTruthy();
    expect(screen.getByText('12 won · 3 lost')).toBeTruthy();
    const achievements = screen.getByRole('heading', { name: 'Achievements' }).closest('section') as HTMLElement;
    expect(within(achievements).getAllByRole('listitem')).toHaveLength(1);
    expect(within(achievements).getByText('Foreman')).toBeTruthy();
    // not the viewer's own page: no link to the progress
    expect(screen.queryByRole('link', { name: 'All achievements and your progress' })).toBeNull();
  });

  it('offers to write to another player, not to oneself', () => {
    open();
    expect(screen.getByRole('button', { name: 'Write a message' })).toBeTruthy();
    cleanup();

    open({ ...profile, id: me.playerId!, name: 'aldric' });
    expect(screen.queryByRole('button', { name: 'Write a message' })).toBeNull();
    expect(screen.getByText('you')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'All achievements and your progress' })).toBeTruthy();
  });

  it('says an NPC lord plays by the same rules, and offers him no message', () => {
    open({ ...profile, npc: true, achievements: [] });

    expect(screen.getByText('An NPC lord plays by the same rules as everyone else.')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Write a message' })).toBeNull();
    expect(screen.queryByRole('heading', { name: 'Achievements' })).toBeNull();
  });

  it('names a met lord’s variant with its line, and says when the player has not met it yet', () => {
    // 2.1 (09-npc-lords §16.7): the server sends the variant only after the first contact
    const lord = { ...profile, id: 9, name: 'Baron Wulfram', npc: true, achievements: [] };
    open({ ...lord, lordVariant: { key: 'ROBBER_KNIGHT', since: '2026-11-19T08:00:00Z' } });
    expect(screen.getByText('Baron Wulfram, the Robber Knight')).toBeTruthy();
    expect(screen.getByText('Lives off raids: fast light cavalry, rarely nobles.')).toBeTruthy();
    expect(screen.queryByText("You haven't met this lord yet.")).toBeNull();
    cleanup();

    open({ ...lord, lordVariant: null });
    expect(screen.getByText("You haven't met this lord yet.")).toBeTruthy();
    expect(screen.queryByText(/Robber Knight/)).toBeNull();
    cleanup();

    // a lord spawned before 2.1, met: nothing extra
    open({ ...lord, lordVariant: { key: null, since: '2026-11-19T08:00:00Z' } });
    expect(screen.queryByText("You haven't met this lord yet.")).toBeNull();
    cleanup();

    open({ ...lord, lordVariant: { key: 'KNIGHT', since: '2026-11-19T08:00:00Z' } }, me, 'de');
    expect(screen.getByText('Baron Wulfram, der Ritter von Ehre')).toBeTruthy();
    cleanup();

    // a female lord gets the female German title (Gräfin, Lady, Dame, Baronin)
    open({ ...lord, name: 'Lady Brunhild', lordVariant: { key: 'HOTHEAD', since: '2026-11-19T08:00:00Z' } }, me, 'de');
    expect(screen.getByText('Lady Brunhild, die Hitzköpfin')).toBeTruthy();
  });

  it('shows the delete tool to the admin only, and asks first', () => {
    open();
    expect(screen.queryByRole('button', { name: 'Delete player' })).toBeNull();
    cleanup();

    open(profile, { ...me, role: 'ADMIN' });
    fireEvent.click(screen.getByRole('button', { name: 'Delete player' }));

    expect(screen.getByText('Delete this player?')).toBeTruthy();
    expect(screen.getByText(/villages turn into barbarian villages/)).toBeTruthy();
  });

  it('names a player who does not exist', async () => {
    vi.spyOn(api, 'GET').mockRejectedValue(new ApiError(404, 'PLAYER_NOT_FOUND', 'Not found'));
    renderPage({
      path: '/players/:playerId',
      url: '/players/99',
      element: <PlayerPage />,
      data: [
        [meQueryKey, me],
        [worldQueryKey, world],
      ],
    });

    expect(await screen.findByRole('alert')).toBeTruthy();
    expect(screen.getByRole('link', { name: /Ranking/ })).toBeTruthy();
  });

  it('is shown in German too', () => {
    open(profile, me, 'de');

    expect(screen.getByText('Rang 3 · 2.480 Punkte')).toBeTruthy();
  });
});
