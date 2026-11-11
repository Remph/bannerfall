// @vitest-environment jsdom
import { cleanup, fireEvent, screen, within } from '@testing-library/react';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client';
import { ApiError } from '../../api/errors';
import { meQueryKey } from '../../api/queries/auth';
import {
  enemiesQueryKey,
  guildListQueryKey,
  guildQueryKey,
  myGuildQueryKey,
  type Enemies,
  type Guild,
  type GuildInvitation,
  type GuildMember,
  type MyGuild,
} from '../../api/queries/guilds';
import { worldQueryKey, type World } from '../../api/queries/world';
import { me, tutorial, world } from '../../testing/fixtures';
import { tutorialQueryKey } from '../../api/queries/tutorial';
import { renderPage } from '../../testing/renderPage';
import { GuildPage } from './GuildPage';
import { GuildProfilePage } from './GuildProfilePage';

const guildsOn: World = { ...world, guildsEnabled: true };
const noEnemies: Enemies = { guilds: [], players: [], villages: [], max: 30 };
const quietTutorial = { ...tutorial, active: false, current: null };

function member(playerId: number, name: string, role: GuildMember['role'] = 'MEMBER'): GuildMember {
  return {
    playerId,
    name,
    role,
    points: 100 + playerId,
    villageCount: 2,
    joinedAt: '2026-10-12T10:00:00Z',
    npc: false,
  };
}

function guild(id: number, name: string, tag: string, members: GuildMember[]): Guild {
  return {
    id,
    name,
    tag,
    description: 'We farm together.',
    createdAt: '2026-10-11T10:00:00Z',
    points: 1234,
    bannerLevel: 0,
    members,
    invitations: [],
  };
}

const friends = guild(1, 'Friends', 'FR', [member(me.playerId!, 'aldric'), member(8, 'Brita', 'LEADER')]);
const wolves = guild(4, 'Iron Wolves', 'IW', [member(20, 'Sir Godwin', 'LEADER')]);
const list = [
  { id: 1, name: 'Friends', tag: 'FR', memberCount: 2, points: 1234 },
  { id: 4, name: 'Iron Wolves', tag: 'IW', memberCount: 1, points: 900 },
];
const invitation: GuildInvitation = {
  id: 9,
  guildId: 4,
  guildName: 'Iron Wolves',
  guildTag: 'IW',
  playerId: me.playerId!,
  playerName: 'aldric',
  invitedByName: 'Sir Godwin',
  createdAt: '2026-10-14T10:00:00Z',
};

function open(mine: MyGuild, shownWorld: World = guildsOn, language: 'en' | 'de' = 'en') {
  return renderPage({
    path: '/guild',
    url: '/guild',
    element: <GuildPage />,
    data: [
      [meQueryKey, me],
      [worldQueryKey, shownWorld],
      [myGuildQueryKey, mine],
      [guildListQueryKey, list],
      [enemiesQueryKey, noEnemies],
      [tutorialQueryKey, quietTutorial],
    ],
    language,
  });
}

function openProfile(url: string, extra: [readonly unknown[], unknown][] = []) {
  return renderPage({
    path: '/guilds/:guildId',
    url,
    element: <GuildProfilePage />,
    children: [],
    data: [
      [meQueryKey, me],
      [worldQueryKey, guildsOn],
      [enemiesQueryKey, noEnemies],
      [myGuildQueryKey, { guild: null, role: null, invitations: [] }],
      ...extra,
    ],
  });
}

describe('GuildPage', () => {
  beforeAll(() => {
    // jsdom has no modal dialogs
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.open = true;
    };
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it('without a guild: the way in is the invitations, founding one and the list of all guilds', () => {
    open({ guild: null, role: null, invitations: [invitation] });

    expect(screen.getByRole('heading', { level: 1, name: 'Guild' })).toBeTruthy();
    for (const title of ['Invitations', 'Found a guild', 'All guilds']) {
      expect(screen.getByRole('heading', { name: title })).toBeTruthy();
    }
    const invitations = screen.getByRole('heading', { name: 'Invitations' }).closest('section') as HTMLElement;
    expect(within(invitations).getByText('invited by Sir Godwin')).toBeTruthy();
    expect(within(invitations).getByRole('button', { name: 'Join' })).toBeTruthy();
    expect(within(invitations).getByRole('button', { name: 'Decline' })).toBeTruthy();
    // the list links to the guilds
    expect(screen.getByRole('link', { name: '[FR] Friends' }).getAttribute('href')).toBe('/guilds/1');
  });

  it('where guilds are off, it says so and offers nothing to found or join', () => {
    open({ guild: null, role: null, invitations: [] }, world);

    expect(screen.getByText('The host has not switched guilds on in this world.')).toBeTruthy();
    expect(screen.queryByRole('heading', { name: 'Found a guild' })).toBeNull();
    expect(screen.queryByRole('heading', { name: 'Invitations' })).toBeNull();
  });

  it('before the opening, it says what joining does', () => {
    open({ guild: null, role: null, invitations: [] }, { ...guildsOn, phase: 'PREGAME' });

    expect(screen.getByText('Whoever is in a guild when the world opens starts next to it.')).toBeTruthy();
  });

  it('shows a member their guild with the members, and "Leave" without the leader tools', () => {
    open({ guild: friends, role: 'MEMBER', invitations: [] });

    expect(screen.getByRole('heading', { level: 1, name: '[FR]Friends' })).toBeTruthy();
    expect(screen.getByText(/2 of 10 members/)).toBeTruthy();
    expect(screen.getByText('We farm together.')).toBeTruthy();
    expect(screen.getByText('Brita')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Leave the guild' })).toBeTruthy();
    expect(screen.queryByRole('heading', { name: 'Invite to guild' })).toBeNull();
    // the other guilds are listed below
    expect(screen.getByRole('heading', { name: 'All guilds' })).toBeTruthy();
  });

  it('asks before leaving', () => {
    open({ guild: friends, role: 'MEMBER', invitations: [] });

    fireEvent.click(screen.getByRole('button', { name: 'Leave the guild' }));

    expect(screen.getByText('Leave the guild?')).toBeTruthy();
  });

  it('gives the leader of a guild with others the tools to invite and disband, but no direct leave', () => {
    const lead = guild(1, 'Friends', 'FR', [member(me.playerId!, 'aldric', 'LEADER'), member(8, 'Brita')]);
    open({ guild: lead, role: 'LEADER', invitations: [] });

    expect(screen.getByRole('button', { name: 'Disband the guild' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Leave the guild' })).toBeNull();
    expect(screen.getByText(/Hand the lead to a member/)).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Invite' })).toBeTruthy();
  });

  it('shows the error with a retry when the guild cannot be read', async () => {
    vi.spyOn(api, 'GET').mockRejectedValue(new ApiError(500, 'INTERNAL', 'Server error'));
    renderPage({
      path: '/guild',
      url: '/guild',
      element: <GuildPage />,
      data: [
        [meQueryKey, me],
        [worldQueryKey, guildsOn],
      ],
    });

    expect(await screen.findByRole('button', { name: 'Try again' })).toBeTruthy();
    expect(screen.queryByRole('heading', { name: 'Found a guild' })).toBeNull();
  });

  it('is shown in German too', () => {
    open({ guild: null, role: null, invitations: [] }, guildsOn, 'de');

    expect(screen.getByRole('heading', { level: 1, name: 'Gilde' })).toBeTruthy();
  });
});

describe('GuildProfilePage', () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it("shows somebody else's guild read-only with a way back to the guilds", () => {
    openProfile('/guilds/4', [[guildQueryKey(4), wolves]]);

    expect(screen.getByRole('heading', { level: 1, name: '[IW]Iron Wolves' })).toBeTruthy();
    expect(screen.getByText('Sir Godwin')).toBeTruthy();
    expect(screen.getByRole('link', { name: /Guilds/ }).getAttribute('href')).toBe('/guild');
    expect(screen.queryByRole('button', { name: 'Leave the guild' })).toBeNull();
  });

  it('names a guild that does not exist', () => {
    renderPage({
      path: '/guilds/:guildId',
      url: '/guilds/abc',
      element: <GuildProfilePage />,
      data: [[meQueryKey, me]],
    });

    expect(screen.getByRole('link', { name: /Guilds/ })).toBeTruthy();
    expect(screen.getByRole('alert').textContent).toMatch(/guild/i);
  });

  it('says so when the server does not know the guild', async () => {
    vi.spyOn(api, 'GET').mockRejectedValue(new ApiError(404, 'GUILD_NOT_FOUND', 'Not found'));
    openProfile('/guilds/77');

    expect(await screen.findByText('This guild does not exist (anymore).')).toBeTruthy();
    expect(screen.getByRole('link', { name: /Guilds/ }).getAttribute('href')).toBe('/guild');
  });
});
