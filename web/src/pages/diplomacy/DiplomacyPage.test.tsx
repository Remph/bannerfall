// @vitest-environment jsdom
import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { diplomacyQueryKey, type Diplomacy, type Lord } from '../../api/queries/diplomacy';
import { enemiesQueryKey, myGuildQueryKey } from '../../api/queries/guilds';
import { victoryQueryKey, type Victory, type VictorySide } from '../../api/queries/world';
import { WorldEndContext, type WorldEnd } from '../../layout/worldEnd';
import { renderPage } from '../../testing/renderPage';
import { DiplomacyPage } from './DiplomacyPage';

const { post, del } = vi.hoisted(() => ({ post: vi.fn(), del: vi.fn() }));
vi.mock('../../api/client', async (importOriginal) => {
  const original = await importOriginal<typeof import('../../api/client')>();
  return { ...original, api: { ...original.api, POST: post, DELETE: del } };
});

const court: VictorySide = {
  kind: 'GUILD',
  id: 40,
  name: 'Ordo Corvi',
  tag: 'OC',
  npc: true,
  villages: 30,
  points: 52_000,
  keeps: 0,
  court: { key: 'RAVEN', numeral: null },
};
const mine: VictorySide = {
  kind: 'GUILD',
  id: 3,
  name: 'Iron Wolves',
  tag: 'IW',
  npc: false,
  villages: 12,
  points: 20_000,
  keeps: 0,
  court: null,
};

const lord = (over: Partial<Lord> & Pick<Lord, 'playerId' | 'name'>): Lord => ({
  guild: null,
  ally: false,
  trust: 0,
  bar: 50,
  changes: [],
  villages: 3,
  points: 900,
  home: null,
  invitationId: null,
  inviteAfter: null,
  // met (the server sends the bar only then)
  variant: { key: null, since: '2026-11-01T12:00:00Z' },
  order: null,
  ...over,
});

const page: Diplomacy = {
  soloMode: true,
  leader: true,
  guildFull: false,
  court,
  mine,
  allVillages: 100,
  allies: [
    lord({
      playerId: 11,
      name: 'Gerold',
      ally: true,
      trust: 64,
      bar: null,
      guild: { id: 3, name: 'Iron Wolves', tag: 'IW' },
      changes: [{ reason: 'SUPPORT', delta: 10, village: 'Wulfsburg' }],
    }),
  ],
  lords: [
    lord({ playerId: 12, name: 'Adalbert', trust: 52, guild: { id: 40, name: 'Ordo Corvi', tag: 'OC' } }),
    lord({
      playerId: 13,
      name: 'Hedwig',
      trust: -8,
      // not met yet: no bar (security review 2.1)
      bar: null,
      variant: null,
      changes: [{ reason: 'SCOUTED', delta: -10, village: 'Eichenau' }],
    }),
    lord({ playerId: 14, name: 'Kunibert', trust: 30, invitationId: 99 }),
    lord({ playerId: 15, name: 'Ratold', trust: 40, inviteAfter: '2999-01-01T12:00:00Z' }),
  ],
};

const victory: Victory = {
  mode: 'DOMINATION',
  dominationPercent: 60,
  holdHours: 48,
  keepCount: 0,
  keepPercent: 51,
  keepsNeeded: 3,
  endsAt: null,
  phase: 'OPEN',
  allVillages: 100,
  sides: [court, mine],
  leader: null,
  leaderSince: null,
  winsAt: null,
  result: null,
};

const enemies = {
  guilds: [],
  players: [],
  villages: [{ id: 70, name: 'Wulframsfeste', x: 61, y: 52, ownerId: 12, ownerName: 'Adalbert', npc: true }],
  max: 20,
};

function open(data: Diplomacy = page, language: 'en' | 'de' = 'en', inGuild = true, end?: WorldEnd) {
  return renderPage({
    path: '/diplomacy',
    url: '/diplomacy',
    element:
      end === undefined ? (
        <DiplomacyPage />
      ) : (
        <WorldEndContext value={end}>
          <DiplomacyPage />
        </WorldEndContext>
      ),
    data: [
      [diplomacyQueryKey, data],
      [victoryQueryKey, victory],
      [myGuildQueryKey, { guild: inGuild ? { id: 3 } : null, invitations: [] }],
      [enemiesQueryKey, enemies],
    ],
    language,
  });
}

/** The diplomacy page (2.1 S2b, 12-solo-and-allies §7). */
describe('DiplomacyPage', () => {
  afterEach(() => {
    cleanup();
    post.mockReset();
    del.mockReset();
  });

  it('names the court, its goal and the race against it', () => {
    open();
    expect(screen.getByRole('heading', { level: 2, name: 'The Raven Court' })).toBeTruthy();
    expect(screen.getByText('The Raven Court wants 60 % of the land.')).toBeTruthy();
    const race = screen.getByRole('heading', { name: 'The race' }).parentElement!;
    expect(within(race).getByText('Your guild')).toBeTruthy();
    // the court holds 30 % against 12 %: it is ahead
    expect(within(race).getAllByText('Ahead')).toHaveLength(1);
    expect(within(race).getByText(/^30.*% of 60 %$/)).toBeTruthy();
  });

  it('lists the allies with their trust and last change', () => {
    open();
    const allies = screen.getByRole('heading', { name: 'Your allies' }).closest('section')!;
    expect(within(allies).getByText('Gerold')).toBeTruthy();
    expect(within(allies).getByLabelText('Trust 64')).toBeTruthy();
    expect(within(allies).getByText('+10 your support at Wulfsburg')).toBeTruthy();
    expect(within(allies).queryByRole('button')).toBeNull();
  });

  it('offers one Invite per lord that can be asked now, and says why the others wait', () => {
    open();
    const buttons = screen.getAllByRole('button', { name: /^Invite .* into your guild$/ });
    expect(buttons.map((button) => button.getAttribute('aria-label'))).toEqual([
      'Invite Adalbert into your guild',
      'Invite Hedwig into your guild',
    ]);
    expect(screen.getByText('Invited: an answer by letter in the next round')).toBeTruthy();
    expect(screen.getByText(/^Said no: ask again/)).toBeTruthy();
    expect(screen.getByLabelText('Trust -8').textContent).toBe('−8');
    expect(screen.getByText('−10 you scouted Eichenau')).toBeTruthy();
  });

  it('says "joins at" only for a lord the player has met', () => {
    open();
    const lords = screen.getByRole('heading', { name: 'Other lords' }).closest('section')!;
    const adalbert = within(lords).getByText('Adalbert').closest('li')!;
    const hedwig = within(lords).getByText('Hedwig').closest('li')!;
    expect(within(adalbert).getByText('joins at 50')).toBeTruthy();
    expect(within(adalbert).queryByText('not met yet')).toBeNull();
    expect(within(hedwig).getByText('not met yet')).toBeTruthy();
    expect(within(hedwig).queryByText(/joins at/)).toBeNull();
    // an unmet lord can still be invited
    expect(within(hedwig).getByRole('button', { name: 'Invite Hedwig into your guild' })).toBeTruthy();
  });

  it('sends the invite of a lord', async () => {
    post.mockResolvedValue({ data: page, response: new Response(null, { status: 200 }) });
    open();
    fireEvent.click(screen.getByRole('button', { name: 'Invite Adalbert into your guild' }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('/api/diplomacy/lords/{lordId}/invite', { params: { path: { lordId: 12 } } }),
    );
  });

  it('lists only the lords and their guilds outside a solo world (2.1 feature review, 03)', () => {
    open({
      ...page,
      soloMode: false,
      court: null,
      allies: [],
      lords: [
        lord({
          playerId: 12,
          name: 'Adalbert',
          trust: null,
          bar: null,
          changes: [],
          points: 4321,
          guild: { id: 40, name: 'Ordo Corvi', tag: 'OC' },
        }),
        lord({ playerId: 13, name: 'Hedwig', trust: null, bar: null, variant: null, changes: [], points: 812 }),
      ],
    });
    expect(screen.getByText(/Lords become a player’s allies only in a solo world/)).toBeTruthy();
    expect(screen.getByText('Adalbert')).toBeTruthy();
    expect(screen.getByText('4,321 points')).toBeTruthy();
    expect(screen.queryByLabelText(/^Trust/)).toBeNull();
    expect(screen.queryByText(/joins at|not met yet/i)).toBeNull();
    expect(screen.queryByText(/The court|Your allies|Trust grows/)).toBeNull();
    // no allies above it: not "Other lords"
    expect(screen.getByRole('heading', { name: 'Lords' })).toBeTruthy();
    expect(screen.queryByText('Other lords')).toBeNull();
    expect(screen.queryAllByRole('button', { name: /^Invite/ })).toHaveLength(0);
  });

  it('sends a player without a guild to found one', () => {
    open({ ...page, leader: false }, 'en', false);
    expect(screen.getByText(/Found a guild to invite lords/)).toBeTruthy();
    expect(screen.getByRole('link', { name: 'To the guild' }).getAttribute('href')).toBe('/guild');
  });

  it('says so while no lord guild has formed', () => {
    open({ ...page, court: null });
    expect(screen.getByText(/No lord guild has formed yet/)).toBeTruthy();
  });

  it('reads in German too', () => {
    open(page, 'de');
    expect(screen.getByRole('heading', { level: 1, name: 'Diplomatie' })).toBeTruthy();
    expect(screen.getByRole('heading', { level: 2, name: 'Der Rabenhof' })).toBeTruthy();
  });

  it('shows an ally’s order and takes it back (2.1 S3)', async () => {
    const ordered: Diplomacy = {
      ...page,
      allies: [
        {
          ...page.allies[0]!,
          order: {
            kind: 'ATTACK',
            target: { id: 70, name: 'Wulframsfeste', x: 61, y: 52, playerId: 12, playerName: 'Adalbert' },
            state: 'OPEN',
            landTogether: false,
            answer: null,
            givenAt: '2026-11-19T10:00:00Z',
            departAt: null,
            arriveAt: null,
          },
        },
      ],
    };
    del.mockResolvedValue({ data: page, response: new Response(null, { status: 200 }) });
    open(ordered);
    const allies = screen.getByRole('heading', { name: 'Your allies' }).closest('section')!;
    expect(
      within(allies).getByText('Order: attack Wulframsfeste (61\u2060|\u206052), answer in the next round.'),
    ).toBeTruthy();
    fireEvent.click(within(allies).getByRole('button', { name: 'Take back the order to Gerold' }));
    await waitFor(() =>
      expect(del).toHaveBeenCalledWith('/api/diplomacy/orders/{lordId}', { params: { path: { lordId: 11 } } }),
    );
  });

  it('offers the guild’s marked villages first as targets for orders (2.1 S3)', () => {
    open();
    const marked = screen.getByRole('heading', { name: 'Marked enemy villages' }).closest('section')!;
    expect(within(marked).getByText('Wulframsfeste (61\u2060|\u206052)')).toBeTruthy();
    expect(
      within(marked).getByRole('button', { name: 'Order allies against Wulframsfeste (61\u2060|\u206052)' }),
    ).toBeTruthy();
  });

  it('shows no marked villages without an ally', () => {
    open({ ...page, allies: [] });
    expect(screen.queryByRole('heading', { name: 'Marked enemy villages' })).toBeNull();
  });

  it('takes no invite, order or take-back while the world is paused or has ended (UI review 2.1, 04)', () => {
    const ordered: Diplomacy = {
      ...page,
      allies: [
        {
          ...page.allies[0]!,
          order: {
            kind: 'HOLD',
            target: { id: 70, name: 'Wulframsfeste', x: 61, y: 52, playerId: 12, playerName: 'Adalbert' },
            state: 'ACTIVE',
            landTogether: false,
            answer: null,
            givenAt: '2026-11-19T10:00:00Z',
            departAt: null,
            arriveAt: null,
          },
        },
      ],
    };
    for (const end of [
      { ended: false, frozen: true, paused: true, host: true },
      { ended: true, frozen: true },
    ]) {
      open(ordered, 'en', true, end);
      const allies = screen.getByRole('heading', { name: 'Your allies' }).closest('section')!;
      // the order still shows, without its button
      expect(within(allies).getByText(/^Order: leave Wulframsfeste/)).toBeTruthy();
      expect(screen.queryByRole('button', { name: /^Take back/ })).toBeNull();
      expect(screen.queryByRole('button', { name: /^Invite / })).toBeNull();
      expect(screen.queryByRole('button', { name: /^Order allies against/ })).toBeNull();
      cleanup();
    }
  });
});
